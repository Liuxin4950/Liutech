package chat.liuxin.ai.agent.response;

/**
 * Agent 错误发生阶段定义。
 *
 * 用于标识错误发生在哪个阶段，帮助前端定位问题。
 *
 * @author liuxin
 */
public enum AgentErrorStage {

    /**
     * 执行阶段。
     * Agent 主流程执行中发生的错误。
     */
    EXECUTE("execute"),

    /**
     * 计划阶段。
     * 构建执行计划时发生的错误。
     */
    PLAN("plan"),

    /**
     * 工具阶段。
     * 工具执行时发生的错误。
     */
    TOOL("tool"),

    /**
     * 结果阶段。
     * 处理结果或确认操作时发生的错误。
     */
    RESULT("result");

    private final String stage;

    AgentErrorStage(String stage) {
        this.stage = stage;
    }

    /**
     * 获取阶段字符串值。
     *
     * @return 阶段字符串
     */
    public String getStage() {
        return stage;
    }
}
