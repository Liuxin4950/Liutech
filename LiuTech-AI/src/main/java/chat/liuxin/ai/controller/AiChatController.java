package chat.liuxin.ai.controller;

import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

/**
 * AI聊天控制器（精简版）
 * 学习顺序：
 * 1) 读 /chat 普通模式
 * 2) 读 /chat/stream 流式模式
 *
 * 作者：刘鑫
 * 时间：2025-09-05
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "*")
@Validated
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * 1) AI聊天接口 - 普通模式
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        log.debug("转发AI聊天请求到Service层 [用户:{}]", request.getUserId());
        return aiChatService.processChat(request);
    }

    /**
     * 2) AI聊天接口 - 流式输出模式（SSE）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        log.debug("转发AI流式聊天请求到Service层 [用户:{}]", request.getUserId());
        return aiChatService.processChatStream(request);
    }
}