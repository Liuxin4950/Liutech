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
        if (task != null) mapper.lease(task.getId(), leaseSeconds);
        return task;
    }

    @Transactional
    public CommunityTaskRetryResult retryFailed(String taskId) {
        CommunityTask task=mapper.taskForRetry(taskId);
        if(task==null) return new CommunityTaskRetryResult(false,"任务不存在");
        if(!"FAILED".equals(task.getStatus())) return new CommunityTaskRetryResult(false,"只有失败任务可以重新排队，当前状态："+task.getStatus());
        if(mapper.retryFailed(taskId)==0) return new CommunityTaskRetryResult(false,"任务状态已变化，请刷新列表");
        log.info("管理员重新排队社区失败任务: taskId={}, attempts={}, hasDecision={}",taskId,task.getAttempts(),task.getDecisionJson()!=null);
        return new CommunityTaskRetryResult(true,"已重新排队；已有生成结果将直接重交，新推理继续计入任务额度");
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
