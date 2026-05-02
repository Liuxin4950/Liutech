package chat.liuxin.ai.agent.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentSseEnvelope DTO.
 */
class AgentSseEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void shouldCreateEnvelopeWithStaticFactory() {
        // Given
        String event = "article-results";
        Long taskId = 123L;
        Long conversationId = 456L;
        var payload = ArticleResultsPayload.builder()
                .source("search")
                .query("Spring")
                .reason("match")
                .items(java.util.List.of())
                .build();

        // When
        var envelope = AgentSseEnvelope.of(event, taskId, conversationId, payload);

        // Then
        assertEquals(1, envelope.getContractVersion());
        assertEquals(event, envelope.getEvent());
        assertEquals(taskId, envelope.getTaskId());
        assertEquals(conversationId, envelope.getConversationId());
        assertNotNull(envelope.getTimestamp());
        assertEquals(payload, envelope.getPayload());
    }

    @Test
    void shouldSetTimestampToCurrentTime() {
        // Given
        Instant before = Instant.now();

        // When
        var envelope = AgentSseEnvelope.of("data", 1L, 1L, "test content");

        // Then
        Instant after = Instant.now();
        assertNotNull(envelope.getTimestamp());
        assertTrue(envelope.getTimestamp().compareTo(before) >= 0);
        assertTrue(envelope.getTimestamp().compareTo(after) <= 0);
    }

    @Test
    void shouldSupportNullPayload() {
        // When
        var envelope = AgentSseEnvelope.of("complete", 1L, 1L, null);

        // Then
        assertNull(envelope.getPayload());
    }

    @Test
    void shouldBuildCompletePayload() {
        // Given
        var errorPayload = AgentErrorPayload.of("AGENT_ERROR", "test error", "execute");

        // When
        var envelope = AgentSseEnvelope.<AgentErrorPayload>builder()
                .event("error")
                .taskId(1L)
                .conversationId(1L)
                .timestamp(Instant.parse("2026-05-01T15:00:00.000Z"))
                .payload(errorPayload)
                .build();

        // Then
        assertEquals(1, envelope.getContractVersion());
        assertEquals("error", envelope.getEvent());
        assertEquals(errorPayload, envelope.getPayload());
    }

    @Test
    void shouldSerializeToJsonWithContractVersion() throws Exception {
        // Given
        var envelope = AgentSseEnvelope.of("data", 1L, 1L, DataPayload.of("hello"));

        // When
        String json = objectMapper.writeValueAsString(envelope);

        // Then
        assertTrue(json.contains("\"contractVersion\":1"), "JSON must contain contractVersion=1");
        assertTrue(json.contains("\"event\":\"data\""), "JSON must contain event name");
        assertTrue(json.contains("\"payload\""), "JSON must contain payload");
    }

    @Test
    void shouldDefaultContractVersionToOneViaBuilder() {
        // When - builder without explicit contractVersion
        var envelope = AgentSseEnvelope.builder()
                .event("test")
                .build();

        // Then
        assertEquals(1, envelope.getContractVersion());
    }
}
