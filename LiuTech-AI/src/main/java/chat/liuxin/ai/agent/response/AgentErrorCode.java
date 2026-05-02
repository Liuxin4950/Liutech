package chat.liuxin.ai.agent.response;

/**
 * Agent 错误码定义。
 *
 * 定义所有 Agent SSE 错误事件的错误码，前端按错误码区分错误类型并展示对应文案。
 *
 * @author liuxin
 */
public enum AgentErrorCode {

    /**
     * Agent 执行异常。
     * 阶段：execute
     */
    AGENT_ERROR("AGENT_ERROR"),

    /**
     * 限流。
     * 阶段：execute
     */
    RATE_LIMITED("RATE_LIMITED"),

    /**
     * Action 已过期。
     * 阶段：result
     */
    ACTION_EXPIRED("ACTION_EXPIRED"),

    /**
     * 工具执行失败。
     * 阶段：tool
     */
    TOOL_FAILED("TOOL_FAILED"),

    /**
     * 工具执行超时。
     * 阶段：tool
     */
    TIMEOUT("TIMEOUT");

    private final String code;

    AgentErrorCode(String code) {
        this.code = code;
    }

    /**
     * 获取错误码字符串值。
     *
     * @return 错误码字符串
     */
    public String getCode() {
        return code;
    }
}
