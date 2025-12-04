package chat.liuxin.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AI提示词配置类
 * 
 * 主要职责：
 * 管理AI助手的系统提示词配置，包括角色定义、行为规范和输出格式
 * 
 * 业务位置：
 * 位于配置层，为ChatClient提供系统提示词配置
 * 
 * 核心功能点：
 * 1. 从配置文件加载提示词相关配置
 * 2. 构建完整的系统提示词，包含角色、行为规范和输出约束
 * 3. 动态添加当前时间信息，提高AI回复的时效性
 * 4. 提供统一的提示词格式，确保AI回复的一致性
 * 
 * 作者：刘鑫
 * 时间：2025-09-22
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.ai.prompt")
public class AiPromptConfig {

    /**
     * 系统角色定义
     * 配置项：spring.ai.prompt.system-role
     */
    private String systemRole;

    /**
     * 行为规范
     * 配置项：spring.ai.prompt.behavior-guidelines
     */
    private String behaviorGuidelines;

    /**
     * JSON输出约束
     * 配置项：spring.ai.prompt.json-output-instruction
     */
    private String jsonOutputInstruction;

    /**
     * 构建完整的系统提示词
     * 
     * 业务流程：
     * 1. 组合系统角色定义
     * 2. 添加行为规范说明
     * 3. 添加输出格式约束
     * 4. 动态插入当前时间信息
     * 
     * @return 格式化后的完整系统提示词
     */
    public String getFullSystemPrompt() {
        // 1. 构建提示词模板，包含角色、行为规范和输出约束
        // 2. 动态添加当前时间，提高AI回复的时效性和准确性
        return String.format("""
                        %s

                        ## 行为规范
                        %s

                        ## JSON输出约束
                        %s

                        ## 当前时间
                        当前时间是：%s
                        """,
                systemRole,                                    // 系统角色定义
                behaviorGuidelines,                            // 行为规范说明
                jsonOutputInstruction,                         // 输出格式约束
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))  // 当前时间
        );
    }

}
