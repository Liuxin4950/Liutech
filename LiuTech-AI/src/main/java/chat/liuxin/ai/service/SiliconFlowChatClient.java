package chat.liuxin.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiliconFlowChatClient {

    private final ChatClient chatClient;

    public String chat(List<Message> messages) {
        return chatClient
                .prompt()
                .messages(messages)
                .options(OpenAiChatOptions.builder()
                        .withModel("THUDM/glm-4-9b-chat")
                        .withTemperature(0.2)
                        .build())
                .call()
                .content();
    }
}
