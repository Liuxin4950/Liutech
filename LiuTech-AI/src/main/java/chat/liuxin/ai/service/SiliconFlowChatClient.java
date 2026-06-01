package chat.liuxin.ai.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * 硅基流动 AI 客户端。
 *
 * <p>统一封装 Spring AI ChatClient 调用，对外提供四种语义：
 * <ul>
 *   <li>{@code chat}       — 看板娘聊天（注册 BlogMcpTools，同步调用）</li>
 *   <li>{@code chatForWriting} — 写作助手（注册 WritingTools，内部流式收集避免超时）</li>
 *   <li>{@code chatWithoutTools} — 纯文本生成（不注册工具）</li>
 *   <li>{@code stream*}    — 上述三种的流式变体</li>
 * </ul>
 *
 * <p>所有公共方法均带 Resilience4j 重试/熔断/限流注解。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowChatClient {

    private final ChatClient chatClient;
    private final BlogMcpTools blogMcpTools;
    private final WritingTools writingTools;

    @Value("${spring.ai.model.default:zai-org/GLM-4.6}")
    private String defaultModel;

    // ==================== 看板娘聊天（BlogMcpTools） ====================

    public String chat(List<Message> messages) {
        return chat(messages, null, null, null);
    }

    public String chat(List<Message> messages, String modelName) {
        return chat(messages, modelName, null, null);
    }

    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChatWithParams")
    @RateLimiter(name = "aiService")
    public String chat(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        try {
            log.debug("调用AI模型: {}, 消息数: {}", model, safeMsgs.size());
            String response = chatClient.prompt().messages(safeMsgs).options(options)
                    .tools(blogMcpTools).call().content();
            return requireNonEmpty(response, model);
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用失败, 模型: {}", model, e);
            throw new AIServiceException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 看板娘流式 ====================

    public Flux<String> streamChat(List<Message> messages, String modelName) {
        return streamChat(messages, modelName, null, null);
    }

    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackStreamChatWithParams")
    @RateLimiter(name = "aiService")
    public Flux<String> streamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        try {
            log.debug("调用AI模型(流式): {}, 消息数: {}", model, safeMsgs.size());
            return chatClient.prompt().messages(safeMsgs).options(options)
                    .tools(blogMcpTools).stream().content();
        } catch (Exception e) {
            log.error("AI流式调用失败, 模型: {}", model, e);
            throw new AIServiceException("AI服务流式调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 写作助手（WritingTools） ====================

    /**
     * 写作助手同步调用。内部使用流式收集（WebClient，无 read timeout），避免 RestClient 超时。
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChatWithParams")
    @RateLimiter(name = "aiService")
    public String chatForWriting(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        try {
            log.debug("调用AI模型(写作): {}, 消息数: {}", model, safeMsgs.size());
            String response = chatClient.prompt().messages(safeMsgs).options(options)
                    .tools(writingTools).stream().content()
                    .collectList().map(parts -> String.join("", parts)).block();
            return requireNonEmpty(response, model);
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI写作调用失败, 模型: {}", model, e);
            throw new AIServiceException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackStreamChatWithParams")
    @RateLimiter(name = "aiService")
    public Flux<String> streamChatForWriting(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        try {
            log.debug("调用AI模型(写作流式): {}, 消息数: {}", model, safeMsgs.size());
            return chatClient.prompt().messages(safeMsgs).options(options)
                    .tools(writingTools).stream().content();
        } catch (Exception e) {
            log.error("AI写作流式调用失败, 模型: {}", model, e);
            throw new AIServiceException("AI服务写作流式调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 无工具调用 ====================

    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChatWithParams")
    @RateLimiter(name = "aiService")
    public String chatWithoutTools(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        try {
            log.debug("调用AI模型(无工具): {}, 消息数: {}", model, safeMsgs.size());
            String response = chatClient.prompt().messages(safeMsgs).options(options)
                    .call().content();
            return requireNonEmpty(response, model);
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用失败(无工具), 模型: {}", model, e);
            throw new AIServiceException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== Fallback ====================

    public String fallbackChat(List<Message> messages, String modelName, Exception exception) {
        log.warn("AI服务熔断, 模型: {}, 异常: {}", modelName, exception.getMessage());
        return "抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage();
    }

    public Flux<String> fallbackStreamChat(List<Message> messages, String modelName, Exception exception) {
        log.warn("AI服务流式熔断, 模型: {}, 异常: {}", modelName, exception.getMessage());
        return Flux.just("抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage());
    }

    public String fallbackChatWithParams(List<Message> messages, String modelName, Double temperature, Integer maxTokens, Exception exception) {
        log.warn("AI服务熔断, 模型: {}, 异常: {}", modelName, exception.getMessage());
        return "抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage();
    }

    public Flux<String> fallbackStreamChatWithParams(List<Message> messages, String modelName, Double temperature, Integer maxTokens, Exception exception) {
        log.warn("AI服务流式熔断, 模型: {}, 异常: {}", modelName, exception.getMessage());
        return Flux.just("抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage());
    }

    // ==================== 内部方法 ====================

    private String resolveModel(String modelName) {
        return modelName != null ? modelName : defaultModel;
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
