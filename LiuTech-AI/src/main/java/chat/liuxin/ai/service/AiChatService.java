package chat.liuxin.ai.service;

import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI聊天核心服务接口（精简版）
 * 只保留与“聊天”直接相关的两个方法，方便快速上手与学习。
 * 学习顺序：
 * 1) 先看 processChat：一次性返回完整答案的最小实现
 * 2) 再看 processChatStream：按块推送答案的流式实现
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

    /**
     * 2) 流式聊天：通过SSE按块推送AI回复
     * @param request 聊天请求
     * @param userId 用户ID（从JWT解析获得）
     */
    SseEmitter processChatStream(ChatRequest request, Long userId);
}