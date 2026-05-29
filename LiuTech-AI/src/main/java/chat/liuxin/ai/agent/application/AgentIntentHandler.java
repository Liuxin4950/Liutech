package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.application.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;


/**
 * Agent Handler 接口。
 *
 * <p>每个 Handler 负责处理一类业务逻辑，通过策略模式分发请求。
 *
 * <p>设计原则：
 * <ul>
 *   <li>单一职责：每个 Handler 只处理一类业务</li>
 *   <li>统一安全门：权限校验在 AgentService 分发层统一完成，Handler 不自行负责</li>
 * </ul>
 *
 * @author liuxin
 */
public interface AgentIntentHandler {

    /**
     * 执行处理逻辑。
     *
     * @param request  用户请求
     * @param context  执行上下文（包含用户、任务、SSE 等信息）
     * @return 聊天响应
     */
    AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext context);
}

