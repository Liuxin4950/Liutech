package chat.liuxin.ai.service;

import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI聊天核心服务接口
 * 整合会话管理、消息处理和AI模型调用
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
public interface AiChatService {

    /**
     * 处理AI聊天请求（普通模式）
     * 
     * @param request 聊天请求
     * @return 聊天响应
     */
    ChatResponse processChat(ChatRequest request);

    /**
     * 处理AI聊天请求（流式模式）
     * 
     * @param request 聊天请求
     * @return SSE流式响应
     */
    SseEmitter processChatStream(ChatRequest request);

    /**
     * 获取用户会话列表
     * 
     * @param userId 用户ID
     * @return 会话列表响应
     */
    SessionListResponse getUserSessions(String userId);

    /**
     * 获取会话历史记录
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 会话历史响应
     */
    SessionHistoryResponse getSessionHistory(String userId, String sessionId);

    /**
     * 清理用户所有会话
     * 
     * @param userId 用户ID
     * @return 操作结果
     */
    OperationResponse clearUserMemory(String userId);

    /**
     * 清理指定会话
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 操作结果
     */
    OperationResponse clearSessionMemory(String userId, String sessionId);

    /**
     * 获取统计信息
     * 
     * @return 统计信息响应
     */
    StatsResponse getStats();

    /**
     * 健康检查
     * 
     * @return 健康状态响应
     */
    HealthResponse health();

    /**
     * 获取服务信息
     * 
     * @return 服务信息响应
     */
    ServiceInfoResponse info();

    /**
     * 解析用户ID（从token或其他方式）
     * 当前默认返回0，后续可扩展JWT解析
     * 
     * @param token 用户token（可选）
     * @return 用户ID
     */
    Long parseUserId(String token);

    /**
     * 生成会话标题（基于首条消息内容）
     * 
     * @param firstMessage 首条消息内容
     * @return 生成的标题
     */
    String generateSessionTitle(String firstMessage);
}