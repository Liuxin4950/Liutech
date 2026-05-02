package chat.liuxin.ai.agent.response;

/**
 * Agent SSE 事件类型枚举。
 *
 * 定义所有 Agent SSE 事件类型，前端按 event: 分发到对应 handler。
 *
 * @author liuxin
 * @see AgentSseEnvelope
 */
public enum AgentSseEventType {

    /**
     * Agent 启动事件。
     * 声明任务、会话、意图、角色、能力。
     */
    AGENT_START("agent-start"),

    /**
     * Agent 执行计划事件。
     * 展示用户可理解的计划步骤，不暴露模型内部推理链。
     */
    AGENT_PLAN("agent-plan"),

    /**
     * 工具开始执行事件。
     * 通知前端工具调用开始。
     */
    TOOL_START("tool-start"),

    /**
     * 工具执行结果事件。
     * 通知前端工具调用完成（成功或失败）。
     */
    TOOL_RESULT("tool-result"),

    /**
     * 自然语言文本事件。
     * 只承载模型输出的自然语言文本，不承载结构化数据。
     */
    DATA("data"),

    /**
     * 文章搜索/推荐结果事件。
     * 只承载文章卡片列表和推荐/搜索元信息。
     */
    ARTICLE_RESULTS("article-results"),

    /**
     * 确认卡片事件。
     * 只承载待确认动作，不由模型自然语言触发。
     */
    CONFIRMATION_REQUIRED("confirmation-required"),

    /**
     * 确认操作执行结果事件。
     */
    ACTION_RESULT("action-result"),

    /**
     * Agent 任务完成事件。
     */
    COMPLETE("complete"),

    /**
     * 错误事件。
     * 包含稳定错误码和阶段字段。
     */
    ERROR("error");

    private final String eventName;

    AgentSseEventType(String eventName) {
        this.eventName = eventName;
    }

    /**
     * 获取 SSE event: 字段的值。
     *
     * @return event 名称字符串
     */
    public String getEventName() {
        return eventName;
    }
}
