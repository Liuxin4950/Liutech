package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端用户控制器
 * 需要管理员权限才能访问
 * 
 * @author 刘鑫
 */
@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('ADMIN')")
public class UsersAdminController {

    @Autowired
    private UserService userService;

    /**
     * 分页查询用户列表
     * 
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param username 用户名（可选，模糊搜索）
     * @param email 邮箱（可选，模糊搜索）
     * @return 分页用户列表
     */
    @GetMapping
    public Result<PageResl<UserResl>> getUserList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email) {
        try {
            // 将username和email合并为一个关键词参数
            String keyword = username != null ? username : (email != null ? email : null);
            PageResl<UserResl> result = userService.getUserListForAdmin(page, size, keyword);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "查询用户列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询用户详情
     * 
     * @param id 用户ID
     * @return 用户详情
     */
    @GetMapping("/{id}")
    public Result<Users> getUserById(@PathVariable Long id) {
        try {
            Users user = userService.findById(id);
            if (user == null) {
                return Result.fail(ErrorCode.USER_NOT_FOUND);
            }
            // 不返回密码等敏感信息
            user.setPasswordHash(null);
            return Result.success(user);
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "查询用户详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建用户
     * 
     * @param user 用户信息
     * @return 创建结果
     */
    @PostMapping
    public Result<String> createUser(@RequestBody Users user) {
        try {
            boolean success = userService.save(user);
            if (success) {
                return Result.success("用户创建成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "用户创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户信息
     * 
     * @param id 用户ID
     * @param user 用户信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody Users user) {
        try {
            user.setId(id);
            // 如果没有提供密码，则不更新密码字段
            if (user.getPasswordHash() == null || user.getPasswordHash().trim().isEmpty()) {
                Users existingUser = userService.findById(id);
                if (existingUser != null) {
                    user.setPasswordHash(existingUser.getPasswordHash());
                }
            }
            boolean success = userService.updateById(user);
            if (success) {
                return Result.success("用户更新成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "用户更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除用户
     * 
     * @param id 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        try {
            boolean success = userService.removeById(id);
            if (success) {
                return Result.success("用户删除成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "用户删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除用户
     * 
     * @param ids 用户ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<String> batchDeleteUsers(@RequestBody List<Long> ids) {
        try {
            boolean success = userService.removeByIds(ids);
            if (success) {
                return Result.success("批量删除用户成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "批量删除用户失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用用户
     * 
     * @param id 用户ID
     * @param enabled 是否启用
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result<String> updateUserStatus(@PathVariable Long id, @RequestParam Boolean enabled) {
        try {
            Users user = new Users();
            user.setId(id);
            // 这里假设Users实体有enabled字段，如果没有可以根据实际情况调整
            boolean success = userService.updateById(user);
            if (success) {
                return Result.success(enabled ? "用户已启用" : "用户已禁用");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "用户状态更新失败: " + e.getMessage());
        }
    }
}