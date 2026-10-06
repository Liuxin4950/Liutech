package chat.liuxin.ai.service.community;

import chat.liuxin.ai.common.client.BackendApiTransport;
import chat.liuxin.ai.dto.community.CommunityTask;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.mapper.CommunityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** 持久单并发 worker，跨进程共享数据库租约；模型执行不依赖浏览器连接。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommunityWorker {
    private final BackendApiTransport transport;
    private final CommunityStore store;
    private final CommunityMapper mapper;
    private final CommunityService community;
    private final ObjectMapper objectMapper;
    private final AiChatProperties properties;
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("community-worker").factory());
    private final java.util.concurrent.atomic.AtomicBoolean dispatched = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean closed;

    @Scheduled(fixedDelayString="${spring.ai.community.poll-delay-ms:5000}",initialDelayString="${spring.ai.community.initial-delay-ms:15000}")
    public void tick() {
        if (closed || !dispatched.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try { pollAndExecute(); }
                finally { dispatched.set(false); }
            });
        } catch (java.util.concurrent.RejectedExecutionException closing) { dispatched.set(false); }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() { closed = true; executor.shutdownNow(); }

    private void pollAndExecute() {
        String token=UUID.randomUUID().toString();
        boolean owned=false;
        try {
            // event ACK 必须在本地 inbox/task 提交之后；ACK 丢失时 UNIQUE(event_id,bot_id) 去重。
            JsonNode events=transport.internalPost("/internal/community/events/claim",Map.of("limit",20,"leaseSeconds",600));
            for(JsonNode event:events) {
                store.ingest(event);
                transport.internalPost("/internal/community/events/"+event.path("id").asLong()+"/ack",
                        Map.of("leaseToken",event.path("leaseToken").asText()));
            }
            int leaseSeconds=(int)Math.min(Integer.MAX_VALUE,Math.max(600L,properties.getSseTimeout()/1000L+120L));
            owned=mapper.acquireWorker(token,leaseSeconds)>0;
            if(!owned) return;
            CommunityTask task=store.claim(leaseSeconds);
            if(task!=null) execute(task);
        } catch(RuntimeException error) {
            log.warn("社区后台轮询失败: {}",error.getClass().getSimpleName());
        } finally {
            if(owned) mapper.releaseWorker(token);
        }
    }

    public void execute(CommunityTask task) {
        JsonNode decision=null;
        boolean publicationConfirmed=false;
        try {
            if(task.getDecisionJson()==null) {
                long epoch=task.getMemoryEpoch()==null?store.epoch(task.getBotId()):task.getMemoryEpoch();
                int attempt=task.getAttempts()+1;
                mapper.attempt(task.getId(),epoch);
                task.setMemoryEpoch(epoch);
                Map<String,Object> result=community.generate(task.getId(),task.getBotId(),task.getPostId(),task.getCommentId(),attempt,false,epoch);
                decision=objectMapper.valueToTree(result);
                mapper.decide(task.getId(),objectMapper.writeValueAsString(result),decision.path("contextVersion").asText());
            } else decision=objectMapper.readTree(task.getDecisionJson());
            if("SKIP".equals(decision.path("decision").asText())) {
                mapper.finish(task.getId(),"SKIPPED",null);
                return;
            }
            Map<String,Object> publish=new LinkedHashMap<>();
            publish.put("taskId",task.getId());publish.put("botId",task.getBotId());publish.put("postId",task.getPostId());
            publish.put("contextCommentId",task.getCommentId());
            publish.put("parentId",decision.path("targetCommentId").isNumber()?decision.path("targetCommentId").asLong():null);
            publish.put("rootEventId",task.getRootEventId());publish.put("contextVersion",decision.path("contextVersion").asText());
            publish.put("content",decision.path("content").asText());
            JsonNode published=transport.internalPost("/internal/community/comments",publish);
            long commentId=published.path("commentId").asLong();
            if(commentId<=0) throw new AIServiceException.ConnectionException("主服务没有确认评论发布结果");
            publicationConfirmed=true;
            recordPublication(task,decision,"SUCCEEDED",published,null);
            // 落库应答丢失时下次只重交同一 taskId；不能重跑模型。
            List<Long> sourceIds=new ArrayList<>();decision.path("sourceCommentIds").forEach(id -> sourceIds.add(id.asLong()));
            sourceIds.add(commentId);
            boolean allVisible=true;
            for(int offset=0;offset<sourceIds.size();offset+=100) {
                var batch=sourceIds.subList(offset,Math.min(sourceIds.size(),offset+100));
                JsonNode visibility=transport.internalPost("/internal/community/visibility",
                        Map.of("sourcePostIds",List.of(task.getPostId()),"sourceCommentIds",batch));
                if(!contains(visibility.path("visiblePostIds"),task.getPostId())
                        || !batch.stream().allMatch(id -> contains(visibility.path("visibleCommentIds"),id))) allVisible=false;
            }
            if(allVisible) {
                List<Long> participants=new ArrayList<>();decision.path("participants").forEach(id -> participants.add(id.asLong()));
                String postTitle=decision.path("postTitle").asText("文章 #"+task.getPostId());
                String summary="在《"+postTitle+"》参与公开讨论："+decision.path("content").asText();
                store.remember(task.getId(),task.getBotId(),decision.path("memoryEpoch").asLong(),task.getPostId(),commentId,summary,participants,sourceIds);
            }
            mapper.finish(task.getId(),"SUCCEEDED",null);
        } catch(RuntimeException error) {
            int code=error instanceof BackendApiTransport.InternalBusinessException business?business.businessCode():0;
            String reason=error instanceof AIServiceException?error.getMessage():"后台执行失败，将有限重试";
            if(reason==null) reason="后台执行失败";
            if(reason.length()>300) reason=reason.substring(0,300);
            if(!publicationConfirmed && decision!=null && !"SKIP".equals(decision.path("decision").asText())) {
                try { recordPublication(task,decision,"FAILED",null,reason); }
                catch(RuntimeException auditError) { log.warn("社区发布结果记录失败: taskId={}",task.getId()); }
            }
            boolean terminal=Set.of(1700,1702,1703,1704,1101,1202,1203,404,403).contains(code);
            if(terminal || task.getFailures()>=2) mapper.finish(task.getId(),terminal?"SKIPPED":"FAILED",reason);
            else mapper.retry(task.getId(),reason,code==1701,30*(task.getFailures()+1));
            log.warn("社区任务执行失败: taskId={}, code={}, retry={}",task.getId(),code,!terminal && task.getFailures()<2);
        }
    }
    private void recordPublication(CommunityTask task,JsonNode decision,String status,JsonNode published,String error) {
        String runId=decision.path("modelRunId").asText(null);
        if(runId==null) runId=mapper.latestGeneratedRun(task.getId());
        if(runId==null) return;
        Map<String,Object> result=objectMapper.convertValue(decision,Map.class);
        result.put("modelRunId",runId);result.put("publicationStatus",status);
        if(published!=null) {
            result.put("publishedCommentId",published.path("commentId").asLong());
            if(!published.path("createdAt").isMissingNode()) result.put("publishedAt",published.path("createdAt").asText());
            result.remove("publicationError");result.remove("error");
        } else { result.put("publicationError",error);result.put("error",error); }
        mapper.updateRun(runId,task.getId(),task.getBotId(),status,objectMapper.writeValueAsString(result),error);
    }
    private boolean contains(JsonNode list,long id) { for(JsonNode item:list) if(item.asLong()==id)return true;return false; }
}
