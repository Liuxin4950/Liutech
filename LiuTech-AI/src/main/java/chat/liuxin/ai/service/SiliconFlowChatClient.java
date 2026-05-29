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

import java.util.Objects;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 硅基流动AI客户端
 * 
 * 主要职责：
 * 封装与硅基流动AI服务的交互，提供统一的聊天接口
 * 
 * 业务位置：
 * 位于AI服务层，是直接与AI模型交互的底层服务
 * 
 * 核心功能点：
 * 1. 提供统一的AI聊天接口，支持默认模型和指定模型
 * 2. 实现自动重试机制，提高服务稳定性
 * 3. 处理AI响应验证和异常转换
 * 4. 记录详细的调用日志，便于问题排查
 * 
 * 作者：刘鑫
 * 时间：2025-12-04
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

    /**
     * 使用默认模型进行聊天
     *
     * @param messages 消息列表，包含对话历史和当前用户输入
     * @return AI生成的回复内容
     */
    public String chat(List<Message> messages) {
        // 委托给带模型参数的方法，使用默认模型
        return chat(messages, null, null, null);
    }

    /**
     * 使用指定模型进行聊天（兼容旧方法）
     */
    public String chat(List<Message> messages, String modelName) {
        return chat(messages, modelName, null, null);
    }

    /**
     * 使用指定模型和参数进行聊天
     *
     * 业务流程：
     * 1. 参数验证和初始化
     * 2. 构建AI请求参数（支持temperature和maxTokens）
     * 3. 调用AI模型并获取响应
     * 4. 验证响应有效性
     * 5. 记录日志并返回结果
     *
     * @param messages 消息列表，包含对话历史和当前用户输入
     * @param modelName 指定的AI模型名称，为null时使用默认模型
     * @param temperature 温度参数，控制回复随机性（0.0-1.0），为null时使用默认值
     * @param maxTokens 最大生成token数，为null时不限制
     * @return AI生成的回复内容
     * @throws AIServiceException 当AI服务调用失败时抛出
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChatWithParams")
    @RateLimiter(name = "aiService")
    public String chat(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        // 1. 参数验证和初始化
        // 允许空消息列表，第一次对话时可能只有用户输入
        if (messages == null) {
            messages = List.of();
        }
        
        // 确定使用的模型，优先使用指定模型，否则使用默认模型
        String model = modelName != null ? modelName : defaultModel;

        try {
            // 2. 记录调用日志
            log.debug("调用AI模型: {}, 消息数量: {}, temperature: {}, maxTokens: {}",
                    model, messages.size(), temperature, maxTokens);

            // 3. 构建AI请求并调用
            // 使用Spring AI的ChatClient进行模型调用，支持动态参数
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(model);

            // 应用温度参数（如果有）
            if (temperature != null && temperature >= 0.0 && temperature <= 1.0) {
                optionsBuilder.temperature(temperature);
            }

            // 应用最大token参数（如果有）
            if (maxTokens != null && maxTokens > 0) {
                optionsBuilder.maxTokens(maxTokens);
            }

            OpenAiChatOptions options = optionsBuilder.build();
            OpenAiChatOptions safeOptions = Objects.requireNonNullElse(options, OpenAiChatOptions.builder().build());
            @SuppressWarnings("null")
            String response = chatClient
                    .prompt()
                    .messages(messages)
                    .options(safeOptions)
                    .tools(blogMcpTools)
                    .call()
                    .content();
            
            // 4. 验证响应有效性
            if (response == null || response.trim().isEmpty()) {
                throw new AIServiceException("AI返回空响应");
            }
            
            // 5. 记录成功日志并返回结果
            log.debug("AI响应成功, 模型: {}, 响应长度: {}", model, response.length());
            return response;
            
        } catch (Exception e) {
            // 异常处理：记录错误日志并转换为AIServiceException
            log.error("AI调用失败, 模型: {}, 错误: {}", model, e.getMessage(), e);
            throw new AIServiceException("AI服务调用失败: " + e.getMessage(), e);
        }
    }


    /**
     * 纯文本生成，不注册任何 MCP 工具。
     *
     * 与 chat() 的区别：chat() 注册了 BlogChatTools，模型可以自主调用搜索工具；
     * chatWithoutTools() 完全不注册工具，模型只做纯文本生成。
     *
     * 使用场景：AgentOrchestrator 在 CHAT 意图下使用，避免双重搜索问题——
     * Handler 已经执行了确定性工具调用，不应再把搜索工具交给模型自主调用。
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChatWithParams")
    @RateLimiter(name = "aiService")
    public String chatWithoutTools(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        if (messages == null) {
            messages = List.of();
        }
        String model = modelName != null ? modelName : defaultModel;
        try {
            log.debug("调用AI模型(无工具): {}, 消息数量: {}, temperature: {}, maxTokens: {}",
                    model, messages.size(), temperature, maxTokens);
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(model);
            if (temperature != null && temperature >= 0.0 && temperature <= 1.0) {
                optionsBuilder.temperature(temperature);
            }
            if (maxTokens != null && maxTokens > 0) {
                optionsBuilder.maxTokens(maxTokens);
            }
            OpenAiChatOptions options = optionsBuilder.build();
            OpenAiChatOptions safeOptions = Objects.requireNonNullElse(options, OpenAiChatOptions.builder().build());
            @SuppressWarnings("null")
            String response = chatClient
                    .prompt()
                    .messages(messages)
                    .options(safeOptions)
                    // 不注册任何工具，避免双重搜索
                    .call()
                    .content();
            if (response == null || response.trim().isEmpty()) {
                throw new AIServiceException("AI返回空响应");
            }
            log.debug("AI响应成功(无工具), 模型: {}, 响应长度: {}", model, response.length());
            return response;
        } catch (Exception e) {
            log.error("AI调用失败(无工具), 模型: {}, 错误: {}", model, e.getMessage(), e);
            throw new AIServiceException("AI服务调用失败: " + e.getMessage(), e);
        }
    }
    /**
     * 写作专用文本生成，不注册 MCP 工具。
     * 内部使用流式调用（WebClient，无 read timeout），避免 RestClient 固定超时。
     * 分类和标签匹配由 Java 代码处理，模型只需生成 HTML 内容。
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackChatWithParams")
    @RateLimiter(name = "aiService")
    public String chatForWriting(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        if (messages == null) {
            messages = List.of();
        }
        String model = modelName != null ? modelName : defaultModel;
        try {
            log.debug("调用AI模型(写作专用): {}, 消息数量: {}, temperature: {}, maxTokens: {}",
                    model, messages.size(), temperature, maxTokens);
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(model);
            if (temperature != null && temperature >= 0.0 && temperature <= 1.0) {
                optionsBuilder.temperature(temperature);
            }
            if (maxTokens != null && maxTokens > 0) {
                optionsBuilder.maxTokens(maxTokens);
            }
            OpenAiChatOptions options = optionsBuilder.build();
            OpenAiChatOptions safeOptions = Objects.requireNonNullElse(options, OpenAiChatOptions.builder().build());
            @SuppressWarnings("null")
            String response = chatClient
                    .prompt()
                    .messages(messages)
                    .options(safeOptions)
                    .tools(writingTools)
                    .stream()
                    .content()
                    .collectList()
                    .map(parts -> String.join("", parts))
                    .block();
            if (response == null || response.trim().isEmpty()) {
                throw new AIServiceException("AI返回空响应");
            }
            log.debug("AI写作专用响应成功, 模型: {}, 响应长度: {}", model, response.length());
            return response;
        } catch (Exception e) {
            log.error("AI写作专用调用失败, 模型: {}, 错误: {}", model, e.getMessage(), e);
            throw new AIServiceException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用指定模型进行流式聊天（兼容旧方法）
     */
    public Flux<String> streamChat(List<Message> messages, String modelName) {
        return streamChat(messages, modelName, null, null);
    }

    /**
     * 使用指定模型和参数进行流式聊天
     *
     * 业务流程：
     * 1. 参数验证和初始化
     * 2. 构建AI请求参数（支持temperature和maxTokens）
     * 3. 调用AI模型并获取流式响应
     * 4. 返回Flux<String>供调用方处理
     * 5. 异常处理和日志记录
     *
     * @param messages 消息列表，包含对话历史和当前用户输入
     * @param modelName 指定的AI模型名称，为null时使用默认模型
     * @param temperature 温度参数，控制回复随机性（0.0-1.0），为null时使用默认值
     * @param maxTokens 最大生成token数，为null时不限制
     * @return Flux<String> 流式响应的字符串序列
     * @throws AIServiceException 当AI服务调用失败时抛出
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackStreamChatWithParams")
    @RateLimiter(name = "aiService")
    public Flux<String> streamChat(List<Message> messages, String modelName, Double temperature, Integer maxTokens) {
        // 1. 参数验证和初始化
        // 允许空消息列表，第一次对话时可能只有用户输入
        if (messages == null) {
            messages = List.of();
        }
        
        // 确定使用的模型，优先使用指定模型，否则使用默认模型
        String model = modelName != null ? modelName : defaultModel;

        try {
            // 2. 记录调用日志
            log.debug("调用AI模型(流式): {}, 消息数量: {}, temperature: {}, maxTokens: {}",
                    model, messages.size(), temperature, maxTokens);

            // 3. 构建AI请求并返回流式响应
            // 使用Spring AI的ChatClient进行流式模型调用，支持动态参数
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(model);

            // 应用温度参数（如果有）
            if (temperature != null && temperature >= 0.0 && temperature <= 1.0) {
                optionsBuilder.temperature(temperature);
            }

            // 应用最大token参数（如果有）
            if (maxTokens != null && maxTokens > 0) {
                optionsBuilder.maxTokens(maxTokens);
            }

            OpenAiChatOptions streamOptions = optionsBuilder.build();
            OpenAiChatOptions safeStreamOptions = Objects.requireNonNullElse(streamOptions, OpenAiChatOptions.builder().build());
            @SuppressWarnings("null")
            Flux<String> responseFlux = chatClient
                    .prompt()
                    .messages(messages)
                    .options(safeStreamOptions)
                    .tools(blogMcpTools)
                    .stream()
                    .content();
            
            // 4. 记录成功日志并返回响应流
            log.debug("AI流式响应成功, 模型: {}", model);
            return responseFlux;
            
        } catch (Exception e) {
            // 异常处理：记录错误日志并转换为AIServiceException
            log.error("AI流式调用失败, 模型: {}, 错误: {}", model, e.getMessage(), e);
            throw new AIServiceException("AI服务流式调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 熔断器fallback方法 - 普通聊天
     * 当AI服务熔断时返回友好的错误提示
     *
     * @param messages 消息列表
     * @param modelName 模型名称
     * @param exception 原始异常
     * @return 友好提示消息
     */
    public String fallbackChat(List<Message> messages, String modelName, Exception exception) {
        log.warn("AI服务熔断，使用fallback响应, 模型: {}, 异常: {}", modelName, exception.getMessage());
        return "抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage();
    }

    /**
     * 熔断器fallback方法 - 流式聊天
     * 当AI服务熔断时返回友好的错误提示
     *
     * @param messages 消息列表
     * @param modelName 模型名称
     * @param exception 原始异常
     * @return 友好提示消息的Flux
     */
    public Flux<String> fallbackStreamChat(List<Message> messages, String modelName, Exception exception) {
        log.warn("AI服务流式熔断，使用fallback响应, 模型: {}, 异常: {}", modelName, exception.getMessage());
        return Flux.just("抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage());
    }

    /**
     * 熔断器fallback方法 - 普通聊天（带参数）
     * 当AI服务熔断时返回友好的错误提示
     */
    public String fallbackChatWithParams(List<Message> messages, String modelName, Double temperature, Integer maxTokens, Exception exception) {
        log.warn("AI服务熔断，使用fallback响应, 模型: {}, temperature: {}, maxTokens: {}, 异常: {}",
                modelName, temperature, maxTokens, exception.getMessage());
        return "抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage();
    }

    /**
     * 熔断器fallback方法 - 流式聊天（带参数）
     * 当AI服务熔断时返回友好的错误提示
     */
    public Flux<String> fallbackStreamChatWithParams(List<Message> messages, String modelName, Double temperature, Integer maxTokens, Exception exception) {
        log.warn("AI服务流式熔断，使用fallback响应, 模型: {}, temperature: {}, maxTokens: {}, 异常: {}",
                modelName, temperature, maxTokens, exception.getMessage());
        return Flux.just("抱歉，AI服务当前繁忙，请稍后重试。错误信息: " + exception.getMessage());
    }
}
