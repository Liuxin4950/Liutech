package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.entity.ChatMessage;
import chat.liuxin.ai.entity.ChatSession;
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.*;
import chat.liuxin.ai.resp.SessionListResponse.SessionInfo;
import chat.liuxin.ai.resp.SessionHistoryResponse.MessageInfo;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.ChatMessageService;
import chat.liuxin.ai.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * AI聊天核心服务实现类
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final OllamaChatModel chatModel;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    @Override
    public chat.liuxin.ai.resp.ChatResponse processChat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 使用请求中的用户ID
            String userId = request.getUserId();
            Long userIdLong = parseUserId(userId);
            String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
            
            log.info("收到AI聊天请求 [用户:{}] [会话:{}]: {}", userId, sessionId, request.getMessage());
            
            // 创建或获取会话
            String sessionTitle = generateSessionTitle(request.getMessage());
            ChatSession session = chatSessionService.createOrGetSession(userIdLong, sessionId, sessionTitle);
            
            // 获取历史消息用于上下文
            List<Message> historyMessages = chatMessageService.getRecentMessagesAsMessages(userIdLong, sessionId, 10);
            
            // 保存用户消息
            String modelName = request.getModel() != null ? request.getModel() : "ollama";
            chatMessageService.saveUserMessage(userIdLong, sessionId, request.getMessage(), modelName);
            
            // 重新获取包含新消息的历史记录
            List<Message> allMessages = chatMessageService.getRecentMessagesAsMessages(userIdLong, sessionId, 10);
            
            // 调用AI模型
            ChatResponse response = chatModel.call(new Prompt(allMessages));
            String aiResponse = response.getResult().getOutput().getContent();
            
            // 保存AI回复
            chatMessageService.saveAssistantMessage(userIdLong, sessionId, aiResponse, null, modelName);
            
            // 更新会话消息数量
            Integer messageCount = chatMessageService.getMessageCount(userIdLong, sessionId);
            chatSessionService.updateMessageCount(userIdLong, sessionId, messageCount);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("AI响应完成 [用户:{}] [会话:{}] 耗时:{}ms", userId, sessionId, processingTime);
            
            return chat.liuxin.ai.resp.ChatResponse.builder()
                    .success(true)
                    .message(aiResponse)
                    .userId(request.getUserId())
                    .model(modelName)
                    .historyCount(historyMessages.size())
                    .timestamp(System.currentTimeMillis())
                    .processingTime(processingTime)
                    .responseLength(aiResponse.length())
                    .build();
            
        } catch (Exception e) {
            log.error("AI聊天处理异常 [用户:{}]", request.getUserId(), e);
            return chat.liuxin.ai.resp.ChatResponse.error(
                "AI服务暂时不可用: " + e.getMessage(), 
                request.getUserId()
            );
        }
    }

    @Override
    public SseEmitter processChatStream(ChatRequest request) {
        // 创建SSE发射器，设置超时时间为30秒
        SseEmitter emitter = new SseEmitter(30000L);
        
        try {
            // 使用请求中的用户ID
            String userId = request.getUserId();
            Long userIdLong = parseUserId(userId);
            String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
            
            log.info("收到AI流式聊天请求 [用户:{}] [会话:{}]: {}", userId, sessionId, request.getMessage());
            
            // 发送用户ID给客户端
            emitter.send(SseEmitter.event()
                .name("user")
                .data(Map.of("userId", request.getUserId())));
            
            // 异步处理AI响应
            CompletableFuture.runAsync(() -> {
                try {
                    // 创建或获取会话
                    String sessionTitle = generateSessionTitle(request.getMessage());
                    ChatSession session = chatSessionService.createOrGetSession(userIdLong, sessionId, sessionTitle);
                    
                    // 获取历史消息用于上下文
                    List<Message> historyMessages = chatMessageService.getRecentMessagesAsMessages(userIdLong, sessionId, 10);
                    
                    // 保存用户消息
                    String modelName = request.getModel() != null ? request.getModel() : "ollama";
                    chatMessageService.saveUserMessage(userIdLong, sessionId, request.getMessage(), modelName);
                    
                    // 重新获取包含新消息的历史记录
                    List<Message> allMessages = chatMessageService.getRecentMessagesAsMessages(userIdLong, sessionId, 10);
                    
                    // 发送开始事件
                    emitter.send(SseEmitter.event()
                        .name("start")
                        .data(Map.of("message", "AI正在思考中...", "historyCount", historyMessages.size())));
                    
                    // 调用Ollama模型获取流式响应
                    Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(allMessages));
                    
                    StringBuilder fullResponse = new StringBuilder();
                    
                    // 处理流式响应
                    responseFlux.subscribe(
                        chatResponse -> {
                            try {
                                String chunk = chatResponse.getResult().getOutput().getContent();
                                if (chunk != null && !chunk.isEmpty()) {
                                    fullResponse.append(chunk);
                                    
                                    // 发送数据块
                                    emitter.send(SseEmitter.event()
                                        .name("data")
                                        .data(Map.of("chunk", chunk, "userId", request.getUserId())));
                                }
                            } catch (IOException e) {
                                log.error("发送流式数据失败", e);
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("AI流式响应异常", error);
                            try {
                                emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(Map.of("success", false, "message", "AI服务异常: " + error.getMessage())));
                            } catch (IOException e) {
                                log.error("发送错误信息失败", e);
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                // 保存完整回复
                                String fullResponseText = fullResponse.toString();
                                if (!fullResponseText.isEmpty()) {
                                    chatMessageService.saveAssistantMessage(userIdLong, sessionId, fullResponseText, null, modelName);
                                    
                                    // 更新会话消息数量
                                    Integer messageCount = chatMessageService.getMessageCount(userIdLong, sessionId);
                                    chatSessionService.updateMessageCount(userIdLong, sessionId, messageCount);
                                }
                                
                                // 发送完成事件
                                emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data(Map.of(
                                        "success", true, 
                                        "userId", request.getUserId(),
                                        "totalLength", fullResponseText.length()
                                    )));
                                
                                log.info("AI流式响应完成 [用户:{}] [会话:{}]: {} 字符", userId, sessionId, fullResponseText.length());
                                emitter.complete();
                            } catch (IOException e) {
                                log.error("发送完成事件失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                    );
                    
                } catch (Exception e) {
                    log.error("处理AI流式请求异常", e);
                    try {
                        emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("success", false, "message", "处理请求异常: " + e.getMessage())));
                    } catch (IOException ioException) {
                        log.error("发送异常信息失败", ioException);
                    }
                    emitter.completeWithError(e);
                }
            });
            
        } catch (Exception e) {
            log.error("创建流式响应失败", e);
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("success", false, "message", "创建流式连接失败: " + e.getMessage())));
            } catch (IOException ioException) {
                log.error("发送初始化错误失败", ioException);
            }
            emitter.completeWithError(e);
        }
        
        return emitter;
    }

    @Override
    public SessionListResponse getUserSessions(String userId) {
        try {
            log.info("获取用户 [{}] 会话列表", userId);
            
            Long userIdLong = parseUserId(userId);
            List<ChatSession> sessions = chatSessionService.getUserSessions(userIdLong);
            List<SessionInfo> sessionInfos = new ArrayList<>();
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            
            for (ChatSession session : sessions) {
                // 获取最后一条消息
                List<ChatMessage> recentMessages = chatMessageService.getRecentMessages(userIdLong, session.getSessionId(), 1);
                String lastMessage = recentMessages.isEmpty() ? "" : recentMessages.get(0).getContent();
                
                SessionInfo sessionInfo = new SessionInfo(
                    session.getSessionId(),
                    session.getTitle(),
                    session.getMessageCount(),
                    session.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    lastMessage
                );
                sessionInfos.add(sessionInfo);
            }
            
            log.info("用户 [{}] 共有 {} 个会话", userIdLong, sessionInfos.size());
            return SessionListResponse.success(userId, sessionInfos);
            
        } catch (Exception e) {
            log.error("获取用户 [{}] 会话列表失败: {}", userId, e.getMessage(), e);
            return SessionListResponse.error("获取会话列表失败: " + e.getMessage());
        }
    }

    @Override
    public SessionHistoryResponse getSessionHistory(String userId, String sessionId) {
        try {
            log.info("获取用户 [{}] 会话 [{}] 历史记录", userId, sessionId);
            
            Long userIdLong = parseUserId(userId);
            List<ChatMessage> messages = chatMessageService.getSessionHistory(userIdLong, sessionId);
            List<MessageInfo> messageInfos = new ArrayList<>();
            
            for (ChatMessage message : messages) {
                MessageInfo messageInfo = new MessageInfo(
                    message.getRole(),
                    message.getContent(),
                    message.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                );
                messageInfos.add(messageInfo);
            }
            
            log.info("用户 [{}] 会话 [{}] 共有 {} 条历史消息", userId, sessionId, messageInfos.size());
            return SessionHistoryResponse.success(userId, sessionId, messageInfos);
            
        } catch (Exception e) {
            log.error("获取用户 [{}] 会话 [{}] 历史记录失败: {}", userId, sessionId, e.getMessage(), e);
            return SessionHistoryResponse.error("获取会话历史记录失败: " + e.getMessage());
        }
    }

    @Override
    public OperationResponse clearUserMemory(String userId) {
        try {
            Long userIdLong = parseUserId(userId);
            
            // 删除用户所有消息
            int deletedMessages = chatMessageService.deleteAllUserMessages(userIdLong);
            
            // 删除用户所有会话
            int deletedSessions = chatSessionService.deleteAllUserSessions(userIdLong);
            
            log.info("用户 [{}] 记忆已清理: {} 条消息, {} 个会话", userId, deletedMessages, deletedSessions);
            
            return OperationResponse.success(
                String.format("用户记忆已清理: %d 条消息, %d 个会话", deletedMessages, deletedSessions), 
                "clear_memory", 
                userId
            );
        } catch (Exception e) {
            log.error("清理用户记忆失败 [用户:{}]", userId, e);
            return OperationResponse.error(
                "清理失败: " + e.getMessage(), 
                "clear_memory", 
                userId
            );
        }
    }

    @Override
    public OperationResponse clearSessionMemory(String userId, String sessionId) {
        try {
            Long userIdLong = parseUserId(userId);
            
            // 删除会话消息
            int deletedMessages = chatMessageService.deleteSessionMessages(userIdLong, sessionId);
            
            // 删除会话
            boolean sessionDeleted = chatSessionService.deleteSession(userIdLong, sessionId);
            
            log.info("用户 [{}] 会话 [{}] 记忆已清理: {} 条消息, 会话删除: {}", userIdLong, sessionId, deletedMessages, sessionDeleted);
            
            return OperationResponse.success(
                String.format("会话记忆清理成功: %d 条消息", deletedMessages),
                "clear_session_memory",
                userId
            );
        } catch (Exception e) {
            log.error("清理用户 [{}] 会话 [{}] 记忆失败: {}", userId, sessionId, e.getMessage(), e);
            return OperationResponse.error("清理会话记忆失败: " + e.getMessage(), "clear_session_memory", userId);
        }
    }

    @Override
    public StatsResponse getStats() {
        try {
            Map<String, Object> sessionStats = chatSessionService.getStatistics();
            
            log.info("获取统计信息: {}", sessionStats);
            
            return StatsResponse.success(sessionStats);
        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage(), e);
            return StatsResponse.error("获取统计信息失败: " + e.getMessage());
        }
    }

    @Override
    public HealthResponse health() {
        try {
            long startTime = System.currentTimeMillis();
            
            // 简单测试Ollama连接
            ChatResponse response = chatModel.call(new Prompt("hello"));
            
            long responseTime = System.currentTimeMillis() - startTime;
            log.debug("健康检查完成，AI模型响应时间: {}ms", responseTime);
            
            return HealthResponse.healthy(responseTime);
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return HealthResponse.unhealthy(e.getMessage());
        }
    }

    @Override
    public ServiceInfoResponse info() {
        log.debug("获取服务信息");
        return ServiceInfoResponse.defaultInfo();
    }

    @Override
    public Long parseUserId(String userId) {
        // TODO: 后续实现JWT token解析
        // 当前简单处理：如果是纯数字则转换，否则使用hashCode的绝对值
        if (userId == null || userId.trim().isEmpty()) {
            return 0L;
        }
        
        try {
            // 尝试直接转换为Long
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            // 如果不是数字，使用hashCode的绝对值作为用户ID
            return Math.abs((long) userId.hashCode());
        }
    }

    @Override
    public String generateSessionTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) {
            return "新对话";
        }
        
        // 截取前20个字符作为标题
        String title = firstMessage.trim();
        if (title.length() > 20) {
            title = title.substring(0, 20) + "...";
        }
        
        return title;
    }
}