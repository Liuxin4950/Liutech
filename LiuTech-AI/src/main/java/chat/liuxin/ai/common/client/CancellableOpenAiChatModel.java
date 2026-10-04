package chat.liuxin.ai.common.client;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.services.async.ChatServiceAsync;
import com.openai.services.async.chat.ChatCompletionServiceAsync;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 补齐 Spring AI 2.0.0 的 SDK 流资源取消：每次订阅独立拥有响应句柄，终态关闭实际 HTTP 流。
 * 请求构造、增量/工具/用量解析、观测完全委托 OpenAiChatModel；不维护第二套模型协议。
 * 每次订阅只新建轻量模型与服务代理，复用同一 SDK 客户端及连接池，不依赖线程上下文。
 */
public final class CancellableOpenAiChatModel implements ChatModel {
    private static final Logger log = LoggerFactory.getLogger(CancellableOpenAiChatModel.class);
    private final OpenAIClient syncClient;
    private final OpenAIClientAsync asyncClient;
    private final OpenAiChatOptions options;
    private final ToolCallingManager toolCallingManager;
    private final ObservationRegistry observationRegistry;
    private final ChatModelObservationConvention observationConvention;
    private final OpenAiChatModel syncModel;

    public CancellableOpenAiChatModel(OpenAIClient syncClient, OpenAIClientAsync asyncClient,
            OpenAiChatOptions options, ToolCallingManager toolCallingManager,
            ObservationRegistry observationRegistry, ChatModelObservationConvention observationConvention) {
        this.syncClient = syncClient;
        this.asyncClient = asyncClient;
        this.options = options;
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
        this.syncModel = model(asyncClient);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return syncModel.call(prompt);
    }

    @Override
    public ChatOptions getOptions() {
        return syncModel.getOptions();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            StreamScope scope = new StreamScope();
            OpenAIClientAsync scopedClient = scopedProxy(OpenAIClientAsync.class, asyncClient, scope);
            try {
                return model(scopedClient).stream(prompt).doFinally(ignored -> scope.close());
            } catch (RuntimeException e) {
                scope.close();
                return Flux.error(e);
            }
        });
    }

    private OpenAiChatModel model(OpenAIClientAsync client) {
        OpenAiChatModel model = OpenAiChatModel.builder().openAiClient(syncClient).openAiClientAsync(client)
                .options(options).toolCallingManager(toolCallingManager).observationRegistry(observationRegistry).build();
        if (observationConvention != null) model.setObservationConvention(observationConvention);
        return model;
    }

    /** SDK 接口表面较大，仅代理 chat/completions 服务及流响应，不复制其它 SDK 功能。 */
    private static <T> T scopedProxy(Class<T> contract, T delegate, StreamScope scope) {
        return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
            final Object value;
            try {
                value = method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (value instanceof AsyncStreamResponse<?> response) return scope.register(response);
            if (value instanceof OpenAIClientAsync client) return scopedProxy(OpenAIClientAsync.class, client, scope);
            if (value instanceof ChatServiceAsync chat) return scopedProxy(ChatServiceAsync.class, chat, scope);
            if (value instanceof ChatCompletionServiceAsync completions) return scopedProxy(ChatCompletionServiceAsync.class, completions, scope);
            return value;
        }));
    }

    private static final class StreamScope {
        private final Set<ScopedResponse<?>> responses = new HashSet<>();
        private boolean closed;

        synchronized <T> ScopedResponse<T> register(AsyncStreamResponse<T> response) {
            ScopedResponse<T> guarded = new ScopedResponse<>(response);
            if (closed) guarded.close();
            else responses.add(guarded);
            return guarded;
        }

        void close() {
            final ArrayList<ScopedResponse<?>> toClose;
            synchronized (this) {
                if (closed) return;
                closed = true;
                toClose = new ArrayList<>(responses);
                responses.clear();
            }
            // 任一 SDK close 异常不能阻止其余句柄清理，也不能替换已确定的业务终态。
            for (ScopedResponse<?> response : toClose) {
                try { response.close(); }
                catch (RuntimeException e) {
                    // SDK 异常信息可能携带原始 HTTP 请求；只记录类型，不输出密钥或 prompt。
                    log.warn("关闭模型上游流失败: type={}", e.getClass().getSimpleName());
                }
            }
        }
    }

    private static final class ScopedResponse<T> implements AsyncStreamResponse<T> {
        private final AsyncStreamResponse<T> delegate;
        private boolean closed;

        ScopedResponse(AsyncStreamResponse<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized AsyncStreamResponse<T> subscribe(Handler<? super T> handler) {
            if (!closed) delegate.subscribe(handler);
            return this;
        }

        @Override
        public synchronized AsyncStreamResponse<T> subscribe(Handler<? super T> handler, Executor executor) {
            if (!closed) delegate.subscribe(handler, executor);
            return this;
        }

        @Override
        public CompletableFuture<Void> onCompleteFuture() {
            return delegate.onCompleteFuture();
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            delegate.close();
        }
    }
}
