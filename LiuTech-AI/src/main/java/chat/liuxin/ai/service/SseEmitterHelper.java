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
 * <p>抽取自 AiChatServiceImpl，供同步/流式服务共用。无状态，全部静态方法。
 */
@Slf4j
public final class SseEmitterHelper {

    private SseEmitterHelper() {}

    public static Map<String, Object> eventPayload(Object... keyValues) {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }

    public static void sendSseEvent(SseEmitter emitter, String event, Map<String, Object> data) throws IOException {
        Object safeData = data != null ? data : new HashMap<String, Object>();
        synchronized (emitter) {
            emitter.send(SseEmitter.event()
                    .name(event != null ? event : "unknown")
                    .data(safeData));
        }
    }

    public static void safeSendError(SseEmitter emitter, Long conversationId, String errorMsg) {
        try {
            sendSseEvent(emitter, "error", eventPayload("conversationId", conversationId, "error", errorMsg));
        } catch (Exception ignore) {
        }
    }

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
