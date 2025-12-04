package chat.liuxin.ai.service.impl;

/**
 * AI聊天服务实现
 * 
 * 主要职责：
 * 提供AI聊天功能的核心实现，处理用户输入并生成AI回复
 * 
 * 业务位置：
 * 位于AI服务层，是用户请求与AI模型交互的核心桥梁
 * 
 * 核心功能点：
 * 1. 处理普通聊天请求，一次性返回完整AI回复
 * 2. 管理会话上下文，维护历史消息记录
 * 3. 构建AI模型请求的消息序列
 * 4. 异常处理和错误恢复机制
 * 5. 性能监控和日志记录
 * 
 * 作者：刘鑫
 * 时间：2025-12-01
 */

import chat.liuxin.ai.exception.AIServiceException;
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.MemoryService;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    
    @Value("${spring.ai.chat.history-limit:19}")
    private int historyLimit; // 历史条数（不含本轮输入）

    /**
     * 处理普通聊天请求，一次性返回完整AI回复
     * 
     * 业务流程：
     * 1. 初始化请求参数和性能监控
     * 2. 加载会话历史上下文
     * 3. 构建AI模型请求的消息序列
     * 4. 处理会话管理（新建或复用）
     * 5. 调用AI模型生成回复
     * 6. 保存交互记录并返回结果
     * 
     * @param request 聊天请求对象，包含用户输入和会话信息
     * @param userId 用户ID，用于会话关联
     * @return 聊天响应对象，包含AI回复和元数据
     */
    @Override
    public ChatResponse processChat(ChatRequest request, Long userId) {
        // 1. 初始化请求参数和性能监控
        long begin = System.currentTimeMillis();
        String userIdStr = userId != null ? userId.toString() : "0";
        String modelName = request.getModel() != null ? request.getModel() : "THUDM/GLM-Z1-9B-0414";
        Long conversationId = request.getConversationId();
        
        try {
            // 2. 提取用户输入
            String input = request.getMessage();

            // 3. 加载会话历史上下文（限制条数以控制token消耗）
            List<Message> historyMessages = conversationId != null
                    ? memoryService.listLastMessagesAsPromptMessages(conversationId, historyLimit)
                    : Collections.emptyList();

            // 4. 构建AI模型请求的消息序列
            List<Message> messages = new ArrayList<>();
            // 注意：系统提示词由 ChatClient 的 defaultSystem 提供，这里不再重复注入
            messages.addAll(historyMessages);
            // 将用户当前输入添加到消息列表末尾，作为最新一条用户消息
            messages.add(new UserMessage(input));

            // 5. 处理会话管理（新建或复用）
            if (conversationId == null) {
                String title = "新会话";
                conversationId = memoryService.createConversation(userIdStr, title);
            }

            // 6. 异步保存用户消息（在调用AI前保存，确保数据不丢失）
            memoryService.saveUserMessage(userIdStr, conversationId, input, modelName, null);
            
            // 7. 调用AI模型生成回复
            String aiOutput = siliconFlowChatClient.chat(messages, modelName);
            System.out.println("AI回复：\n" + (aiOutput == null ? "" : aiOutput) + '\n');

            // 8. 保存AI回复记录
            memoryService.saveAssistantMessage(userIdStr, conversationId, aiOutput, modelName, 1, null);

            // 9. 计算处理耗时并记录日志
            long cost = System.currentTimeMillis() - begin;
            log.debug("AI普通聊天成功，模型:{}，输入长度:{}，输出长度:{}，耗时:{}ms", modelName, input.length(),
                    aiOutput != null ? aiOutput.length() : 0, cost);

            // 10. 构建并返回响应对象
            return ChatResponse.builder()
                    .success(true)
                    .message(aiOutput)
                    .emotion(null)
                    .action(null)
                    .model(modelName)
                    .processingTime(cost)
                    .responseLength(aiOutput != null ? aiOutput.length() : 0)
                    .conversationId(conversationId)
                    .build();

        } catch (AIServiceException e) {
            // AI服务特定异常，直接抛出让全局异常处理器处理
            log.error("AI服务异常: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            // 通用异常处理：记录错误日志并尝试保存错误状态
            log.error("AI普通聊天失败", e);
            try {
                // 尝试保存错误状态到数据库，便于问题追踪
                memoryService.saveAssistantMessage(userIdStr, conversationId, null, modelName, 9, null);
            } catch (Exception ignore) {
                log.warn("记录AI错误消息失败: {}", ignore.getMessage());
            }

            // 根据异常类型抛出相应的AI服务异常，便于上层处理
            if (e.getCause() instanceof java.net.ConnectException ||
                    e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new AIServiceException.ConnectionException("AI服务连接失败: " + e.getMessage());
            } else if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new AIServiceException.TimeoutException("AI服务响应超时: " + e.getMessage());
            } else {
                throw new AIServiceException("AI服务处理异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理流式聊天请求，通过SSE推送AI回复流
     * 
     * 业务流程：
     * 1. 创建SseEmitter并设置超时和完成/错误处理
     * 2. 异步处理聊天请求，避免阻塞主线程
     * 3. 发送开始事件，标识流开始
     * 4. 逐块发送AI响应数据
     * 5. 发送完成事件，标识流结束
     * 6. 在完成或错误时保存完整消息到数据库
     * 7. 设置请求属性标记流已完成，避免Security权限检查
     * 
     * @param request 聊天请求对象，包含用户输入和会话信息
     * @param userId 用户ID，用于会话关联
     * @return SseEmitter对象，用于推送流式响应
     */
    @Override
    public SseEmitter processStreamChat(ChatRequest request, Long userId) {
        // 1. 初始化请求参数
        String userIdStr = userId != null ? userId.toString() : "0";
        String modelName = request.getModel() != null ? request.getModel() : "THUDM/GLM-Z1-9B-0414";
        Long conversationId = request.getConversationId();
        String input = request.getMessage();
        
        // 2. 创建SseEmitter，设置超时时间为2分钟
        SseEmitter emitter = new SseEmitter(120000L);
        
        // 3. 设置完成和错误处理
        emitter.onCompletion(() -> {
            log.debug("流式聊天完成，用户ID: {}, 会话ID: {}", userIdStr, conversationId);
        });
        emitter.onTimeout(() -> {
            log.warn("流式聊天超时，用户ID: {}, 会话ID: {}", userIdStr, conversationId);
            emitter.complete();
        });
        
        // 4. 异步处理聊天请求
        CompletableFuture.runAsync(() -> {
            // 处理会话管理（新建或复用）
            Long currentConversationId = conversationId;
            if (currentConversationId == null) {
                String title = "新会话";
                currentConversationId = memoryService.createConversation(userIdStr, title);
            }
            final Long finalConversationId = currentConversationId;
            
            try {
                // 4.1 加载会话历史上下文
                List<Message> historyMessages = conversationId != null
                        ? memoryService.listLastMessagesAsPromptMessages(conversationId, historyLimit)
                        : Collections.emptyList();
                
                // 4.2 构建AI模型请求的消息序列
                List<Message> messages = new ArrayList<>();
                messages.addAll(historyMessages);
                messages.add(new UserMessage(input));
                
                // 4.4 异步保存用户消息
                memoryService.saveUserMessage(userIdStr, conversationId, input, modelName, null);
                
                // 4.5 发送开始事件
                sendSseEvent(emitter, "start", Map.of(
                    "conversationId", finalConversationId,
                    "model", modelName
                ));
                
                // 4.6 用于收集完整响应的容器
                AtomicReference<StringBuilder> fullResponseRef = new AtomicReference<>(new StringBuilder());
                
                // 4.7 调用流式AI接口
                Flux<String> responseFlux = siliconFlowChatClient.streamChat(messages, modelName);
                
                // 4.8 订阅流式响应并处理每个数据块
                responseFlux.subscribe(
                    chunk -> {
                        try {
                            // 收集完整响应
                            fullResponseRef.get().append(chunk);
                            
                            // 发送数据块
                            sendSseEvent(emitter, "data", Map.of(
                                "content", chunk,
                                "conversationId", finalConversationId
                            ));
                        } catch (IOException e) {
                            log.error("发送SSE事件失败", e);
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        // 处理错误
                        log.error("流式响应错误，用户ID: {}, 会话ID: {}", userIdStr, finalConversationId, error);
                        try {
                            memoryService.saveAssistantMessage(userIdStr, finalConversationId, null, modelName, 9, null);
                            sendSseEvent(emitter, "error", Map.of(
                                "conversationId", finalConversationId,
                                "error", error.getMessage()
                            ));
                        } catch (Exception ex) {
                            log.error("发送错误事件失败", ex);
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        // 处理完成
                        try {
                            // 获取完整响应
                            String fullResponse = fullResponseRef.get().toString();
                            
                            // 保存AI回复记录
                            memoryService.saveAssistantMessage(userIdStr, finalConversationId, fullResponse, modelName, 1, null);
                            
                            // 发送完成事件
                            sendSseEvent(emitter, "complete", Map.of(
                                "conversationId", finalConversationId,
                                "responseLength", fullResponse.length()
                            ));
                            
                            // 完成流
                            emitter.complete();
                        } catch (Exception e) {
                            log.error("完成流式响应时发生错误", e);
                            emitter.completeWithError(e);
                        }
                    }
                );
                
            } catch (Exception e) {
                log.error("流式聊天处理失败，用户ID: {}, 会话ID: {}", userIdStr, finalConversationId, e);
                // 在异常情况下，使用已创建的会话ID或创建新的异常会话
                Long errorConversationId = finalConversationId;
                if (errorConversationId == null) {
                    try {
                        errorConversationId = memoryService.createConversation(userIdStr, "异常会话");
                    } catch (Exception ex) {
                        log.error("创建异常会话失败", ex);
                        errorConversationId = 0L; // 使用默认值
                    }
                }
                final Long finalErrorConversationId = errorConversationId;
                
                try {
                    // 尝试保存错误状态
                    memoryService.saveAssistantMessage(userIdStr, finalErrorConversationId, null, modelName, 9, null);
                    
                    // 发送错误事件
                    sendSseEvent(emitter, "error", Map.of(
                        "conversationId", finalErrorConversationId,
                        "error", e.getMessage()
                    ));
                } catch (Exception ex) {
                    log.error("发送错误事件失败", ex);
                }
                
                // 完成流并传递错误
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
    
    /**
     * 发送SSE事件
     * 
     * @param emitter SseEmitter对象
     * @param event 事件类型（start, data, complete, error）
     * @param data 事件数据
     * @throws IOException 当发送失败时抛出
     */
    private void sendSseEvent(SseEmitter emitter, String event, Map<String, Object> data) throws IOException {
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                .name(event)
                .data(data);
        
        emitter.send(eventBuilder);
        log.debug("发送SSE事件: {}, 数据: {}", event, data);
    }
}
