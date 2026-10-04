package chat.liuxin.ai.infra.config;

import chat.liuxin.ai.common.client.CancellableOpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

class ChatClientConfigTest {
    @Test
    void normalizesOnlyOfficialBareApiRootsWithoutDuplicatingVersionOrGuessingCustomPaths() {
        assertEquals("https://api.siliconflow.cn/v1", AiHttpClientConfig.normalizeBaseUrl("https://api.siliconflow.cn"));
        assertEquals("https://api.openai.com/v1", AiHttpClientConfig.normalizeBaseUrl("https://api.openai.com/"));
        assertEquals("https://api.siliconflow.cn/v1", AiHttpClientConfig.normalizeBaseUrl("https://api.siliconflow.cn/v1"));
        assertEquals("http://127.0.0.1:11434/api", AiHttpClientConfig.normalizeBaseUrl("http://127.0.0.1:11434/api"));
        assertEquals("http://127.0.0.1:13309", AiHttpClientConfig.normalizeBaseUrl("http://127.0.0.1:13309"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({AiHttpClientConfig.class, ChatClientConfig.class})
    static class Application {}

    @Test
    void applicationHasOneDefaultModelAndRetainsServerOptions() {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setLogStartupInfo(false);
        try (var context = app.run("--spring.config.location=classpath:application.yml",
                "--spring.main.banner-mode=off", "--spring.ai.openai.api-key=local-test-key",
                "--spring.ai.openai.base-url=http://127.0.0.1:1/v1")) {
            assertEquals(1, context.getBeansOfType(ChatModel.class).size());
            ChatModel model = context.getBean(ChatModel.class);
            assertInstanceOf(CancellableOpenAiChatModel.class, model);
            assertEquals("deepseek-ai/DeepSeek-V3.2", model.getOptions().getModel());
        }
    }
}
