package chat.liuxin.ai.service;

import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI聊天核心服务接口
 * 提供普通模式和流式模式的聊天功能
 *
 * 作者：刘鑫
 * 时间：2025-12-04
 */
public interface AiChatService {

    /**
     * 1) 普通聊天：一次性返回完整AI回复
     * @param request 聊天请求
     * @param userId 用户ID（从JWT解析获得）
     * @return 聊天响应对象
     */
    ChatResponse processChat(ChatRequest request, Long userId);

    /**
     * 2) 流式聊天：通过SSE推送AI回复流
     * 
     * 业务流程：
     * 1. 创建SseEmitter并设置超时和完成/错误处理
     * 2. 异步处理聊天请求，避免阻塞主线程
     * 3. 发送开始事件，标识流开始
     * 4. 逐块发送AI响应数据
     * 5. 发送完成事件，标识流结束
     * 6. 在完成或错误时保存完整消息到数据库
     * 
     * @param request 聊天请求
     * @param userId 用户ID（从JWT解析获得）
     * @return SseEmitter对象，用于推送流式响应
     */
    SseEmitter processStreamChat(ChatRequest request, Long userId);
}