package chat.liuxin.ai.config;

import chat.liuxin.ai.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Security配置类 - AI服务专用
 * 配置AI服务的安全策略和访问权限
 *
 * 作者：刘鑫
 * 说明：
 * 1) AI服务主要提供聊天接口，需要JWT认证
 * 2) 统一配置认证/鉴权失败时的JSON返回
 * 3) 配置CORS支持前端跨域访问
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 配置安全过滤器链
     *
     * @param http HttpSecurity对象
     * @return SecurityFilterChain
     * @throws Exception 异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护（对于REST API通常不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 启用CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 统一处理：未登录/权限不足时返回JSON
            .exceptionHandling(ex -> ex
                // 未认证（如未携带/携带无效Token）
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json;charset=UTF-8");
                    Map<String, Object> body = new HashMap<>();
                    body.put("success", false);
                    body.put("message", "未登录或Token已失效");
                    body.put("code", 401);
                    new ObjectMapper().writeValue(response.getWriter(), body);
                })
                // 已认证但权限不足
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    Map<String, Object> body = new HashMap<>();
                    body.put("success", false);
                    body.put("message", "权限不足，拒绝访问");
                    body.put("code", 403);
                    new ObjectMapper().writeValue(response.getWriter(), body);
                })
            )
            // 设置会话管理为无状态（JWT不需要session）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置请求授权
            .authorizeHttpRequests(authz -> authz
                // 预检请求必须放行
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // AI聊天接口需要认证 - 需要有效的JWT Token
                .requestMatchers("/ai/**").authenticated()
                
                // 健康检查接口可以公开访问 - 无需Token认证
                .requestMatchers("/health", "/actuator/**").permitAll()
                
                // 静态资源文件可以公开访问 - 无需Token认证
                .requestMatchers("/static/**").permitAll()
                
                // 其他所有请求都需要认证 - 需要有效的JWT Token
                .anyRequest().authenticated()
            )
            // 添加JWT认证过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS配置
     * 允许前端跨域访问AI服务API
     *
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的源（前端地址）
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost:3001",
            "http://127.0.0.1:3001",
            "http://localhost:5173",
            "http://127.0.0.1:5173"
        ));

        // 允许的HTTP方法
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        // 允许的请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // 允许发送凭证（如cookies）
        configuration.setAllowCredentials(true);

        // 预检请求的缓存时间（秒）
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * 密码加密器Bean
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}