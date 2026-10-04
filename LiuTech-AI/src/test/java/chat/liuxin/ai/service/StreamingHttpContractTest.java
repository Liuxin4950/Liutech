package chat.liuxin.ai.service;

import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.common.tts.AvatarCueService;
import chat.liuxin.ai.common.tts.TtsSegmenter;
import chat.liuxin.ai.common.utils.AuthUtils;
import chat.liuxin.ai.controller.AiChatController;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.config.AvatarCueProperties;
import chat.liuxin.ai.infra.config.TtsSegmenterProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.infra.exception.GlobalExceptionHandler;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.PromptBudget;
import chat.liuxin.ai.service.tts.TtsSpeechService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 MVC/HTTP 回归：错误事件发送后必须正常结束 chunked 流，不能再混入全局 JSON 响应。 */
class StreamingHttpContractTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
    @Import({AiChatController.class, GlobalExceptionHandler.class})
    static class Application {
        @Bean Fixture fixture() { return new Fixture(); }
        @Bean StreamingChatService streaming(Fixture fixture) { return fixture.service; }
        @Bean MemoryService memory(Fixture fixture) { return fixture.memory; }
        @Bean AuthUtils authUtils() { return new AuthUtils(); }
        @Bean AiChatService facade(Fixture fixture) {
            AiChatService facade = mock(AiChatService.class);
            when(facade.processStreamChat(any(), nullable(Long.class), anyString()))
                    .thenAnswer(call -> fixture.service.processStreamChat(call.getArgument(0), call.getArgument(1),
                            "test-model", fixture.params, call.getArgument(2)));
            return facade;
        }
        @Bean(destroyMethod = "close") SimpleMeterRegistry registry(Fixture fixture) { return fixture.registry; }
    }

    static class Fixture {
        final AiChatProperties properties = new AiChatProperties();
        final MemoryService memory = mock(MemoryService.class);
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final AtomicReference<Flux<String>> upstream = new AtomicReference<>(Flux.never());
        final AiModelPolicy.ModelParameters params = new AiModelPolicy.ModelParameters(.3, 1024, 8192, 6656, false, false, "test");
        final StreamingChatService service;

        Fixture() {
            properties.setSseTimeout(5_000);
            SiliconFlowChatClient client = mock(SiliconFlowChatClient.class);
            ChatServiceHelper helper = mock(ChatServiceHelper.class);
            when(helper.prepareMessages(any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any()))
                    .thenReturn(List.of(new UserMessage("hello")));
            when(client.streamChat(anyList(), anyString(), any(), any(), any(), anyString(), anyMap()))
                    .thenAnswer(ignored -> upstream.get());
            service = new StreamingChatService(client, memory, helper, mock(TtsSpeechService.class),
                    new TtsSegmenter(new TtsSegmenterProperties()), new AvatarCueService(new AvatarCueProperties()),
                    properties, new PromptBudget(properties), mock(BlogApiClient.class), new AiMetrics(registry));
        }
    }

    @Test
    void modelFailureAndDeadlineProduceCompleteSseErrorResponsesWithoutJsonOrBrokenChunking() throws Exception {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.setLogStartupInfo(false);
        try (var context = app.run("--spring.config.location=classpath:application.yml", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.main.banner-mode=off", "--spring.ai.openai.api-key=local-test-key",
                "--spring.ai.openai.base-url=http://127.0.0.1:1/v1")) {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            Fixture fixture = context.getBean(Fixture.class);
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

            fixture.upstream.set(Flux.concat(Flux.just("已生成的片段"),
                    Flux.<String>error(new AIServiceException.ConnectionException("模拟上游不可用"))
                            .delaySubscription(Duration.ofMillis(100))));
            String partialFailure = readErrorResponse(client, port);
            assertTrue(partialFailure.contains("event:data"));
            assertTrue(partialFailure.contains("已生成的片段"));

            fixture.upstream.set(Flux.error(new AIServiceException.RequestException("模拟输出达到上限")));
            readErrorResponse(client, port);

            fixture.properties.setSseTimeout(150);
            fixture.upstream.set(Flux.never());
            String timeout = readErrorResponse(client, port);
            assertTrue(timeout.contains("超时"));
            assertEquals(2, fixture.registry.get("ai_stream_requests_total").tag("outcome", "error").counter().count());
            assertEquals(1, fixture.registry.get("ai_stream_requests_total").tag("outcome", "timeout").counter().count());
            verifyNoInteractions(fixture.memory);
        }
    }

    private String readErrorResponse(HttpClient client, int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ai/chat/stream"))
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"hello\"}")).build();
        // ofString 完整读取至合法 HTTP EOF；缺少 chunked 结束标记时 JDK 客户端会抛 IOException。
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        String body = response.body();
        assertTrue(body.contains("event:start"));
        assertTrue(body.contains("event:error"));
        assertTrue(body.endsWith("\n\n"));
        assertFalse(body.contains("\"success\":false"), "已开始的 SSE 不能混入全局异常处理器 JSON");
        return body;
    }
}
