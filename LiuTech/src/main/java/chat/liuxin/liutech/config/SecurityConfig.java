package chat.liuxin.liutech.config;

import chat.liuxin.liutech.filter.JwtAuthenticationFilter;
import chat.liuxin.liutech.filter.RequestTraceFilter;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;   
import tools.jackson.databind.ObjectMapper;
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
@EnableMethodSecurity
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
            // 白名单配置：公开接口（统一使用 SecurityWhitelist，与 JwtAuthenticationFilter 同源）
            .authorizeHttpRequests(authz -> {
                // OPTIONS 预检请求
                authz.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                // 完全公开路径（任意方法）
                for (String path : SecurityWhitelist.FULLY_PUBLIC) {
                    authz.requestMatchers(path).permitAll();
                }

                // 需认证的路径（在公开前缀下但需要登录）
                for (String path : SecurityWhitelist.AUTHENTICATED_PATHS) {
                    authz.requestMatchers(HttpMethod.GET, path).authenticated();
                }

                // GET 公开精确路径
                authz.requestMatchers(HttpMethod.GET,
                        SecurityWhitelist.PUBLIC_GET_EXACT.toArray(new String[0])).permitAll();

                // GET 公开前缀路径
                for (String prefix : SecurityWhitelist.PUBLIC_GET_PREFIXES) {
                    authz.requestMatchers(HttpMethod.GET, prefix + "**").permitAll();
                }

                // HEAD 公开前缀路径
                for (String prefix : SecurityWhitelist.PUBLIC_HEAD_PREFIXES) {
                    authz.requestMatchers(HttpMethod.HEAD, prefix + "**").permitAll();
                }

                // POST 公开精确路径
                authz.requestMatchers(HttpMethod.POST,
                        SecurityWhitelist.PUBLIC_POST_EXACT.toArray(new String[0])).permitAll();

                // 拒绝访问的路径（GET/HEAD）
                for (String prefix : SecurityWhitelist.DENY_PREFIXES) {
                    authz.requestMatchers(HttpMethod.GET, prefix + "**").denyAll();
                    authz.requestMatchers(HttpMethod.HEAD, prefix + "**").denyAll();
                }

                // 管理后台
                authz.requestMatchers("/admin/**").hasRole("ADMIN");

                // 上传需认证
                authz.requestMatchers(HttpMethod.POST, "/upload/**").authenticated();

                authz.anyRequest().authenticated();
            })
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
            "https://www.liuxin.chat:*",
            "http://admin.liuxin.chat",
            "https://admin.liuxin.chat"
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

   
    // 作者：刘鑫，时间：2025-08-26（Asia/Shanghai）
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
