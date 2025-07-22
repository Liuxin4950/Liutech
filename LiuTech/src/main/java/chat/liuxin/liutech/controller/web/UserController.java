package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.service.UserService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
     * 验证用户凭据并返回用户信息
     * 
     * @param loginReq 登录请求参数，包含用户名和密码
     * @return 登录成功的用户信息（脱敏后）
     */
    @PostMapping("/login")
    public Result<UserResl> login(@Valid @RequestBody LoginReq loginReq) {
        log.info("收到用户登录请求，用户名: {}", loginReq.getUsername());
        UserResl userResl = userService.login(loginReq);
        log.info("用户登录成功，用户名: {}", loginReq.getUsername());
        return Result.success("登录成功", userResl);
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
