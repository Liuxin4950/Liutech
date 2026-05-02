package chat.liuxin.ai.agent.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Agent SSE 统一事件信封。
 *
 * 所有 Agent SSE 事件都包装为此结构，前端按 event 字段分发到对应 handler。
 * JSON 结构示例：
 *   {
 *     "contractVersion": 1,
 *     "event": "article-results",
 *     "taskId": 123,
 *     "conversationId": 43,
 *     "timestamp": "2026-05-01T15:28:23.029Z",
 *     "payload": { ... }
 *   }
 *
 * 版本策略：
 * - contractVersion=1 为当前版本
 * - 破坏性变更必须递增版本号
 * - 前端根据版本号决定解析逻辑
 *
 * @author liuxin
 */
@Data
@Builder
public class AgentSseEnvelope<T> {

    /**
     * 协议版本，当前为 1。
     * 破坏性变更必须递增。Builder 和 Jackson 反序列化均默认为 1。
     */
    @Builder.Default
    private int contractVersion = 1;

    /**
     * 事件名称。
     * 前端 SSE parser 按此字段分发到对应 handler。
     * 取值见 AgentSseEventType 枚举。
     */
    private String event;

    /**
     * 任务 ID。
     * 关联同一轮对话的所有事件，前端用于追踪完整执行流程。
     */
    private Long taskId;

    /**
     * 对话 ID。
     * 用于多轮上下文追踪，前端用于关联历史消息。
     */
    private Long conversationId;

    /**
     * 事件时间戳。
     * UTC ISO-8601 格式，由 AgentSseEnvelope.of() 自动填充为当前时间。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    /**
     * 事件负载。
     * 类型由 event 字段决定，例如：
     * - agent-start → AgentStartPayload
     * - tool-start/tool-result → AgentToolEventPayload
     * - data → DataPayload
     * - error → AgentErrorPayload
     * - complete → AgentCompletePayload
     * - article-results → ArticleResultsPayload
     */
    private T payload;

    /**
     * 创建信封，时间戳自动设为当前 UTC 时间。
     */
    public static <T> AgentSseEnvelope<T> of(String event, Long taskId, Long conversationId, T payload) {
        return AgentSseEnvelope.<T>builder()
                .event(event)
                .taskId(taskId)
                .conversationId(conversationId)
                .timestamp(Instant.now())
                .payload(payload)
                .build();
    }
}
