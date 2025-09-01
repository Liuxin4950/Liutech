package chat.liuxin.ai.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI聊天控制器
 * 基于Spring AI + Ollama实现
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiChatController {

    @Autowired
    private OllamaChatModel chatModel;

    /**
     * AI聊天接口
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return Map.of(
                    "success", false,
                    "message", "消息内容不能为空"
                );
            }

            log.info("收到AI聊天请求: {}", message);
            
            // 调用Ollama模型
            ChatResponse response = chatModel.call(new Prompt(message));
            String aiResponse = response.getResult().getOutput().getContent();
            
            log.info("AI响应: {}", aiResponse);
            
            return Map.of(
                "success", true,
                "data", Map.of(
                    "message", aiResponse,
                    "model", "ollama"
                )
            );
            
        } catch (Exception e) {
            log.error("AI聊天处理异常", e);
            return Map.of(
                "success", false,
                "message", "AI服务暂时不可用: " + e.getMessage()
            );
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        try {
            // 简单测试Ollama连接
            ChatResponse response = chatModel.call(new Prompt("hello"));
            return Map.of(
                "success", true,
                "status", "healthy",
                "message", "AI服务运行正常"
            );
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return Map.of(
                "success", false,
                "status", "unhealthy",
                "message", "AI服务异常: " + e.getMessage()
            );
        }
    }

    /**
     * 服务信息
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
            "service", "LiuTech AI Service",
            "version", "1.0.0",
            "author", "刘鑫",
            "description", "基于Spring AI + Ollama的智能助手"
        );
    }
}