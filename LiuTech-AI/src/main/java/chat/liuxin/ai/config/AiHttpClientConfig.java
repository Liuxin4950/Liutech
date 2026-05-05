package chat.liuxin.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 统一 AI 出站 HTTP 传输层。
 *
 * Spring AI 默认链路在当前环境下通过 WebClient 调用 SiliconFlow 时会出现 TLS handshake 被远端终止。
 * 这里显式切到 JDK HttpClient，和本地已验证可用的直连方式保持一致。
 */
@Configuration
public class AiHttpClientConfig {

    @Bean
    public HttpClient aiHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    @Primary
    public RestClient.Builder aiRestClientBuilder(HttpClient aiHttpClient) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(aiHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    @Primary
    public WebClient.Builder aiWebClientBuilder(HttpClient aiHttpClient) {
        return WebClient.builder().clientConnector(new JdkClientHttpConnector(aiHttpClient));
    }
}
