package chat.liuxin.ai.infra.config;

import chat.liuxin.ai.common.client.CancellableOpenAiChatModel;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 唯一默认 ChatModel；业务工具循环留在 SiliconFlowChatClient，传输仅补齐 SDK 取消。 */
@Configuration(proxyBeanMethods = false)
public class ChatClientConfig {
    @Bean
    @Primary
    public ChatModel chatModel(OpenAIClient aiSdkClient, OpenAIClientAsync aiSdkAsyncClient,
            OpenAiChatProperties chatProperties, ToolCallingManager toolCallingManager,
            ObjectProvider<ObservationRegistry> observations,
            ObjectProvider<ChatModelObservationConvention> conventions) {
        return new CancellableOpenAiChatModel(aiSdkClient, aiSdkAsyncClient,
                chatProperties.toOptions().mutate().maxRetries(0).build(), toolCallingManager,
                observations.getIfUnique(() -> ObservationRegistry.NOOP), conventions.getIfAvailable());
    }
}
