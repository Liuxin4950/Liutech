package chat.liuxin.ai.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 一轮 SSE 的配额、上游订阅和线程资源；所有关闭路径幂等。 */
final class StreamLifecycle implements AutoCloseable {
    final AtomicBoolean closed = new AtomicBoolean();
    final Disposable.Swap upstream = Disposables.swap();
    final AtomicReference<ExecutorService> tts = new AtomicReference<>();
    final AtomicReference<ScheduledExecutorService> heartbeat = new AtomicReference<>();
    private final AtomicBoolean released = new AtomicBoolean();
    private final Semaphore capacity;

    StreamLifecycle(Semaphore capacity) {
        this.capacity = capacity;
        if (!capacity.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前流式请求较多，请稍后重试");
        }
    }

    void bind(SseEmitter emitter, Runnable timeoutAction) {
        emitter.onCompletion(this::close);
        emitter.onError(error -> close());
        emitter.onTimeout(() -> {
            close();
            timeoutAction.run();
            emitter.complete();
        });
    }

    @Override public void close() {
        closed.set(true);
        upstream.dispose();
        SseEmitterHelper.shutdown(tts.getAndSet(null), true);
        SseEmitterHelper.shutdown(heartbeat.getAndSet(null), true);
        if (released.compareAndSet(false, true)) capacity.release();
    }
}
