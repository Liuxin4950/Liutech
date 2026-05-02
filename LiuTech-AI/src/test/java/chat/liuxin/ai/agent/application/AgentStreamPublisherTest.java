package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.response.AgentCompletePayload;
import chat.liuxin.ai.agent.response.AgentErrorPayload;
import chat.liuxin.ai.agent.response.AgentStartPayload;
import chat.liuxin.ai.agent.response.AgentToolEventPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AgentStreamPublisher.
 */
@ExtendWith(MockitoExtension.class)
class AgentStreamPublisherTest {

    private AgentStreamPublisher publisher;

    @Mock
    private SseEmitter emitter;

    @BeforeEach
    void setUp() {
        publisher = new AgentStreamPublisher();
    }

    @Test
    void shouldSendEnvelopeWithCorrectStructure() throws IOException {
        // Given
        String eventName = "agent-start";
        Long taskId = 123L;
        Long conversationId = 456L;
        var payload = AgentStartPayload.builder()
                .taskId(taskId)
                .conversationId(conversationId)
                .intent("SEARCH_ARTICLES")
                .role("guest")
                .capabilities(List.of("chat", "search"))
                .build();

        // When
        publisher.send(emitter, eventName, taskId, conversationId, payload);

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldNotSendWhenEmitterIsNull() {
        // When - should not throw
        assertDoesNotThrow(() -> publisher.send(null, "test", 1L, 1L, "payload"));
    }

    @Test
    void shouldSendErrorEvent() throws IOException {
        // When
        publisher.error(emitter, "AGENT_ERROR", "test error", "execute", 1L, 1L);

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldSendAgentStartEvent() throws IOException {
        // When
        publisher.sendAgentStart(emitter, 1L, 1L, "SEARCH", "guest", List.of("chat"));

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldSendToolStartEvent() throws IOException {
        // When
        publisher.sendToolStart(emitter, 1L, 1L, "public.search", "Search", "keyword=test");

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldSendToolResultSuccessEvent() throws IOException {
        // When
        publisher.sendToolResultSuccess(emitter, 1L, 1L, "public.search", "Search", 100L, "5 results");

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldSendToolResultFailureEvent() throws IOException {
        // When
        publisher.sendToolResultFailure(emitter, 1L, 1L, "public.search", "Search", 100L, "timeout");

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldSendDataEvent() throws IOException {
        // When
        publisher.sendData(emitter, 1L, 1L, "Hello world");

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldSendCompleteEvent() throws IOException {
        // When
        publisher.sendComplete(emitter, 1L, 1L);

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldHandleIOExceptionOnSend() throws IOException {
        // Given
        doThrow(new IOException("connection closed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // When - should handle gracefully
        assertDoesNotThrow(() -> publisher.send(emitter, "test", 1L, 1L, "payload"));
    }

    @Test
    void shouldSendAgentPlanEvent() throws IOException {
        // When
        publisher.sendAgentPlan(emitter, 1L, 1L, List.of(
                java.util.Map.of("step", 1, "description", "search")
        ));

        // Then
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }
}
