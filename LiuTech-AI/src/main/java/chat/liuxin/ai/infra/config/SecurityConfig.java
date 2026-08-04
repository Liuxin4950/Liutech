package chat.liuxin.ai.infra.config;

import chat.liuxin.ai.common.utils.WebUtils;
import chat.liuxin.ai.infra.filter.JwtAuthenticationFilter;
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
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Security配置类 - AI服务专用
 * 配置AI服务的安全策略和访问权限
 *
 * 作者：刘鑫
 * 说明：
 * 1) /ai/chat、/ai/chat/stream 公开（permitAll），登录用户走会话持久化，游客走临时模式
 * 2) /ai/writing、/ai/writing/stream 需管理员角色（hasRole('ADMIN')）
 * 3) 统一配置认证/鉴权失败时的JSON返回
 * 4) 配置CORS支持前端跨域访问
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;
    
    // 使用RequestAttributeSecurityContextRepository解决SSE流完成后认证上下文丢失问题
    private final SecurityContextRepository securityContextRepository = 
            new RequestAttributeSecurityContextRepository();

    /**
     * 构建 Spring Security 过滤器链。
     *
     * 关键决策：
     * - CSRF 关闭（REST API + JWT 不需要）
     * - Session 无状态（STATELESS，每次请求都从 JWT 重新认证）
     * - 用 RequestAttributeSecurityContextRepository 存上下文，解决 SSE 完成后
     *   SecurityContextHolder 被清空导致 authenticationEntryPoint 二次触发的坑
     * - 未认证/权限不足统一返回 JSON（{success,message,code}），前端便于处理
     * - SSE 请求响应已提交时跳过异常写入，避免破坏 event-stream 格式
     *
     * 端点分级：
     * - 公开：/ai/models/**、/ai/status、/ai/chat、/ai/chat/stream、/health、/actuator/health、/static/**
     * - 管理员：/ai/writing、/ai/writing/stream、/admin/**、/ai/admin/**
     * - 其他：需要有效 JWT
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护（对于REST API通常不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 启用CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 设置SecurityContextRepository，解决SSE流完成后认证上下文丢失问题
            .securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository))
            // 统一处理：未登录/权限不足时返回JSON
            .exceptionHandling(ex -> ex
                // 未认证（如未携带/携带无效Token）
                .authenticationEntryPoint((request, response, authException) -> {
                    // 对于SSE请求，如果响应已经提交，则不处理认证异常
                    if (WebUtils.isSseRequest(request) && response.isCommitted()) {
                        return;
                    }
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json;charset=UTF-8");
                    Map<String, Object> body = new HashMap<>();
                    body.put("success", false);
                    body.put("message", "未登录或Token已失效");
                    body.put("code", 401);
                    objectMapper.writeValue(response.getWriter(), body);
                })
                // 已认证但权限不足
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // 对于SSE请求，如果响应已经提交，则不处理权限拒绝异常
                    if (WebUtils.isSseRequest(request) && response.isCommitted()) {
                        return;
                    }
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    Map<String, Object> body = new HashMap<>();
                    body.put("success", false);
                    body.put("message", "权限不足，拒绝访问");
                    body.put("code", 403);
                    objectMapper.writeValue(response.getWriter(), body);
                })
            )
            // 设置会话管理为无状态（JWT不需要session）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置请求授权
            .authorizeHttpRequests(authz -> authz
                // 预检请求必须放行
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 公开API：模型与游客聊天能力
                .requestMatchers("/ai/models/**").permitAll()
                .requestMatchers("/ai/status").permitAll()
                .requestMatchers("/ai/chat", "/ai/chat/stream").permitAll()
                // 写作助手是博主/管理员功能，配置层直接限制管理员，避免依赖业务层补判
                .requestMatchers("/ai/writing", "/ai/writing/stream").hasRole("ADMIN")

                // 管理员API：模型管理（必须是管理员）
                .requestMatchers("/admin/**", "/ai/admin/**").hasRole("ADMIN")

                // 健康检查接口可以公开访问 - 无需Token认证
                .requestMatchers("/health", "/actuator/health").permitAll()

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
}
