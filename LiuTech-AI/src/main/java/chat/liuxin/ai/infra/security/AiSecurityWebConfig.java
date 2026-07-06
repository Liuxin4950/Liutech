package chat.liuxin.ai.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把 {@link AiRateLimitInterceptor} 挂到聊天/写作四个端点上。
 *
 * 只对真正消耗 AI 推理成本的路径限流，其他公开端点（models/status）不限流，
 * 免得踩到用户列模型这种低成本查询。
 */
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
