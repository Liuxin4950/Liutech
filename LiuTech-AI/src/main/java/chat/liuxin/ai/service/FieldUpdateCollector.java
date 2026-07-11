package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.FieldUpdatePayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 写作模式工具调用副作用收集器。
 *
 * 写作工具 {@code applyArticleUpdate} 被 AI 通过 function calling 调用时，
 * 把 {@link FieldUpdatePayload} 压入本收集器（线程安全）。
 * 注册 {@link #addListener} 可在每次 add 时立即收到回调（用于实时发 SSE field-update 事件），
 * 避免等到 onComplete 才一次性推送导致前端"卡住"。
 *
 * 通过 {@link org.springframework.ai.chat.model.ToolContext} 在工具方法与流处理器之间传递，
 * 避免在 reactive 线程上使用 ThreadLocal。
 *
 * @author 刘鑫
 */
public final class FieldUpdateCollector {

    /** ToolContext 上下文键 */
    public static final String CONTEXT_KEY = "writingFieldUpdateCollector";

    private final List<FieldUpdatePayload> items = new CopyOnWriteArrayList<>();
    private final List<Consumer<FieldUpdatePayload>> listeners = new CopyOnWriteArrayList<>();

    /** 注册一个回调，每次 add 时立即触发（用于实时发 SSE） */
    public void addListener(Consumer<FieldUpdatePayload> listener) {
        if (listener != null) listeners.add(listener);
    }

    /** 工具方法调用时压入一条字段更新，并立即触发所有 listener */
    public void add(FieldUpdatePayload payload) {
        if (payload == null) return;
        items.add(payload);
        for (Consumer<FieldUpdatePayload> l : listeners) {
            try { l.accept(payload); } catch (Exception e) { /* listener 失败不影响主流程 */ }
        }
    }

    /** 流结束后取出并清空全部收集到的字段更新 */
    public List<FieldUpdatePayload> drain() {
        if (items.isEmpty()) return Collections.emptyList();
        List<FieldUpdatePayload> snapshot = new ArrayList<>(items);
        items.clear();
        return snapshot;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}

