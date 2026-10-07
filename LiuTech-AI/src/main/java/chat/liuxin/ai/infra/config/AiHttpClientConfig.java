package chat.liuxin.ai.infra.config;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/** 统一模型 SDK 连接参数与连接池；TTS 保持逐跳校验的独立 JDK 客户端。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OpenAiCommonProperties.class, OpenAiChatProperties.class})
public class AiHttpClientConfig {
    @Bean(destroyMethod = "close")
    public OpenAIClient aiSdkClient(OpenAiCommonProperties common, OpenAiChatProperties chat,
            ObjectProvider<ObservationRegistry> observations, ObjectProvider<MeterRegistry> meters,
            ObjectProvider<OpenAiHttpClientBuilderCustomizer> customizers) {
        var connection = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, chat);
        // 重试由明确业务策略决定。SDK 自动重放可能重复推理或工具，统一关闭。
        return OpenAiSetup.setupSyncClient(normalizeBaseUrl(connection.getBaseUrl()), connection.getApiKey(), connection.getCredential(),
                connection.getMicrosoftDeploymentName(), connection.getMicrosoftFoundryServiceVersion(),
                connection.getOrganizationId(), connection.isMicrosoftFoundry(), connection.isGitHubModels(),
                connection.getModel(), connection.getTimeout(), 0, connection.getProxy(), connection.getCustomHeaders(),
                observations.getIfUnique(() -> ObservationRegistry.NOOP),
                connection.isConnectionPoolMetricsEnabled() ? meters.getIfAvailable() : null,
                customizers.orderedStream().toList());
    }

    /** 官方OpenAI兼容服务要求/v1，兼容旧环境变量仅配置域名的情况。自建端点不猜测路径。 */
    static String normalizeBaseUrl(String value) {
        java.net.URI uri = java.net.URI.create(value);
        if (uri.getQuery() == null && uri.getFragment() == null
                && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                && java.util.Set.of("api.siliconflow.cn", "api.siliconflow.com", "api.openai.com").contains(uri.getHost())) {
            return value.replaceAll("/+$", "") + "/v1";
        }
        return value;
    }

    // sync/async 共用同一 SDK transport，关闭池只由 aiSdkClient 负责。
    @Bean(destroyMethod = "")
    public OpenAIClientAsync aiSdkAsyncClient(OpenAIClient aiSdkClient) {
        return aiSdkClient.async();
    }

    /** TTS 音频下载必须逐跳校验同源地址，禁止客户端自动跟随重定向。 */
    @Bean("ttsHttpClient")
    public HttpClient ttsHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
