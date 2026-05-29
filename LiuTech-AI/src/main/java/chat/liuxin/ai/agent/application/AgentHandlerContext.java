package chat.liuxin.ai.agent.application;
import chat.liuxin.ai.dto.AgentUserContext;

import chat.liuxin.ai.agent.response.AgentPlanStep;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Agent Handler 执行上下文。
 *
 * <p>封装 Handler 执行所需的共享状态，避免在接口中传递过多参数。
 *
 * @author liuxin
 */
@Data
@Builder
public class AgentHandlerContext {

    /** 任务 ID（关联同一轮对话的所有事件）。 */
    private Long taskId;

    /** 用户上下文（用于权限判断和数据访问）。 */
    private AgentUserContext user;

    /** 执行计划（由 AgentService 构建）。 */
    private List<AgentPlanStep> plan;

    /** SSE 发射器（流式场景使用，非流式场景为 null）。 */
    private SseEmitter emitter;

    /** 对话 ID。 */
    private Long conversationId;

    /**
     * 创建执行上下文。
     *
     * @param taskId          任务 ID
     * @param user            用户上下文
     * @param plan            执行计划
     * @param emitter         SSE 发射器（可为 null）
     * @param conversationId  对话 ID
     * @return 执行上下文
     */
    public static AgentHandlerContext of(
            Long taskId,
            AgentUserContext user,
            List<AgentPlanStep> plan,
            SseEmitter emitter,
            Long conversationId) {
        return AgentHandlerContext.builder()
                .taskId(taskId)
                .user(user)
                .plan(plan)
                .emitter(emitter)
                .conversationId(conversationId)
                .build();
    }

    /**
     * 判断当前用户是否为管理员。
     *
     * @return true 如果用户是管理员
     */
    public boolean isAdmin() {
        return user != null && user.isAdmin();
    }

    /**
     * 获取用户 ID 字符串（用于日志）。
     *
     * @return 用户 ID 字符串，匿名用户返回 "anonymous"
     */
    public String getUserIdString() {
        return user == null ? "anonymous" : user.userIdString();
    }
}

