package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.domain.AgentToolCall;
import chat.liuxin.ai.agent.persistence.AgentToolCallMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolCallRecorder {

    private static final int MAX_JSON_LENGTH = 8000;

    private final AgentToolCallMapper agentToolCallMapper;
    private final ObjectMapper objectMapper;

    public <T> T record(Long taskId, String toolName, Object input, Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        try {
            T output = supplier.get();
            save(taskId, toolName, input, summarizeOutput(output), null, System.currentTimeMillis() - start);
            return output;
        } catch (RuntimeException e) {
            save(taskId, toolName, input, null, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    public void recordResult(Long taskId, String toolName, Object input, Object output, long durationMs) {
        save(taskId, toolName, input, summarizeOutput(output), null, durationMs);
    }

    public void recordFailure(Long taskId, String toolName, Object input, String errorMessage, long durationMs) {
        save(taskId, toolName, input, null, errorMessage, durationMs);
    }

    private void save(Long taskId, String toolName, Object input, Object output, String errorMessage, long durationMs) {
        try {
            AgentToolCall call = new AgentToolCall();
            call.setTaskId(taskId);
            call.setToolName(toolName);
            call.setSuccess(errorMessage == null ? 1 : 0);
            call.setInput(toJson(input));
            call.setOutput(output == null ? null : toJson(output));
            call.setErrorMessage(errorMessage);
            call.setDurationMs(durationMs);
            call.setCreatedAt(LocalDateTime.now());
            agentToolCallMapper.insert(call);
        } catch (Exception e) {
            log.warn("记录 Agent 工具调用失败: toolName={}, taskId={}", toolName, taskId, e);
        }
    }

    private Object summarizeOutput(Object output) {
        if (output instanceof java.util.Collection<?> collection) {
            return Map.of("type", "collection", "size", collection.size());
        }
        return output;
    }

    private String toJson(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > MAX_JSON_LENGTH ? json.substring(0, MAX_JSON_LENGTH) : json;
        } catch (Exception e) {
            return "{\"serializationError\":\"" + e.getClass().getSimpleName() + "\"}";
        }
    }
}
