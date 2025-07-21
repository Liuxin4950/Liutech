package chat.liuxin.liutech.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置类
 * 配置API文档的基本信息和展示设置
 *
 * @author liuxin
 */
@Configuration
public class SwaggerConfig {

    /**
     * 配置OpenAPI信息
     *
     * @return OpenAPI配置
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LiuTech博客系统API")
                        .description("基于Spring Boot的博客系统后端API接口文档")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("liuxin")
                                .email("1371149587@qq.com")
                                .url("https://github.com/Liuxin4950"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
