package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 工具事件负载。
 * 用于 tool-start 和 tool-result 事件，记录工具执行开始和结束状态。
 *
 * tool-start 示例：
 *   {
 *     "toolName": "public.searchArticles",
 *     "displayName": "搜索文章",
 *     "inputSummary": "keyword=Spring, limit=6"
 *   }
 *
 * tool-result 示例：
 *   {
 *     "toolName": "public.searchArticles",
 *     "success": true,
 *     "durationMs": 127,
 *     "resultSummary": "返回 6 篇文章",
 *     "errorMessage": null
 *   }
 *
 * @author liuxin
 * @see AgentSseEnvelope
 */
@Data
@Builder
public class AgentToolEventPayload {

    /**
     * 工具名称。
     * 格式：命名空间.工具方法名，例如 public.searchArticles
     */
    private String toolName;

    /**
     * 工具展示名称。
     * 用于前端展示给用户的友好名称，例如"搜索文章"。
     */
    private String displayName;

    /**
     * 输入摘要。
     * 对工具输入参数的简略描述，不包含敏感信息。
     * 格式：key=value, key2=value2
     */
    private String inputSummary;

    /**
     * 是否成功。
     * tool-result 事件使用，tool-start 时为 null。
     */
    private Boolean success;

    /**
     * 执行耗时（毫秒）。
     * tool-result 事件使用，tool-start 时为 null。
     */
    private Long durationMs;

    /**
     * 结果摘要。
     * 对工具返回结果的简略描述，不包含敏感信息。
     * 例如："返回 6 篇文章"
     */
    private String resultSummary;

    /**
     * 错误信息。
     * 工具执行失败时的原因，仅 success=false 时有值。
     */
    private String errorMessage;

    /**
     * 创建工具开始事件负载。
     *
     * @param toolName     工具名称，非空
     * @param displayName  展示名称，非空
     * @param inputSummary 输入摘要，可空
     */
    public static AgentToolEventPayload start(String toolName, String displayName, String inputSummary) {
        return AgentToolEventPayload.builder()
                .toolName(toolName)
                .displayName(displayName)
                .inputSummary(inputSummary)
                .build();
    }

    /**
     * 创建工具结果事件负载（成功）。
     *
     * @param toolName      工具名称，非空
     * @param displayName   展示名称，非空
     * @param durationMs    执行耗时，非空
     * @param resultSummary 结果摘要，可空
     */
    public static AgentToolEventPayload success(String toolName, String displayName, Long durationMs, String resultSummary) {
        return AgentToolEventPayload.builder()
                .toolName(toolName)
                .displayName(displayName)
                .success(true)
                .durationMs(durationMs)
                .resultSummary(resultSummary)
                .build();
    }

    /**
     * 创建工具结果事件负载（失败）。
     *
     * @param toolName     工具名称，非空
     * @param displayName  展示名称，非空
     * @param durationMs   执行耗时，非空
     * @param errorMessage 错误信息，非空
     */
    public static AgentToolEventPayload failure(String toolName, String displayName, Long durationMs, String errorMessage) {
        return AgentToolEventPayload.builder()
                .toolName(toolName)
                .displayName(displayName)
                .success(false)
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .build();
    }
}
