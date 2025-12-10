package chat.liuxin.ai.service;

import chat.liuxin.ai.exception.AIServiceException;
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

    /**
     * 使用默认模型进行聊天
     * 
     * @param messages 消息列表，包含对话历史和当前用户输入
     * @return AI生成的回复内容
     */
    public String chat(List<Message> messages) {
        // 委托给带模型参数的方法，使用默认模型
        return chat(messages, null);
    }
    
    /**
     * 使用指定模型进行聊天
     * 
     * 业务流程：
     * 1. 参数验证和初始化
     * 2. 构建AI请求参数
     * 3. 调用AI模型并获取响应
     * 4. 验证响应有效性
     * 5. 记录日志并返回结果
     * 
     * @param messages 消息列表，包含对话历史和当前用户输入
     * @param modelName 指定的AI模型名称，为null时使用默认模型
     * @return AI生成的回复内容
     * @throws AIServiceException 当AI服务调用失败时抛出
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String chat(List<Message> messages, String modelName) {
        // 1. 参数验证和初始化
        // 允许空消息列表，第一次对话时可能只有用户输入
        if (messages == null) {
            messages = List.of();
        }
        
        // 确定使用的模型，优先使用指定模型，否则使用默认模型
        String model = modelName != null ? modelName : "THUDM/GLM-Z1-9B-0414";
        
        try {
            // 2. 记录调用日志
            log.debug("调用AI模型: {}, 消息数量: {}", model, messages.size());
            
            // 3. 构建AI请求并调用
            // 注意：使用Spring AI的ChatClient进行模型调用，自动处理OpenAI兼容接口
            String response = chatClient
                    .prompt()
                    .messages(messages)
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(0.2)  // 设置较低的温度以获得更稳定的回复
                            .build())
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
     * 使用指定模型进行流式聊天
     * 
     * 业务流程：
     * 1. 参数验证和初始化
     * 2. 构建AI请求参数
     * 3. 调用AI模型并获取流式响应
     * 4. 返回Flux<String>供调用方处理
     * 5. 异常处理和日志记录
     * 
     * @param messages 消息列表，包含对话历史和当前用户输入
     * @param modelName 指定的AI模型名称，为null时使用默认模型
     * @return Flux<String> 流式响应的字符串序列
     * @throws AIServiceException 当AI服务调用失败时抛出
     */
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Flux<String> streamChat(List<Message> messages, String modelName) {
        // 1. 参数验证和初始化
        // 允许空消息列表，第一次对话时可能只有用户输入
        if (messages == null) {
            messages = List.of();
        }
        
        // 确定使用的模型，优先使用指定模型，否则使用默认模型
        String model = modelName != null ? modelName : "THUDM/GLM-Z1-9B-0414";
        
        try {
            // 2. 记录调用日志
            log.debug("调用AI模型(流式): {}, 消息数量: {}", model, messages.size());
            
            // 3. 构建AI请求并返回流式响应
            // 注意：使用Spring AI的ChatClient进行流式模型调用，自动处理OpenAI兼容接口
            // 流式响应通过调用.stream()方法启用，不需要在OpenAiChatOptions中设置stream选项
            // 直接返回Flux<String>，让调用方处理响应流
            Flux<String> responseFlux = chatClient
                    .prompt()
                    .messages(messages)
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(0.2)  // 设置较低的温度以获得更稳定的回复
                            .build())
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
}
