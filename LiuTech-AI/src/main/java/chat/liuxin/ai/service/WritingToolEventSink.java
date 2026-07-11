package chat.liuxin.ai.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 写作工具事件回调：工具方法调用开始/结束时通过 ToolContext 触发，
 * StreamingChatService 订阅后转成 SSE tool-start/tool-result 事件推给前端。
 */
public class WritingToolEventSink {
    public static final String CONTEXT_KEY = "writingToolEventSink";

    private final BiConsumer<String, Map<String, Object>> eventSender;
    private final ThreadLocal<Instant> startTimes = new ThreadLocal<>();

    public WritingToolEventSink(BiConsumer<String, Map<String, Object>> eventSender) {
        this.eventSender = eventSender;
    }

    public void fireStart(String toolName, String displayName, String inputSummary) {
        startTimes.set(Instant.now());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolName);
        payload.put("displayName", displayName);
        if (inputSummary != null) payload.put("inputSummary", inputSummary);
        eventSender.accept("tool-start", payload);
    }

    public void fireSuccess(String toolName, String displayName, String resultSummary) {
        Map<String, Object> payload = basePayload(toolName, displayName);
        payload.put("success", true);
        if (resultSummary != null) payload.put("resultSummary", resultSummary);
        eventSender.accept("tool-result", payload);
    }

    public void fireError(String toolName, String displayName, String errorMessage) {
        Map<String, Object> payload = basePayload(toolName, displayName);
        payload.put("success", false);
        if (errorMessage != null) payload.put("errorMessage", errorMessage);
        eventSender.accept("tool-result", payload);
    }

    private Map<String, Object> basePayload(String toolName, String displayName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolName);
        payload.put("displayName", displayName);
        long durationMs = 0;
        Instant start = startTimes.get();
        startTimes.remove();
        if (start != null) durationMs = Duration.between(start, Instant.now()).toMillis();
        payload.put("durationMs", durationMs);
        return payload;
    }
}
