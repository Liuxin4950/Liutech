package chat.liuxin.ai.agent.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Component
public class AgentStreamPublisher {

    public void send(SseEmitter emitter, String event, Object payload) {
        if (emitter == null) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(payload));
            }
        } catch (IOException e) {
            log.warn("发送 Agent SSE 事件失败: {}", event, e);
            emitter.completeWithError(e);
        }
    }
}
