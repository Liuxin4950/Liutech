package chat.liuxin.ai.service;

import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.common.mcp.RoleBasedToolRegistry;
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
import java.util.Map;
import java.util.Objects;

/**
 * 硅基流动 AI 客户端。
 *
 * 对外提供两个核心方法：
 * - chat       - 同步调用（CHAT 模式注册 BlogMcpTools，WRITING 模式注册 WritingTools 并内部流式收集）
 * - streamChat - 流式调用（CHAT 模式注册 BlogMcpTools，WRITING 模式注册 WritingTools）
 *
 * 写作模式通过 toolContext 把 FieldUpdateCollector 传给 WritingTools.applyArticleUpdate，
 * 让 AI 通过 function calling 直接操作编辑器字段，而非输出文本标记。
 *
 * 所有公共方法均带 Resilience4j 重试/熔断/限流注解。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowChatClient {

    /** 聊天模式：CHAT 使用看板娘工具，WRITING 使用写作工具 */
    public enum ChatMode { CHAT, WRITING }

    private final ChatClient chatClient;
    private final AiChatProperties aiChatProperties;
    private final RoleBasedToolRegistry roleBasedToolRegistry;

    // ==================== 核心方法 ====================

    /** CHAT 模式的便捷重载,等价于显式传 {@link ChatMode#CHAT}，无工具上下文。 */
    public String chat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, String role) {
        return chat(messages, modelName, temperature, maxTokens, ChatMode.CHAT, role, null);
    }

    /** 6 参重载：指定模式但不带工具上下文（兼容旧调用方）。 */
    public String chat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode, String role) {
        return chat(messages, modelName, temperature, maxTokens, mode, role, null);
    }

    /**
     * 同步调用底层 ChatClient 拿完整回复（带工具上下文）。
     *
     * CHAT 模式:直接 call(),注册 {@link BlogMcpTools} 供 AI 查文章数据。
     * WRITING 模式:内部走 stream() + collectList() 收集,避开 RestClient 对长响应的读超时;注册 {@link WritingTools}。
     * toolContext 在 WRITING 模式下携带 {@link FieldUpdateCollector}，供写工具收集字段更新。
     *
     * 带 Resilience4j 熔断/限流 + Spring Retry(最多 3 次,1s 退避)。
     * 熔断打开时走 {@link #fallbackChat} 返回降级文案。
     * 空响应会直接抛 AIServiceException,便于上层记录失败。
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChat")
    @RateLimiter(name = "aiService")
    public String chat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode, String role, Map<String, Object> toolContext) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        Object[] tools = resolveToolsByRole(role).toArray();
        try {
            log.debug("调用AI模型: {}, 模式: {}, 角色: {}, 消息数: {}", model, mode, role, safeMsgs.size());
            if (mode == ChatMode.WRITING) {
                // 写作模式：内部流式收集，避免 RestClient 超时
                String response = chatClient.prompt().messages(safeMsgs).options(options.mutate())
                        .tools(tools).toolContext(resolveToolContext(toolContext))
                        .stream().content()
                        .collectList().map(parts -> String.join("", parts)).block();
                return requireNonEmpty(response, model);
            } else {
                // 聊天模式：直接调用
                String response = chatClient.prompt().messages(safeMsgs).options(options.mutate())
                        .tools(tools).toolContext(resolveToolContext(toolContext))
                        .call().content();
                return requireNonEmpty(response, model);
            }
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用失败, 模型: {}, 模式: {}", model, mode, e);
            throw new AIServiceException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    /** CHAT 模式的便捷重载，无工具上下文。 */
    public Flux<String> streamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, String role) {
        return streamChat(messages, modelName, temperature, maxTokens, ChatMode.CHAT, role, null);
    }

    /** 6 参重载：指定模式但不带工具上下文（兼容旧调用方）。 */
    public Flux<String> streamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode, String role) {
        return streamChat(messages, modelName, temperature, maxTokens, mode, role, null);
    }

    /**
     * 流式调用,返回文本分片的 Flux 供上层订阅（带工具上下文）。
     *
     * 模式对应工具与 {@link #chat} 一致。toolContext 在 WRITING 模式下携带
     * {@link FieldUpdateCollector}，供 WritingTools.applyArticleUpdate 收集字段更新。
     * 熔断打开走 {@link #fallbackStreamChat}。
     * 注意重试注解在流订阅前的方法调用阶段生效;订阅后的流内异常需上层处理。
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackStreamChat")
    @RateLimiter(name = "aiService")
    public Flux<String> streamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode, String role, Map<String, Object> toolContext) {
        String model = resolveModel(modelName);
        List<Message> safeMsgs = safeMessages(messages);
        OpenAiChatOptions options = buildOptions(model, temperature, maxTokens);
        Object[] tools = resolveToolsByRole(role).toArray();
        try {
            log.debug("调用AI模型(流式): {}, 模式: {}, 角色: {}, 消息数: {}", model, mode, role, safeMsgs.size());
            return chatClient.prompt().messages(safeMsgs).options(options.mutate())
                    .tools(tools).toolContext(resolveToolContext(toolContext))
                    .stream().content();
        } catch (Exception e) {
            log.error("AI流式调用失败, 模型: {}, 模式: {}", model, mode, e);
            throw new AIServiceException("AI服务流式调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== Fallback ====================

    /**
     * 同步调用熔断兜底。Resilience4j 通过反射按签名匹配调用,末尾必须多一个 Exception 参数。
     * 返回固定的降级文案,让前端能显示"AI 繁忙"而不是抛 5xx。
     */
    public String fallbackChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode, String role, Map<String, Object> toolContext, Exception exception) {
        log.warn("AI服务熔断, 模型: {}, 模式: {}, 异常: {}", modelName, mode, exception.getMessage());
        return "抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage();
    }

    /** 流式调用熔断兜底,返回只发一条降级文案的 Flux。 */
    public Flux<String> fallbackStreamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens, ChatMode mode, String role, Map<String, Object> toolContext, Exception exception) {
        log.warn("AI服务流式熔断, 模型: {}, 模式: {}, 异常: {}", modelName, mode, exception.getMessage());
        return Flux.just("抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage());
    }

    // ==================== 内部方法 ====================

    /** null toolContext 兜底为空 Map，避免 ChatClient 抛 NPE。 */
    private Map<String, Object> resolveToolContext(Map<String, Object> toolContext) {
        return toolContext != null ? toolContext : Map.of();
    }

    /**
     * 按角色分派工具（防御纵深：与 SecurityConfig URL 层共同隔离 admin/user 工具）。
     * admin 角色可用 WritingTools，user/guest 仅 BlogMcpTools。
     */
    public List<Object> resolveToolsByRole(String role) {
        return roleBasedToolRegistry.getToolsForRole(role);
    }

    /** 请求未指定模型时回退到配置里的默认模型。 */
    private String resolveModel(String modelName) {
        return modelName != null ? modelName : aiChatProperties.getDefaultModel();
    }

    /** null 消息列表兜底为空列表,避免 Spring AI 抛 NPE。 */
    private List<Message> safeMessages(List<Message> messages) {
        return messages == null ? List.of() : messages;
    }

    /**
     * 构建 OpenAI 兼容的调用参数。temperature 仅在 [0,1] 内生效,maxTokens 仅正数生效,
     * 其余情况让底层使用模型自身默认值。
     */
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

    /** 校验模型回复非空,否则抛 AIServiceException 让重试或熔断介入。 */
    private String requireNonEmpty(String response, String model) {
        if (response == null || response.trim().isEmpty()) {
            throw new AIServiceException("AI返回空响应");
        }
        log.debug("AI响应成功, 模型: {}, 响应长度: {}", model, response.length());
        return response;
    }
}
