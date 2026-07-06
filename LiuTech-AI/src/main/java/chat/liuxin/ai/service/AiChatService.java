package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.ChatResponse;
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
     * 看板娘同步聊天：阻塞直到拿到完整 AI 回复,登录态会持久化用户和 AI 消息。
     *
     * userId 为空表示访客模式,不入库、不写会话。
     * 未传 conversationId 且登录时会自动新建一条会话。
     */
    ChatResponse processChat(ChatRequest request, Long userId);

    /**
     * 看板娘流式聊天,通过 SSE 推送分片、TTS 音频段和 Live2D 表情提示。
     *
     * 委托给 {@link StreamingChatService},内部异步执行,立即返回 emitter。
     * 完成/错误时会异步保存 assistant 消息(异常保留 partial 文本,status=3)。
     */
    SseEmitter processStreamChat(ChatRequest request, Long userId);

    /**
     * 写作助手同步调用,注册 WritingTools(分类/标签/草稿工具)。
     *
     * 与看板娘聊天的区别:不持久化消息、系统提示词一致,底层用流式收集回退避免长响应超时。
     */
    ChatResponse processWriting(ChatRequest request, Long userId);

    /**
     * 写作助手流式调用,SSE 输出但不落库,同样注册 WritingTools。
     */
    SseEmitter processWritingStream(ChatRequest request, Long userId);
}