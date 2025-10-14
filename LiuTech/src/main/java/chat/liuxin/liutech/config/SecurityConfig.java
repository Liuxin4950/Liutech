package chat.liuxin.liutech.config;

import chat.liuxin.liutech.filter.JwtAuthenticationFilter;
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
            // 统一处理：未登录/权限不足时返回JSON（解决默认返回HTML的问题）
            // 这样前端 http://localhost:3000 可以直接解析为统一的Result结构
            .exceptionHandling(ex -> ex
                // 未认证（如未携带/携带无效Token）
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json;charset=UTF-8");
                    // 统一响应体，错误码使用 ErrorCode.UNAUTHORIZED
                    Result<Void> body = Result.fail(ErrorCode.UNAUTHORIZED, "未登录或Token已失效");
                    new ObjectMapper().writeValue(response.getWriter(), body);
                })
                // 已认证但权限不足（如没有ADMIN角色）
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    // 统一响应体，错误码使用 ErrorCode.FORBIDDEN
                    Result<Void> body = Result.fail(ErrorCode.FORBIDDEN, "权限不足，拒绝访问");
                    new ObjectMapper().writeValue(response.getWriter(), body);
                })
            )
            // 设置会话管理为无状态（JWT不需要session）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置请求授权 - 优化版本：白名单模式，只配置公开接口
            .authorizeHttpRequests(authz -> authz
                // ========== 完全公开的接口（无需任何认证） ==========
                .requestMatchers("/").permitAll()
                .requestMatchers("/user/register", "/user/login").permitAll()
                // 预检请求必须放行，否则浏览器跨域会被拦截
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ========== 管理端接口（需要认证，具体权限由@PreAuthorize控制） ==========
                // 注意：必须放在 /tags/** 等通配符之前，避免被误匹配为公开接口
                .requestMatchers("/admin/**").authenticated()  // 管理端接口需要认证

                // ========== 只读公开接口（GET请求） ==========
                // 需要认证的文章接口
                .requestMatchers(HttpMethod.GET, "/posts/my").authenticated()
                .requestMatchers(HttpMethod.GET, "/posts/drafts").authenticated()
                .requestMatchers(HttpMethod.GET, "/posts/favorites").authenticated()
                // 公开的文章接口
                .requestMatchers(HttpMethod.GET, "/posts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/tags/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/comments/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/announcements/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/user/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/user/profile").permitAll()
                .requestMatchers(HttpMethod.POST, "/upload/**").authenticated()

                // ========== 其他所有请求都需要认证 ==========
                // 包括：POST、PUT、DELETE等写操作
                // 这样就不用一个个配置了，默认保护所有写操作
                .anyRequest().authenticated()
            )
            // 添加JWT认证过滤器
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
