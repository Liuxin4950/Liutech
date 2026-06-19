package chat.liuxin.ai.service;

import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.common.mcp.BlogMcpTools;
import chat.liuxin.ai.common.mcp.WritingTools;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * 硅基流动 AI 客户端。
 *
 * <p>对外提供两个核心方法：
 * <ul>
 *   <li>{@code chat}       — 同步调用（CHAT 模式注册 BlogMcpTools，WRITING 模式注册 WritingTools 并内部流式收集）</li>
 *   <li>{@code streamChat} — 流式调用（CHAT 模式注册 BlogMcpTools，WRITING 模式注册 WritingTools）</li>
 * </ul>
 *
 * <p>所有公共方法均带 Resilience4j 重试/熔断/限流注解。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowChatClient {

    /** 聊天模式：CHAT 使用看板娘工具，WRITING 使用写作工具 */
    public enum ChatMode { CHAT, WRITING }

    private final ChatClient chatClient;
    private final BlogMcpTools blogMcpTools;
    private final WritingTools writingTools;
    private final AiChatProperties aiChatProperties;

    // ==================== 核心方法 ====================

    /** 同步调用（默认 CHAT 模式） */
    public String chat(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        return chat(messages, modelName, temperature, maxTokens, ChatMode.CHAT);
    }

    /**
     * 同步调用。
     *
     * <p>CHAT 模式：直接调用，注册 BlogMcpTools。
     * <p>WRITING 模式：内部流式收集（避免 RestClient 超时），注册 WritingTools。
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChat")
    @RateLimiter(name = "aiService")
    public String chat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        Object tools = resolveTools(mode);
        try {
            log.debug("调用AI模型: {}, 模式: {}, 消息数: {}", model, mode, safeMsgs.size());
            if (mode == ChatMode.WRITING) {
                // 写作模式：内部流式收集，避免 RestClient 超时
                String response = chatClient.prompt().messages(safeMsgs).options(options)
                        .tools(tools).stream().content()
                        .collectList().map(parts -> String.join("", parts)).block();
                return requireNonEmpty(response, model);
            } else {
                // 聊天模式：直接调用
                String response = chatClient.prompt().messages(safeMsgs).options(options)
                        .tools(tools).call().content();
                return requireNonEmpty(response, model);
            }
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用失败, 模型: {}, 模式: {}", model, mode, e);
            throw new AIServiceException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    /** 流式调用（默认 CHAT 模式） */
    public Flux<String> streamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        return streamChat(messages, modelName, temperature, maxTokens, ChatMode.CHAT);
    }

    /**
     * 流式调用。
     *
     * <p>CHAT 模式注册 BlogMcpTools，WRITING 模式注册 WritingTools。
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackStreamChat")
    @RateLimiter(name = "aiService")
    public Flux<String> streamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        Object tools = resolveTools(mode);
        try {
            log.debug("调用AI模型(流式): {}, 模式: {}, 消息数: {}", model, mode, safeMsgs.size());
            return chatClient.prompt().messages(safeMsgs).options(options)
                    .tools(tools).stream().content();
        } catch (Exception e) {
            log.error("AI流式调用失败, 模型: {}, 模式: {}", model, mode, e);
            throw new AIServiceException("AI服务流式调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== Fallback ====================

    public String fallbackChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode, Exception exception) {
        log.warn("AI服务熔断, 模型: {}, 模式: {}, 异常: {}", modelName, mode, exception.getMessage());
        return "抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage();
    }

    public Flux<String> fallbackStreamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode, Exception exception) {
        log.warn("AI服务流式熔断, 模型: {}, 模式: {}, 异常: {}", modelName, mode, exception.getMessage());
        return Flux.just("抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage());
    }

    // ==================== 内部方法 ====================

    private Object resolveTools(ChatMode mode) {
        return mode == ChatMode.WRITING ? writingTools : blogMcpTools;
    }

    private String resolveModel(String modelName) {
        return modelName != null ? modelName : aiChatProperties.getDefaultModel();
    }

    private List<Message> safeMessages(List<Message> messages) {
        return messages == null ? List.of() : messages;
    }

    private OpenAiChatOptions buildOptions(String model, Double temperature, Integer maxTokens) {
        var builder = OpenAiChatOptions.builder().model(model);
        if (temperature != null && temperature >= 0.0 && temperature <= 1.0) {
            builder.temperature(temperature);
        }
        if (maxTokens != null && maxTokens > 0) {
            builder.maxTokens(maxTokens);
        }
        return Objects.requireNonNullElse(builder.build(), OpenAiChatOptions.builder().build());
    }

    private String requireNonEmpty(String response, String model) {
        if (response == null || response.trim().isEmpty()) {
            throw new AIServiceException("AI返回空响应");
        }
        log.debug("AI响应成功, 模型: {}, 响应长度: {}", model, response.length());
        return response;
    }
}
