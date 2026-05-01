package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.domain.AgentAction;
import chat.liuxin.ai.agent.domain.AgentActionStatus;
import chat.liuxin.ai.agent.domain.AgentActionType;
import chat.liuxin.ai.agent.domain.AgentTask;
import chat.liuxin.ai.agent.domain.AgentTaskStatus;
import chat.liuxin.ai.agent.persistence.AgentActionMapper;
import chat.liuxin.ai.agent.persistence.AgentTaskMapper;
import chat.liuxin.ai.agent.request.AdminArticleDraftRequest;
import chat.liuxin.ai.agent.response.AgentActionResultResponse;
import chat.liuxin.ai.agent.response.ConfirmationRequiredPayload;
import chat.liuxin.ai.agent.tool.AdminBlogClient;
import chat.liuxin.ai.security.AiToolAccessPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentActionService {

    private final AgentActionMapper agentActionMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AdminBlogClient adminBlogClient;
    private final ObjectMapper objectMapper;
    private final AgentToolCallRecorder toolCallRecorder;
    private final AiToolAccessPolicy toolAccessPolicy;

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationRequiredPayload createDraftAction(Long taskId, AgentUserContext user, AdminArticleDraftRequest draft) {
        toolAccessPolicy.assertAllowed(user, AgentActionType.CREATE_DRAFT);
        AgentAction action = baseAction(taskId, user, AgentActionType.CREATE_DRAFT, "post", null);
        action.setPayload(toJson(draft));
        agentActionMapper.insert(action);
        return ConfirmationRequiredPayload.builder()
                .actionId(action.getId())
                .actionType(AgentActionType.CREATE_DRAFT.name())
                .title("确认创建文章草稿")
                .description("Agent 生成的新文章第一次只能保存为草稿。确认后会调用主后端创建草稿，不会直接发布。")
                .preview(draft)
                .riskLevel("medium")
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationRequiredPayload createPublishAction(Long taskId, AgentUserContext user, Long postId) {
        toolAccessPolicy.assertAllowed(user, AgentActionType.PUBLISH_POST);
        AgentAction action = baseAction(taskId, user, AgentActionType.PUBLISH_POST, "post", postId);
        action.setPayload("{\"postId\":" + postId + "}");
        agentActionMapper.insert(action);
        return ConfirmationRequiredPayload.builder()
                .actionId(action.getId())
                .actionType(AgentActionType.PUBLISH_POST.name())
                .title("确认发布文章")
                .description("确认后会将该草稿发布为正文。请确保你已经审查过标题、正文、分类和标签。")
                .preview(java.util.Map.of("postId", postId))
                .riskLevel("high")
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationRequiredPayload createOfflineAction(Long taskId, AgentUserContext user, Long postId) {
        toolAccessPolicy.assertAllowed(user, AgentActionType.OFFLINE_POST);
        AgentAction action = baseAction(taskId, user, AgentActionType.OFFLINE_POST, "post", postId);
        action.setPayload("{\"postId\":" + postId + "}");
        agentActionMapper.insert(action);
        return ConfirmationRequiredPayload.builder()
                .actionId(action.getId())
                .actionType(AgentActionType.OFFLINE_POST.name())
                .title("确认下架文章")
                .description("确认后会将该文章状态改为草稿。")
                .preview(java.util.Map.of("postId", postId))
                .riskLevel("high")
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentActionResultResponse confirm(Long actionId, AgentUserContext user) {
        if (user == null || !user.isAdmin()) {
            return failure(actionId, null, "只有管理员可以确认 Agent 写操作");
        }
        AgentAction action = agentActionMapper.selectById(actionId);
        if (action == null) {
            return failure(actionId, null, "待确认操作不存在");
        }
        if (!String.valueOf(user.getUserId()).equals(action.getUserId())) {
            return failure(actionId, action.getActionType(), "不能确认其他用户发起的 Agent 操作");
        }
        if (!AgentActionStatus.PENDING.name().equals(action.getStatus())) {
            return failure(actionId, action.getActionType(), "该操作当前不可确认");
        }
        if (action.getExpiresAt() != null && action.getExpiresAt().isBefore(LocalDateTime.now())) {
            action.setStatus(AgentActionStatus.EXPIRED.name());
            agentActionMapper.updateById(action);
            updateTask(action.getTaskId(), AgentTaskStatus.FAILED, "Agent 操作已过期", "该操作已过期，请重新发起");
            return failure(actionId, action.getActionType(), "该操作已过期，请重新发起");
        }

        try {
            long start = System.currentTimeMillis();
            Object target;
            AgentActionType type = AgentActionType.valueOf(action.getActionType());
            toolAccessPolicy.assertAllowed(user, type);
            if (type == AgentActionType.CREATE_DRAFT) {
                AdminArticleDraftRequest draft = objectMapper.readValue(action.getPayload(), AdminArticleDraftRequest.class);
                draft.setStatus("draft");
                target = adminBlogClient.createDraft(draft, user.getBearerToken());
            } else if (type == AgentActionType.PUBLISH_POST) {
                target = adminBlogClient.publishPost(action.getTargetId(), user.getBearerToken());
            } else if (type == AgentActionType.OFFLINE_POST) {
                target = adminBlogClient.offlinePost(action.getTargetId(), user.getBearerToken());
            } else {
                throw new IllegalStateException("不支持的操作类型: " + action.getActionType());
            }

            Long resolvedTargetId = resolveTargetId(target, action.getTargetId());
            action.setTargetId(resolvedTargetId);
            action.setStatus(AgentActionStatus.EXECUTED.name());
            action.setResult(toJson(target));
            agentActionMapper.updateById(action);
            updateTask(action.getTaskId(), AgentTaskStatus.COMPLETED, "Agent 操作已确认并执行", null);
            toolCallRecorder.recordResult(
                    action.getTaskId(),
                    "admin." + type.name(),
                    java.util.Map.of("actionId", actionId, "targetId", resolvedTargetId == null ? "" : resolvedTargetId),
                    target,
                    System.currentTimeMillis() - start);
            return AgentActionResultResponse.builder()
                    .success(true)
                    .message("Agent 操作已执行")
                    .actionId(action.getId())
                    .actionType(action.getActionType())
                    .status(action.getStatus())
                    .target(target)
                    .build();
        } catch (Exception e) {
            action.setStatus(AgentActionStatus.FAILED.name());
            action.setResult(toJson(java.util.Map.of("error", e.getMessage() == null ? "未知错误" : e.getMessage())));
            agentActionMapper.updateById(action);
            updateTask(action.getTaskId(), AgentTaskStatus.FAILED, null, e.getMessage());
            toolCallRecorder.recordFailure(
                    action.getTaskId(),
                    "admin." + action.getActionType(),
                    java.util.Map.of("actionId", actionId, "targetId", action.getTargetId() == null ? "" : action.getTargetId()),
                    e.getMessage(),
                    0L);
            return failure(actionId, action.getActionType(), e.getMessage());
        }
    }

    private AgentAction baseAction(Long taskId, AgentUserContext user, AgentActionType type, String targetType, Long targetId) {
        AgentAction action = new AgentAction();
        action.setTaskId(taskId);
        action.setUserId(String.valueOf(user.getUserId()));
        action.setActionType(type.name());
        action.setStatus(AgentActionStatus.PENDING.name());
        action.setTargetType(targetType);
        action.setTargetId(targetId);
        action.setExpiresAt(LocalDateTime.now().plusMinutes(20));
        action.setCreatedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());
        return action;
    }

    private Long resolveTargetId(Object target, Long fallback) {
        if (target instanceof AdminBlogClient.AdminPostActionResult result && result.getPostId() != null) {
            return result.getPostId();
        }
        return fallback;
    }

    private void updateTask(Long taskId, AgentTaskStatus status, String summary, String error) {
        if (taskId == null) {
            return;
        }
        AgentTask task = agentTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(status.name());
        task.setResultSummary(summary);
        task.setErrorMessage(error);
        task.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    private AgentActionResultResponse failure(Long actionId, String actionType, String message) {
        return AgentActionResultResponse.builder()
                .success(false)
                .message(message)
                .actionId(actionId)
                .actionType(actionType)
                .status(AgentActionStatus.FAILED.name())
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 Agent 操作数据失败", e);
        }
    }
}
