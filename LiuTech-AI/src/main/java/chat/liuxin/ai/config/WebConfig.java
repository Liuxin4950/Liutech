package chat.liuxin.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类
 * 配置静态资源映射
 * 
 * 作者：刘鑫
 * 时间：2025-09-05
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置RestTemplate Bean
     * 用于HTTP请求调用
     * 
     * @return RestTemplate实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 配置静态资源映射
     * 将/static/**映射到classpath:/static/目录
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}