package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.resl.CheckinResl;
import chat.liuxin.liutech.resl.CheckinStatusResl;
import chat.liuxin.liutech.service.CheckinService;
import chat.liuxin.liutech.utils.JwtUtil;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;
// 移除Swagger依赖，项目不再使用
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 签到控制器
 * 
 * @author 刘鑫
 * @since 2025-01-30
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
// 移除Swagger注解
public class CheckinController {

    private final CheckinService checkinService;
    private final JwtUtil jwtUtil;

    /**
     * 每日签到
     * 
     * @param request HTTP请求
     * @return 签到结果
     */
    @PostMapping("/checkin")
    public Result<CheckinResl> checkin(HttpServletRequest request) {
        try {
            // 从请求头获取token
            String token = request.getHeader("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                return Result.fail(ErrorCode.UNAUTHORIZED);
            }
            
            token = token.substring(7); // 移除"Bearer "前缀
            
            // 验证token并获取用户ID
            if (!jwtUtil.validateToken(token)) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "Token无效或已过期");
            }
            
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "无法获取用户信息");
            }
            
            return checkinService.checkin(userId);
            
        } catch (Exception e) {
            log.error("签到接口异常", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取签到状态
     * 
     * @param request HTTP请求
     * @return 签到状态
     */
    @GetMapping("/checkin/status")
    public Result<CheckinStatusResl> getCheckinStatus(HttpServletRequest request) {
        try {
            // 从请求头获取token
            String token = request.getHeader("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                return Result.fail(ErrorCode.UNAUTHORIZED);
            }
            
            token = token.substring(7); // 移除"Bearer "前缀
            
            // 验证token并获取用户ID
            if (!jwtUtil.validateToken(token)) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "Token无效或已过期");
            }
            
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.fail(ErrorCode.UNAUTHORIZED, "无法获取用户信息");
            }
            
            return checkinService.getCheckinStatus(userId);
            
        } catch (Exception e) {
            log.error("获取签到状态接口异常", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR);
        }
    }
}