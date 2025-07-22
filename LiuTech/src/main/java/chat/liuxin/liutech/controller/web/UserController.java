package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.req.ChangePasswordReq;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.resl.LoginResl;
import chat.liuxin.liutech.service.UserService;
import chat.liuxin.liutech.utils.JwtUtil;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户控制器
 * 提供用户相关的REST API接口，包括注册、登录、用户管理等功能
 * 
 * @author liuxin
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户注册接口
     * 创建新用户账户，包括用户名唯一性检查、邮箱唯一性检查、密码加密等
     * 
     * @param registerReq 注册请求参数，包含用户名、邮箱、密码等信息
     * @return 注册成功的用户信息（脱敏后）
     */
    @PostMapping("/register")
    public Result<UserResl> register(@Valid @RequestBody RegisterReq registerReq) {
        log.info("收到用户注册请求，用户名: {}", registerReq.getUsername());
        UserResl userResl = userService.register(registerReq);
        log.info("用户注册成功，用户名: {}", registerReq.getUsername());
        return Result.success("注册成功", userResl);
    }

    /**
     * 用户登录接口
     * 验证用户凭据并返回JWT token
     * 
     * @param loginReq 登录请求参数，包含用户名和密码
     * @return 包含JWT token的登录响应，客户端需要保存token用于后续API调用
     */
    @PostMapping("/login")
    public Result<LoginResl> login(@Valid @RequestBody LoginReq loginReq) {
        log.info("收到用户登录请求，用户名: {}", loginReq.getUsername());
        LoginResl loginResl = userService.login(loginReq);
        log.info("用户登录成功，用户名: {}", loginReq.getUsername());
        return Result.success("登录成功", loginResl);
    }
    
    /**
     * 获取当前用户信息接口
     * 从Spring Security上下文中获取认证用户信息
     * 
     * @return 当前用户信息（脱敏后）
     */
    @GetMapping("/current")
    public Result<UserResl> getCurrentUser() {
        log.info("收到获取当前用户信息请求");
        
        // 1. 从Security上下文获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("用户未认证");
            return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        
        // 2. 获取用户名
        String username = authentication.getName();
        if (username == null) {
            log.warn("无法获取用户名");
            return Result.fail(ErrorCode.UNAUTHORIZED, "认证信息无效");
        }
        
        // 3. 根据用户名查询用户信息
        List<Users> users = userService.findByUserName(username);
        if (users == null || users.isEmpty()) {
            log.warn("用户不存在，用户名: {}", username);
            return Result.fail(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        Users user = users.get(0);
        
        // 4. 转换为响应对象（不包含敏感信息）
        UserResl userResl = new UserResl();
        org.springframework.beans.BeanUtils.copyProperties(user, userResl);
        
        log.info("成功获取当前用户信息，用户名: {}", user.getUsername());
        return Result.success("获取用户信息成功", userResl);
    }
    
    /**
     * 修改密码接口
     * 从Spring Security上下文中获取认证用户信息
     * 
     * @param changePasswordReq 修改密码请求参数
     * @return 修改结果
     */
    @PutMapping("/password")
    public Result<String> changePassword(@Valid @RequestBody ChangePasswordReq changePasswordReq) {
        log.info("收到修改密码请求");
        
        // 1. 验证密码一致性
        if (!changePasswordReq.isPasswordMatch()) {
            log.warn("新密码和确认密码不一致");
            return Result.fail(ErrorCode.PARAMS_ERROR, "新密码和确认密码不一致");
        }
        
        // 2. 从Security上下文获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("用户未认证");
            return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        
        // 3. 获取用户名
        String username = authentication.getName();
        if (username == null) {
            log.warn("无法获取用户名");
            return Result.fail(ErrorCode.UNAUTHORIZED, "认证信息无效");
        }
        
        // 4. 根据用户名查询用户信息
        List<Users> users = userService.findByUserName(username);
        if (users == null || users.isEmpty()) {
            log.warn("用户不存在，用户名: {}", username);
            return Result.fail(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        Users user = users.get(0);
        
        // 5. 调用服务层修改密码
        try {
            userService.changePassword(user.getId(), username, user.getPasswordHash(), 
                    changePasswordReq.getOldPassword(), changePasswordReq.getNewPassword());
            log.info("用户 {} 密码修改成功", username);
            return Result.success("密码修改成功");
        } catch (Exception e) {
            log.error("密码修改失败: {}", e.getMessage());
            return Result.fail(ErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
    
    /**
     * 获取用户信息
     * GET /user - 获取所有用户列表
     * GET /user/{id} - 根据ID获取单个用户
     * GET /user?username=xxx - 根据用户名查询用户
     * 
     * @param id 用户ID（可选）
     * @param username 用户名（可选，支持模糊查询）
     * @return 用户信息或用户列表
     */
    @GetMapping
    public Result<?> getUsers(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String username) {
        // 根据ID获取单个用户
        if (id != null) {
            log.info("根据ID获取用户信息，ID: {}", id);
            Users user = userService.findById(id);
            return Result.success(user);
        }
        
        // 根据用户名查询用户
        if (username != null && !username.trim().isEmpty()) {
            log.info("根据用户名查询用户，用户名: {}", username);
            List<Users> users = userService.findByUserName(username);
            return Result.success(users);
        }
        
        // 获取所有用户列表
        log.info("获取所有用户列表");
        List<Users> users = userService.findAll();
        return Result.success(users);
    }

    /**
     * 根据ID获取单个用户
     * GET /user/{id}
     * 
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result<Users> getUserById(
            @PathVariable Long id) {
        log.info("根据ID获取用户信息，ID: {}", id);
        Users user = userService.findById(id);
        return Result.success(user);
    }

    /**
     * 创建新用户
     * POST /user - 管理员接口，直接添加用户到系统中
     * 
     * @param user 用户信息
     * @return 操作结果
     */
    @PostMapping
    public Result<String> createUser(@Valid @RequestBody Users user) {
        log.info("管理员创建用户: {}", user.getUsername());
        userService.addUser(user);
        return Result.success("用户创建成功");
    }

    /**
     * 更新用户信息
     * PUT /user/{id} - 根据ID更新用户信息
     * 
     * @param id 用户ID
     * @param user 更新的用户信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public Result<String> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody Users user) {
        log.info("更新用户信息，ID: {}, 用户名: {}", id, user.getUsername());
        user.setId(id); // 确保ID一致
        userService.updateUser(user);
        return Result.success("用户信息更新成功");
    }

    /**
     * 删除用户
     * DELETE /user/{id} - 根据用户ID删除用户
     * 
     * @param id 用户ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(
            @PathVariable Long id) {
        log.info("删除用户，ID: {}", id);
        userService.deleteById(id);
        return Result.success("用户删除成功");
    }

}
