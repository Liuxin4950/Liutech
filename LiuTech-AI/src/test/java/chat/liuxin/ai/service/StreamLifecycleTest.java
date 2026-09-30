package chat.liuxin.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamLifecycleTest {
    @Test void cancellationStopsUpstreamAndReturnsCapacityExactlyOnce() {
        Semaphore capacity = new Semaphore(1);
        var lifecycle = new StreamLifecycle(capacity);
        AtomicBoolean cancelled = new AtomicBoolean();
        lifecycle.upstream.update(Flux.never().doOnCancel(() -> cancelled.set(true)).subscribe());
        assertThrows(ResponseStatusException.class, () -> new StreamLifecycle(capacity));
        lifecycle.close();
        lifecycle.close();
        assertTrue(cancelled.get());
        assertTrue(lifecycle.closed.get());
        assertEquals(1, capacity.availablePermits());
    }

    @Test void lateSubscriptionAfterTimeoutIsDisposed() {
        var lifecycle = new StreamLifecycle(new Semaphore(1));
        lifecycle.close();
        var disposable = mock(reactor.core.Disposable.class);
        lifecycle.upstream.update(disposable);
        verify(disposable).dispose();
    }
}
