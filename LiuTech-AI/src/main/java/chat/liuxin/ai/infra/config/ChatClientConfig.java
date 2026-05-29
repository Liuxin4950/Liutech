package chat.liuxin.ai.infra.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
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
 *
 * 作者：刘鑫
 * 时间：2025-12-04
 */
@Configuration
public class ChatClientConfig {

    /**
     * 创建并配置ChatClient Bean
     *
     * 避免在@Bean方法签名中直接引用 ChatModel，防止 IDE/运行时类路径异常时
     * 在 Spring 反射解析阶段提前触发 NoClassDefFoundError。
     *
     * @param applicationContext Spring 容器
     * @return 配置完成的ChatClient实例
     */
    @Bean
    public ChatClient chatClient(ApplicationContext applicationContext) {
        try {
            Class<?> chatModelClass = Class.forName("org.springframework.ai.chat.model.ChatModel");
            Object chatModel = applicationContext.getBean(chatModelClass);
            return (ChatClient) ChatClient.class.getMethod("create", chatModelClass).invoke(null, chatModel);
        } catch (Exception e) {
            throw new IllegalStateException("创建 ChatClient 失败，请检查 Spring AI 依赖与 IDE 运行时类路径是否一致", e);
        }
    }
}
