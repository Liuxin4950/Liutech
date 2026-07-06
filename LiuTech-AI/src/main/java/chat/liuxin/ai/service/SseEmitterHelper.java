package chat.liuxin.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 发射器工具类。
 *
 * 抽取自 AiChatServiceImpl，供同步/流式服务共用。无状态，全部静态方法。
 */
@Slf4j
public final class SseEmitterHelper {

    private SseEmitterHelper() {}

    /**
     * 用 key1, value1, key2, value2, ... 的可变参数拼一个事件负载 Map,长度不匹配时忽略末尾单独的 key。
     */
    public static Map<String, Object> eventPayload(Object... keyValues) {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }

    /**
     * 线程安全地发送一条 SSE 事件。
     *
     * 用 emitter 对象做同步锁,避免并发线程(数据流 + 心跳 + TTS 回调)同时写导致的
     * ResponseBodyEmitter.send 序列化错乱。
     */
    public static void sendSseEvent(SseEmitter emitter, String event, Map<String, Object> data) throws IOException {
        Object safeData = data != null ? data : new HashMap<String, Object>();
        synchronized (emitter) {
            emitter.send(SseEmitter.event()
                    .name(event != null ? event : "unknown")
                    .data(safeData));
        }
    }

    /**
     * 尽力发送 error 事件,发送失败静默吞掉 —— 通常此时连接已断,再抛异常无意义。
     */
    public static void safeSendError(SseEmitter emitter, Long conversationId, String errorMsg) {
        try {
            sendSseEvent(emitter, "error", eventPayload("conversationId", conversationId, "error", errorMsg));
        } catch (Exception ignore) {
        }
    }

    /**
     * 关闭线程池:immediate=true 直接 shutdownNow(强制中断,用于错误/超时场景);
     * immediate=false 走优雅关闭并最多等 2 秒(用于正常收尾)。
     */
    public static void shutdown(ExecutorService executor, boolean immediate) {
        if (executor == null) return;
        try {
            if (immediate) {
                executor.shutdownNow();
            } else {
                executor.shutdown();
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (Exception ignore) {
        }
    }
}
