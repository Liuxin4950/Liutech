package chat.liuxin.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LiuTech AI 服务启动类
 * 基于Spring AI + Ollama实现智能助手功能
 * 
 * @author 刘鑫
 * @since 2025-01-31
 */
@Slf4j
@SpringBootApplication
public class LiuTechAiApplication {

    public static void main(String[] args) {
        log.info("=== LiuTech AI 服务启动中... ===");
        SpringApplication.run(LiuTechAiApplication.class, args);
        log.info("服务地址: http://localhost:8081");
        log.info("=== LiuTech AI 服务启动完成！===");
    }
}