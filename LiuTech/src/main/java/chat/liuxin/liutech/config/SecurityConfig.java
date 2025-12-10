package chat.liuxin.liutech.config;

import chat.liuxin.liutech.filter.JwtAuthenticationFilter;
import chat.liuxin.liutech.filter.RequestTraceFilter;
import chat.liuxin.liutech.common.ErrorCode; // 新增：统一错误码
import chat.liuxin.liutech.common.Result;    // 新增：统一响应体
import com.fasterxml.jackson.databind.ObjectMapper; // 新增：用于将对象写为JSON
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus; // 新增：HTTP状态码
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // 新增：开启方法级权限注解
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // 新增：密码加密器Bean
import org.springframework.http.HttpMethod; // 新增：显式允许预检请求

import java.util.Arrays;

/**
 * Spring Security配置类
 * 配置安全策略和访问权限
 *
 * 作者：刘鑫
 * 说明：
 * 1) 开启 @EnableMethodSecurity 后，Controller/Service 上的 @PreAuthorize 等注解才能生效；
 * 2) 统一配置认证/鉴权失败时的JSON返回，和全局异常格式保持一致，便于前端统一处理。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 新增：启用方法级安全控制（如 @PreAuthorize）
public class SecurityConfig {

    // 依赖说明：
    // - 依赖 JwtAuthenticationFilter 进行无状态认证
    // - 使用统一响应体 Result 与错误码 ErrorCode 输出401/403
    // - 通过 @EnableMethodSecurity 激活 @PreAuthorize 等注解
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private RequestTraceFilter requestTraceFilter;

    /**
     * 配置安全过滤器链
     *
     * @param http HttpSecurity对象
     * @return SecurityFilterChain
     * @throws Exception 异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 核心职责：统一安全策略
        // - 关闭CSRF（REST场景）
        // - 开启CORS（前端跨域）
        // - 401/403 统一JSON返回（前端一致处理）
        // - 会话无状态（走JWT）
        // - 白名单优先（只放行公开接口）
        // - 其他默认认证保护
        // - 在 UsernamePasswordAuthenticationFilter 之前加入 JWT 认证过滤器
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // 统一401返回
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json;charset=UTF-8");
                    Result<Void> body = Result.fail(ErrorCode.UNAUTHORIZED, "未登录或Token已失效");
                    new ObjectMapper().writeValue(response.getWriter(), body);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // 统一403返回
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    Result<Void> body = Result.fail(ErrorCode.FORBIDDEN, "权限不足，拒绝访问");
                    new ObjectMapper().writeValue(response.getWriter(), body);
                })
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/").permitAll()
                .requestMatchers("/user/register", "/user/login").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/admin/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/posts/my").authenticated()
                .requestMatchers(HttpMethod.GET, "/posts/drafts").authenticated()
                .requestMatchers(HttpMethod.GET, "/posts/favorites").authenticated()
                .requestMatchers(HttpMethod.GET, "/posts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/tags/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/comments/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/announcements/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/user/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/author/profile").permitAll()
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/upload/**").authenticated()
                .anyRequest().authenticated()
            )
            // 依赖：JwtAuthenticationFilter 提供身份认证上下文
            .addFilterBefore(requestTraceFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS配置
     * 允许前端跨域访问后端API
     *
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
    
        // 允许的源（前端地址）——使用patterns支持端口与子域名，解决服务器环境 liuxin.chat:3000 的跨域
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://liuxin.chat",
            "https://liuxin.chat",
            "http://liuxin.chat:*",
            "https://liuxin.chat:*",
            "http://www.liuxin.chat",
            "https://www.liuxin.chat",
            "http://www.liuxin.chat:*",
            "https://www.liuxin.chat:*"
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

    // 新增：统一提供BCryptPasswordEncoder Bean，避免在各处手动new，便于后续替换算法或集中配置
    // 作者：刘鑫，时间：2025-08-26（Asia/Shanghai）
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
