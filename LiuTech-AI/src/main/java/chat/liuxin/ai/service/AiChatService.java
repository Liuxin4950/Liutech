package chat.liuxin.ai.service;

import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;

/**
 * AI聊天核心服务接口（精简版）
 * 只保留与“聊天”直接相关的普通模式方法。
 *
 * 作者：刘鑫
 * 时间：2025-09-05
 */
public interface AiChatService {

    /**
     * 1) 普通聊天：一次性返回完整AI回复
     * @param request 聊天请求
     * @param userId 用户ID（从JWT解析获得）
     */
    ChatResponse processChat(ChatRequest request, Long userId);

    
}