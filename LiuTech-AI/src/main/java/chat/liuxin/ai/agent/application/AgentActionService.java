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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Agent 动作服务。
 *
 * <p>负责创建和管理 Agent 动作（action），包括：
 * <ul>
 *   <li>创建草稿动作</li>
 *   <li>创建发布动作</li>
 *   <li>创建下架动作</li>
 *   <li>确认执行动作</li>
 * </ul>
 *
 * <p>事务管理：
 * <ul>
 *   <li>所有 public 方法使用 @Transactional</li>
 *   <li>事务内包含数据库操作和远程 HTTP 调用</li>
 * </ul>
 *
 * <p>注意：远程 HTTP 调用在事务内执行，若 backend 响应慢可能占用数据库连接。
 * 本期保持此设计，风险可控。
 *
 * @author liuxin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentActionService {

    /**
     * 动作过期时间（分钟）。
     */
    private static final int ACTION_EXPIRE_MINUTES = 20;

    /**
     * 通用的用户友好错误信息。
     * 用于替换内部异常信息，避免泄露敏感细节。
     */
    private static final String GENERIC_ERROR_MESSAGE = "操作执行失败，请稍后重试";

    private final AgentActionMapper agentActionMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AdminBlogClient adminBlogClient;
    private final ObjectMapper objectMapper;
    private final AgentToolCallRecorder toolCallRecorder;
    private final AiToolAccessPolicy toolAccessPolicy;

    /**
     * 创建草稿动作。
     *
     * @param taskId 任务 ID
     * @param user   用户上下文
     * @param draft  草稿内容
     * @return 确认卡片负载
     */
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

    /**
     * 创建发布动作。
     *
     * @param taskId 任务 ID
     * @param user   用户上下文
     * @param postId 文章 ID
     * @return 确认卡片负载
     */
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

    /**
     * 创建下架动作。
     *
     * @param taskId 任务 ID
     * @param user   用户上下文
     * @param postId 文章 ID
     * @return 确认卡片负载
     */
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

    /**
     * 确认执行动作。
     *
     * <p>执行流程：
     * <ol>
     *   <li>校验用户权限和管理员身份</li>
     *   <li>校验动作存在性和归属</li>
     *   <li>校验动作状态为 PENDING</li>
     *   <li>校验动作未过期</li>
     *   <li>执行对应的后端操作</li>
     *   <li>更新动作状态为 EXECUTED</li>
     *   <li>记录工具调用审计</li>
     * </ol>
     *
     * <p>返回路径：
     * <ul>
     *   <li>成功：返回包含 success=true 的响应</li>
     *   <li>失败：返回包含 success=false 和错误信息的响应</li>
     *   <li>过期：返回 409 Conflict，错误码 ACTION_EXPIRED</li>
     * </ul>
     *
     * @param actionId 动作 ID
     * @param user     用户上下文
     * @return 执行结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentActionResultResponse confirm(Long actionId, AgentUserContext user) {
        // 校验用户权限
        if (user == null || !user.isAdmin()) {
            return failure(actionId, null, "只有管理员可以确认 Agent 写操作");
        }

        // 查询动作
        AgentAction action = agentActionMapper.selectById(actionId);
        if (action == null) {
            return failure(actionId, null, "待确认操作不存在");
        }

        // 校验动作归属
        if (!String.valueOf(user.getUserId()).equals(action.getUserId())) {
            return failure(actionId, action.getActionType(), "不能确认其他用户发起的 Agent 操作");
        }

        // 校验动作状态
        if (!AgentActionStatus.PENDING.name().equals(action.getStatus())) {
            return failure(actionId, action.getActionType(), "该操作当前不可确认");
        }

        // 校验动作过期
        if (action.getExpiresAt() != null && action.getExpiresAt().isBefore(LocalDateTime.now())) {
            action.setStatus(AgentActionStatus.EXPIRED.name());
            agentActionMapper.updateById(action);
            updateTask(action.getTaskId(), AgentTaskStatus.FAILED, "Agent 操作已过期", "该操作已过期，请重新发起");
            return failure(actionId, action.getActionType(), "该操作已过期，请重新发起");
        }

        // 执行动作
        try {
            long start = System.currentTimeMillis();
            Object target;

            // 解析动作类型，使用 equals() 比较枚举
            AgentActionType type;
            try {
                type = AgentActionType.valueOf(action.getActionType());
            } catch (IllegalArgumentException e) {
                return failure(actionId, action.getActionType(), "不支持的操作类型");
            }

            toolAccessPolicy.assertAllowed(user, type);

            if (type.equals(AgentActionType.CREATE_DRAFT)) {
                AdminArticleDraftRequest draft = objectMapper.readValue(action.getPayload(), AdminArticleDraftRequest.class);
                draft.setStatus("draft");
                target = adminBlogClient.createDraft(draft, user.getBearerToken());
            } else if (type.equals(AgentActionType.PUBLISH_POST)) {
                target = adminBlogClient.publishPost(action.getTargetId(), user.getBearerToken());
            } else if (type.equals(AgentActionType.OFFLINE_POST)) {
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
            log.error("Agent 操作执行失败: actionId={}, error={}", actionId, e.getMessage(), e);
            action.setStatus(AgentActionStatus.FAILED.name());
            // 不泄露内部异常信息，只记录通用错误
            action.setResult(toJson(java.util.Map.of("error", GENERIC_ERROR_MESSAGE)));
            agentActionMapper.updateById(action);
            updateTask(action.getTaskId(), AgentTaskStatus.FAILED, null, GENERIC_ERROR_MESSAGE);
            toolCallRecorder.recordFailure(
                    action.getTaskId(),
                    "admin." + action.getActionType(),
                    java.util.Map.of("actionId", actionId, "targetId", action.getTargetId() == null ? "" : action.getTargetId()),
                    GENERIC_ERROR_MESSAGE,
                    0L);
            // 返回通用错误信息给前端
            return failure(actionId, action.getActionType(), GENERIC_ERROR_MESSAGE);
        }
    }

    /**
     * 创建基础动作。
     */
    private AgentAction baseAction(Long taskId, AgentUserContext user, AgentActionType type, String targetType, Long targetId) {
        AgentAction action = new AgentAction();
        action.setTaskId(taskId);
        action.setUserId(String.valueOf(user.getUserId()));
        action.setActionType(type.name());
        action.setStatus(AgentActionStatus.PENDING.name());
        action.setTargetType(targetType);
        action.setTargetId(targetId);
        action.setExpiresAt(LocalDateTime.now().plusMinutes(ACTION_EXPIRE_MINUTES));
        action.setCreatedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());
        return action;
    }

    /**
     * 解析目标 ID。
     */
    private Long resolveTargetId(Object target, Long fallback) {
        if (target instanceof AdminBlogClient.AdminPostActionResult result && result.getPostId() != null) {
            return result.getPostId();
        }
        return fallback;
    }

    /**
     * 更新任务状态。
     */
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

    /**
     * 创建失败响应。
     */
    private AgentActionResultResponse failure(Long actionId, String actionType, String message) {
        return AgentActionResultResponse.builder()
                .success(false)
                .message(message)
                .actionId(actionId)
                .actionType(actionType)
                .status(AgentActionStatus.FAILED.name())
                .build();
    }

    /**
     * 序列化为 JSON。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化 Agent 操作数据失败: {}", e.getMessage());
            throw new IllegalStateException("序列化 Agent 操作数据失败", e);
        }
    }
}
