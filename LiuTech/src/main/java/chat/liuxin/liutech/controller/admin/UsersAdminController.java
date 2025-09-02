package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.service.UserManagementService;
import chat.liuxin.liutech.utils.ValidationUtil;
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
@RequestMapping("/admin/users")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('ADMIN')")
public class UsersAdminController extends BaseAdminController {

    @Autowired
    private UserManagementService userManagementService;

    /**
     * 分页查询用户列表
     * 
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param username 用户名（可选，模糊搜索）
     * @param email 邮箱（可选，模糊搜索）
     * @param status 用户状态（可选，0禁用，1启用）
     * @param includeDeleted 是否包含已删除用户（可选，true包含，false不包含，默认false）
     * @return 分页用户列表
     */
    @GetMapping
    public Result<PageResl<UserResl>> getUserList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        
        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);
        
        try {
            PageResl<UserResl> result = userManagementService.getUserListForAdmin(page, size, username, email, status, includeDeleted);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询用户列表");
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
        ValidationUtil.validateId(id, "用户ID");
        try {
            Users user = userManagementService.findUserById(id);
            if (user == null) {
                return Result.fail(ErrorCode.USER_NOT_FOUND);
            }
            // 不返回密码等敏感信息
            user.setPasswordHash(null);
            return Result.success(user);
        } catch (Exception e) {
            return handleException(e, "查询用户详情");
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
        ValidationUtil.validateNotNull(user, "用户信息");
        ValidationUtil.validateUsername(user.getUsername());
        ValidationUtil.validateEmail(user.getEmail());
        
        try {
            boolean success = userManagementService.saveUser(user);
            return handleOperationResult(success, "用户创建成功", "用户创建");
        } catch (Exception e) {
            return handleException(e, "用户创建");
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
        ValidationUtil.validateId(id, "用户ID");
        ValidationUtil.validateNotNull(user, "用户信息");
        ValidationUtil.validateEmail(user.getEmail());
        
        try {
            user.setId(id);
            preservePasswordIfEmpty(user, id);
            boolean success = userManagementService.updateUserById(user);
            return handleOperationResult(success, "用户更新成功", "用户更新");
        } catch (Exception e) {
            return handleException(e, "用户更新");
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
        ValidationUtil.validateId(id, "用户ID");
        try {
            boolean success = userManagementService.removeUserById(id);
            return handleOperationResult(success, "用户删除成功", "用户删除");
        } catch (Exception e) {
            return handleException(e, "用户删除");
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
        ValidationUtil.validateNotEmpty(ids, "用户ID列表");
        try {
            boolean success = userManagementService.removeUsersByIds(ids);
            return handleOperationResult(success, "批量删除用户成功", "批量删除用户");
        } catch (Exception e) {
            return handleException(e, "批量删除用户");
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
        ValidationUtil.validateId(id, "用户ID");
        ValidationUtil.validateNotNull(enabled, "用户状态");
        
        try {
            Users user = buildUserStatusUpdate(id, enabled);
            boolean success = userManagementService.updateUserById(user);
            String message = enabled ? "用户已启用" : "用户已禁用";
            return handleOperationResult(success, message, "用户状态更新");
        } catch (Exception e) {
            return handleException(e, "用户状态更新");
        }
    }
    

    

    
    /**
     * 如果密码为空则保留原密码
     * @param user 用户对象
     * @param id 用户ID
     */
    private void preservePasswordIfEmpty(Users user, Long id) {
        if (user.getPasswordHash() == null || user.getPasswordHash().trim().isEmpty()) {
            Users existingUser = userManagementService.findUserById(id);
            if (existingUser != null) {
                user.setPasswordHash(existingUser.getPasswordHash());
            }
        }
    }
    
    /**
     * 构建用户状态更新对象
     * @param id 用户ID
     * @param enabled 是否启用
     * @return 用户对象
     */
    private Users buildUserStatusUpdate(Long id, Boolean enabled) {
        Users user = new Users();
        user.setId(id);
        user.setStatus(enabled ? 1 : 0);
        return user;
    }
}