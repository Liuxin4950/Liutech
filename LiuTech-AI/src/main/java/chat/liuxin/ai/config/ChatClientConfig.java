package chat.liuxin.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat客户端配置类
 * 
 * 主要职责：
 * 配置Spring AI的ChatClient，设置默认系统提示词
 * 
 * 业务位置：
 * 位于配置层，负责AI聊天客户端的初始化和配置
 * 
 * 核心功能点：
 * 1. 创建ChatClient Bean并注入到Spring容器
 * 2. 设置默认系统提示词，确保AI回复的一致性
 * 3. 集成AiPromptConfig，实现提示词的统一管理
 * 
 * 作者：刘鑫
 * 时间：2025-12-04
 */
@Configuration
public class ChatClientConfig {

    /**
     * 创建并配置ChatClient Bean
     * 
     * 业务流程：
     * 1. 接收ChatClient.Builder和AiPromptConfig依赖
     * 2. 设置默认系统提示词
     * 3. 构建并返回ChatClient实例
     * 
     * @param builder ChatClient构建器，由Spring AI自动提供
     * @param aiPromptConfig AI提示词配置，包含系统角色和行为规范
     * @return 配置完成的ChatClient实例
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, AiPromptConfig aiPromptConfig) {
        // 1. 设置默认系统提示词，确保每次AI调用都遵循相同的角色和行为规范
        // 2. 使用AiPromptConfig获取完整的系统提示词，包含角色、行为规范和输出约束
        return builder
                .defaultSystem(aiPromptConfig.getFullSystemPrompt())
                .build();
    }
}
