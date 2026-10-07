package chat.liuxin.ai.common.client;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.async.ChatServiceAsync;
import com.openai.services.async.chat.ChatCompletionServiceAsync;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class CancellableOpenAiChatModelTest {
    private OpenAIClient sync;
    private OpenAIClientAsync async;
    private ChatCompletionServiceAsync completions;
    private CancellableOpenAiChatModel model;

    @BeforeEach
    void setUp() {
        sync = mock(OpenAIClient.class);
        async = mock(OpenAIClientAsync.class);
        ChatServiceAsync chat = mock(ChatServiceAsync.class);
        completions = mock(ChatCompletionServiceAsync.class);
        when(async.chat()).thenReturn(chat);
        when(chat.completions()).thenReturn(completions);
        model = model(sync, async);
    }

    @Test
    void cancellingFluxClosesActualSdkResponseExactlyOnce() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AsyncStreamResponse<ChatCompletionChunk> response = response(future);
        when(completions.createStreaming(any(ChatCompletionCreateParams.class))).thenReturn(response);
        var subscription = model.stream(prompt()).subscribe();
        subscription.dispose();
        subscription.dispose();
        future.complete(null);
        verify(response, times(1)).close();
        verify(sync, never()).close();
        verify(async, never()).close();
    }

    @Test
    void completionAndFailureAlsoReleaseSdkResponse() {
        CompletableFuture<Void> successFuture = new CompletableFuture<>();
        CompletableFuture<Void> errorFuture = new CompletableFuture<>();
        AsyncStreamResponse<ChatCompletionChunk> success = response(successFuture);
        AsyncStreamResponse<ChatCompletionChunk> failure = response(errorFuture);
        when(completions.createStreaming(any(ChatCompletionCreateParams.class))).thenReturn(success, failure);
        model.stream(prompt()).subscribe();
        successFuture.complete(null);
        verify(success, times(1)).close();
        AtomicReference<Throwable> observedError = new AtomicReference<>();
        model.stream(prompt()).subscribe(ignored -> {}, observedError::set);
        errorFuture.completeExceptionally(new IllegalStateException("mock upstream failure"));
        assertNotNull(observedError.get());
        verify(failure, times(1)).close();
    }

    @Test
    void cancellingOneSubscriptionDoesNotCloseAnotherOnTheSameModel() {
        AsyncStreamResponse<ChatCompletionChunk> first = response(new CompletableFuture<>());
        AsyncStreamResponse<ChatCompletionChunk> second = response(new CompletableFuture<>());
        when(completions.createStreaming(any(ChatCompletionCreateParams.class))).thenReturn(first, second);
        var flux = model.stream(prompt());
        var firstSubscription = flux.subscribe();
        var secondSubscription = flux.subscribe();
        firstSubscription.dispose();
        verify(first, times(1)).close();
        verify(second, never()).close();
        secondSubscription.dispose();
        verify(second, times(1)).close();
    }

    @Test
    void responseArrivingAfterCancellationIsClosedWithoutStartingSubscription() throws Exception {
        CountDownLatch creating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AsyncStreamResponse<ChatCompletionChunk> response = response(new CompletableFuture<>());
        doAnswer(ignored -> { closed.countDown(); return null; }).when(response).close();
        when(completions.createStreaming(any(ChatCompletionCreateParams.class))).thenAnswer(ignored -> {
            creating.countDown();
            // 模拟底层创建句柄暂时不响应 interrupt，但最终仍返回可关闭的响应。
            boolean waiting = true;
            while (waiting) {
                try { release.await(); waiting = false; } catch (InterruptedException ignoredInterruption) { }
            }
            return response;
        });
        var subscription = model.stream(prompt()).subscribeOn(Schedulers.boundedElastic()).subscribe();
        try {
            assertTrue(creating.await(2, TimeUnit.SECONDS));
            subscription.dispose();
            release.countDown();
            assertTrue(closed.await(2, TimeUnit.SECONDS));
            verify(response, times(1)).close();
            verify(response, never()).subscribe(any(AsyncStreamResponse.Handler.class));
        } finally {
            release.countDown();
            subscription.dispose();
        }
    }

    @Test
    void frameworkStillBuildsRequestOptionsInsteadOfASecondParser() {
        AsyncStreamResponse<ChatCompletionChunk> response = response(new CompletableFuture<>());
        when(completions.createStreaming(any(ChatCompletionCreateParams.class))).thenReturn(response);
        var subscription = model.stream(new Prompt("hello", OpenAiChatOptions.builder()
                .model("configured-request-model").maxTokens(1234).temperature(0.2).build())).subscribe();
        var params = org.mockito.ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(completions).createStreaming(params.capture());
        assertEquals("configured-request-model", params.getValue().model().asString());
        assertEquals(1234L, params.getValue().maxTokens().orElseThrow());
        assertEquals(0.2, params.getValue().temperature().orElseThrow());
        subscription.dispose();
    }

    @Test
    void disposingRealSdkStreamClosesLoopbackHttpConnection() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                String chunk = "data: {\"id\":\"local\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"local\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"hello\"},\"finish_reason\":null}]}\n\n";
                output.write(chunk.getBytes(StandardCharsets.UTF_8));
                output.flush();
                connected.countDown();
                byte[] heartbeat = (":" + " ".repeat(16_384) + "\n\n").getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < 250; i++) {
                    output.write(heartbeat);
                    output.flush();
                    try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            } catch (IOException e) {
                disconnected.countDown();
            } finally {
                exchange.close();
            }
        });
        server.start();
        OpenAIClient client = OpenAiSetup.setupSyncClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "local-test-key", null, null, null, null, false, false, "deepseek-ai/DeepSeek-V3.2",
                Duration.ofSeconds(5), 0, null, Map.of(), ObservationRegistry.NOOP, null, List.of());
        var subscription = model(client, client.async()).stream(prompt()).subscribe(ignored -> {}, ignored -> {});
        try {
            assertTrue(connected.await(3, TimeUnit.SECONDS), "本地 HTTP 流应已建立");
            subscription.dispose();
            assertTrue(disconnected.await(3, TimeUnit.SECONDS), "取消必须关闭实际连接，不能只丢弃响应分片");
        } finally {
            subscription.dispose();
            client.close();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private AsyncStreamResponse<ChatCompletionChunk> response(CompletableFuture<Void> future) {
        AsyncStreamResponse<ChatCompletionChunk> response = mock(AsyncStreamResponse.class);
        when(response.subscribe(any(AsyncStreamResponse.Handler.class))).thenReturn(response);
        when(response.onCompleteFuture()).thenReturn(future);
        return response;
    }

    private CancellableOpenAiChatModel model(OpenAIClient sync, OpenAIClientAsync async) {
        return new CancellableOpenAiChatModel(sync, async,
                OpenAiChatOptions.builder().model("deepseek-ai/DeepSeek-V3.2").maxRetries(0).build(),
                ToolCallingManager.builder().build(), ObservationRegistry.NOOP, null);
    }

    private Prompt prompt() {
        return new Prompt("hello");
    }
}
