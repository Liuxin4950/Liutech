package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "用户管理", description = "用户相关的API接口，包括注册、登录、用户CRUD操作")
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
    @Operation(summary = "用户注册", description = "创建新用户账户，包括用户名唯一性检查、邮箱唯一性检查、密码加密等")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "注册成功"),
            @ApiResponse(responseCode = "400", description = "参数错误或用户名/邮箱已存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
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
    @Operation(summary = "用户登录", description = "验证用户凭据并返回用户信息，支持用户名和密码登录")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功"),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "用户名或密码错误"),
            @ApiResponse(responseCode = "403", description = "账户被禁用"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
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
    @Operation(summary = "获取用户信息", description = "根据参数获取用户信息：无参数获取所有用户，id参数获取单个用户，username参数模糊查询用户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @GetMapping
    public Result<?> getUsers(
            @Parameter(description = "用户ID，可选参数") @RequestParam(required = false) Long id,
            @Parameter(description = "用户名，支持模糊查询，可选参数") @RequestParam(required = false) String username) {
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
    @Operation(summary = "根据ID获取用户", description = "通过用户ID获取单个用户的详细信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @GetMapping("/{id}")
    public Result<Users> getUserById(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id) {
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
    @Operation(summary = "创建用户", description = "管理员接口，直接创建新用户到系统中")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户创建成功"),
            @ApiResponse(responseCode = "400", description = "参数错误或用户已存在"),
            @ApiResponse(responseCode = "403", description = "权限不足"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
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
    @Operation(summary = "更新用户信息", description = "根据用户ID更新用户的详细信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户信息更新成功"),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "403", description = "权限不足"),
            @ApiResponse(responseCode = "404", description = "用户不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PutMapping("/{id}")
    public Result<String> updateUser(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id,
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
    @Operation(summary = "删除用户", description = "根据用户ID删除指定用户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户删除成功"),
            @ApiResponse(responseCode = "403", description = "权限不足"),
            @ApiResponse(responseCode = "404", description = "用户不存在"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id) {
        log.info("删除用户，ID: {}", id);
        userService.deleteById(id);
        return Result.success("用户删除成功");
    }

}
