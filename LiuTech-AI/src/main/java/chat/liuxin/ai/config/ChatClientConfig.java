package chat.liuxin.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, AiPromptConfig aiPromptConfig) {
        return builder
                .defaultSystem(aiPromptConfig.getFullSystemPrompt())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}