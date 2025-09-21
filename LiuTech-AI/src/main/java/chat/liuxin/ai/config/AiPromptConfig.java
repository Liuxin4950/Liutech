package chat.liuxin.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AI提示词配置类
 * 定义智能助手的角色、行为规范和JSON输出约束
 *
 * 作者：刘鑫
 * 时间：2025-09-22
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.prompt")
public class AiPromptConfig {

    /**
     * 系统角色定义
     */
    private String systemRole;

    /**
     * 行为规范
     */
    private String behaviorGuidelines;

    /**
     * JSON输出约束
     */
    private String jsonOutputInstruction;

    /**
     * 获取完整的系统提示词
     */
    public String getFullSystemPrompt() {
        return String.format("""
                        %s

                        ## 行为规范
                        %s

                        ## JSON输出约束
                        %s

                        ## 当前时间
                        当前时间是：%s
                        """,
                systemRole,
                behaviorGuidelines,
                jsonOutputInstruction,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }

}
