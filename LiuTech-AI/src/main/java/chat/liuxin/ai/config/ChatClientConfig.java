package chat.liuxin.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, AiPromptConfig aiPromptConfig) {
        return builder
                .defaultSystem(aiPromptConfig.getFullSystemPrompt())
                .build();
    }
}
