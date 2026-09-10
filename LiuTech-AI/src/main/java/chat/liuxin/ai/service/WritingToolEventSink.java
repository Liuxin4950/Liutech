package chat.liuxin.ai.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 写作工具事件回调：工具方法调用开始/结束时通过 ToolContext 触发，
 * StreamingChatService 订阅后转成 SSE tool-start/tool-result 事件推给前端。
 *
 * <p>关于耗时统计方式（这里踩过坑）：早期用 {@code ThreadLocal<Instant>} 记录开始时间，
 * 但 Spring AI 的工具执行发生在响应式线程上，与 {@code fireStart} 不一定同线程，
 * 于是 {@code startTimes.get()} 常年取到 null，前端看到的 {@code durationMs} 恒为 0 —— 一个假耗时。
 * 现在改成按工具名维护「开始时间栈」：{@code fireStart} 压栈、{@code fireResult} 弹栈，
 * 跨线程也能正确配对，同名工具被连续调用时也不会串。
 *
 * <p>事件负载字段（前端据此渲染真实的执行时间线，不再自己编进度）：
 * <ul>
 *   <li>tool-start：{@code toolName}、{@code displayName}、{@code inputSummary?}、{@code startedAt}（epoch 毫秒）</li>
 *   <li>tool-result：{@code toolName}、{@code displayName}、{@code success}、{@code durationMs}、
 *       {@code finishedAt}、成功时 {@code resultSummary?}、失败时 {@code errorMessage?}</li>
 * </ul>
 */
public class WritingToolEventSink {
    public static final String CONTEXT_KEY = "writingToolEventSink";

    private final BiConsumer<String, Map<String, Object>> eventSender;

    /** 工具名 → 开始时间栈（同一工具可能被连续调用，用栈保证先进后出正确配对） */
    private final Map<String, Deque<Instant>> startTimes = new ConcurrentHashMap<>();

    public WritingToolEventSink(BiConsumer<String, Map<String, Object>> eventSender) {
        this.eventSender = eventSender;
    }

    public void fireStart(String toolName, String displayName, String inputSummary) {
        Instant startedAt = Instant.now();
        startTimes.computeIfAbsent(toolName, key -> new ArrayDeque<>()).push(startedAt);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolName);
        payload.put("displayName", displayName);
        if (inputSummary != null) payload.put("inputSummary", inputSummary);
        payload.put("startedAt", startedAt.toEpochMilli());
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
        Instant finishedAt = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolName);
        payload.put("displayName", displayName);

        // 弹出该工具最近一次 fireStart 的时间；没有对应 start（异常路径）时耗时为 0
        Instant startedAt = null;
        Deque<Instant> stack = startTimes.get(toolName);
        if (stack != null) {
            synchronized (stack) {
                startedAt = stack.poll();
            }
        }
        payload.put("durationMs", startedAt == null ? 0L
                : Math.max(0L, java.time.Duration.between(startedAt, finishedAt).toMillis()));
        payload.put("finishedAt", finishedAt.toEpochMilli());
        return payload;
    }
}
