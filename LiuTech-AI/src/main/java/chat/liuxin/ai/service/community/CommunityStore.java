package chat.liuxin.ai.service.community;

import chat.liuxin.ai.dto.community.CommunityTask;
import chat.liuxin.ai.dto.community.CommunityTaskRetryResult;
import chat.liuxin.ai.mapper.CommunityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 短事务只管理执行状态；网络和模型调用始终在事务之外。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommunityStore {
    private final CommunityMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void ingest(JsonNode event) {
        mapper.inbox(event.path("id").asLong(), objectMapper.writeValueAsString(event));
        CommunityTask task = new CommunityTask();
        task.setId(UUID.randomUUID().toString());
        task.setEventId(event.path("id").asLong());
        task.setBotId(event.path("botId").asLong());
        task.setPostId(event.path("postId").asLong());
        task.setCommentId(event.path("commentId").isNumber() ? event.path("commentId").asLong() : null);
        task.setRootEventId(event.path("rootEventId").asText());
        mapper.ensureRole(task.getBotId());
        task.setMemoryEpoch(mapper.epoch(task.getBotId()));
        mapper.task(task);
    }

    @Transactional
    public CommunityTask claim(int leaseSeconds) {
        CommunityTask task = mapper.next();
        if (task != null && mapper.lease(task.getId(), leaseSeconds) == 0) return null;
        return task;
    }

    @Transactional
    public void cancel(String taskId) {
        mapper.cancel(taskId);
        log.info("社区任务取消状态已保存: taskId={}",taskId);
    }

    @Transactional
    public CommunityTaskRetryResult retryFailed(String taskId) {
        CommunityTask task=mapper.taskForRetry(taskId);
        if(task==null) return new CommunityTaskRetryResult(false,"任务不存在");
        if(!"FAILED".equals(task.getStatus())) return new CommunityTaskRetryResult(false,"当前任务已非失败状态，请刷新；历史单次失败不需要重试");
        describeRetry(task);
        if(mapper.retryFailed(taskId,"regenerate".equals(task.getRetryKind()))==0)
            return new CommunityTaskRetryResult(false,"任务状态已变化，请刷新列表");
        log.info("管理员重新排队社区失败任务: taskId={}, attempts={}, retryKind={}",taskId,task.getAttempts(),task.getRetryKind());
        return new CommunityTaskRetryResult(true,"已重新排队；"+task.getRetryReason(),task.getRetryKind());
    }

    /** 历史运行失败与当前任务失败分开；只根据已持久保存的事实解释恢复步骤。 */
    public void describeRetry(CommunityTask task) {
        JsonNode decision=savedDecision(task.getDecisionJson());
        if(decision!=null && decision.path("publishedCommentId").asLong()>0)
            task.setPublishedCommentId(decision.path("publishedCommentId").asLong());
        if(!"FAILED".equals(task.getStatus())) return;
        if(task.getPublishedCommentId()!=null) {
            task.setRetryKind("postprocess");
            task.setRetryReason("评论已发表，只恢复发布记录和公共记忆；不重新调用模型或重复发表评论");
        } else if(decision!=null && "SKIP".equals(decision.path("decision").asText())) {
            task.setRetryKind("complete");
            task.setRetryReason("已决定保持沉默，只恢复任务结束状态；不调用模型或发表评论");
        } else if(reusableDecision(decision)) {
            task.setRetryKind("publish");
            task.setRetryReason("继续提交已生成内容，无需重新调用模型；若讨论已变化，再读取当前上下文重新生成");
        } else {
            task.setRetryKind("regenerate");
            task.setRetryReason("重新读取文章和讨论后生成；调用模型时计入新的模型任务额度。沿用原任务，避免重复发表");
        }
    }

    JsonNode savedDecision(String json) {
        if(json==null || json.isBlank()) return null;
        try { return objectMapper.readTree(json); }
        catch(RuntimeException invalid) { return null; }
    }

    /** 升级前损坏的缓存不能无限重交；目标仍由主后端基于当前公开事实核验。 */
    static boolean reusableDecision(JsonNode decision) {
        if(decision==null || !decision.isObject()) return false;
        if(decision.path("publishedCommentId").asLong()>0) return true;
        String action=decision.path("decision").asText();
        if("SKIP".equals(action)) return true;
        if(!Set.of("COMMENT","REPLY").contains(action) || decision.path("contextVersion").asText().isBlank()
                || !decision.path("content").isTextual() || decision.path("content").asText().isBlank()
                || decision.path("content").asText().length()>1000) return false;
        JsonNode target=decision.path("targetCommentId");
        return "COMMENT".equals(action) ? target.isMissingNode() || target.isNull()
                : target.isIntegralNumber() && target.asLong()>0;
    }

    @Transactional
    public long epoch(long botId) {
        mapper.ensureRole(botId);
        return mapper.epoch(botId);
    }

    @Transactional
    public void clearMemory(long botId) {
        mapper.ensureRole(botId);
        mapper.epoch(botId);
        mapper.advanceEpoch(botId);
        int deleted = mapper.clearMemory(botId);
        log.info("社区角色记忆清空: botId={}, deleted={}", botId, deleted);
    }

    @Transactional
    public void remember(String taskId, long botId, long epoch, long postId, long commentId,
                         String summary, List<Long> participants, List<Long> sourceCommentIds) {
        mapper.ensureRole(botId);
        if (mapper.epoch(botId) != epoch) return;
        for (Long userId : participants.stream().distinct().sorted().toList()) {
            mapper.ensureUser(userId);
            if (mapper.userPurged(userId)) return;
        }
        String id = UUID.randomUUID().toString();
        if (mapper.memory(id,taskId,botId,postId,commentId,summary) > 0) {
            for (Long userId : participants) mapper.participant(id,userId);
            for (Long source : sourceCommentIds) mapper.memorySource(id,source);
            mapper.memorySource(id,commentId);
        }
    }

    @Transactional
    public int purgeUser(long userId) {
        for (Long botId : mapper.participantBots(userId)) {
            mapper.ensureRole(botId);
            mapper.epoch(botId);
            mapper.advanceEpoch(botId);
        }
        mapper.ensureUser(userId);
        mapper.userPurged(userId);
        mapper.markUserPurged(userId);
        int count = mapper.purgeParticipant(userId);
        log.info("社区记忆用户引用清理: userId={}, deleted={}",userId,count);
        return count;
    }
}
