package chat.liuxin.ai.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AiSecurityWebConfig implements WebMvcConfigurer {

    private final AiRateLimitInterceptor aiRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiRateLimitInterceptor)
                .addPathPatterns(
                        "/ai/chat",
                        "/ai/chat/stream",
                        "/ai/writing",
                        "/ai/writing/stream");
    }
}
