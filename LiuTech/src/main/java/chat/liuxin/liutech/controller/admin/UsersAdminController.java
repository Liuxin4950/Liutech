package chat.liuxin.liutech.controller.admin;

import lombok.RequiredArgsConstructor;
import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.UserResp;
import chat.liuxin.liutech.service.UserManagementService;
import chat.liuxin.liutech.utils.ValidationUtil;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端用户控制器（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UsersAdminController extends BaseAdminController {

    private final UserManagementService userManagementService;

    /** 分页查询用户列表 */
    @GetMapping
    public Result<PageResp<UserResp>> getUserList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        ValidationUtil.validateRange(page, "页码", 1, Integer.MAX_VALUE);
        ValidationUtil.validateRange(size, "页面大小", 1, 100);
        return Result.success(userManagementService.getUserListForAdmin(page, size, username, email, role, status, includeDeleted));
    }

    /** 根据ID查询用户详情 */
    @GetMapping("/{id}")
    public Result<Users> getUserById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "用户ID");
        Users user = userManagementService.findUserById(id);
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }
        // 不返回密码等敏感信息
        user.setPasswordHash(null);
        return Result.success(user);
    }

    /** 创建用户 */
    @PostMapping
    @OperationLog(action = "create", targetType = "user", description = "创建用户: #user.username", targetName = "#user.username")
    public Result<String> createUser(@RequestBody Users user) {
        ValidationUtil.validateNotNull(user, "用户信息");
        ValidationUtil.validateUsername(user.getUsername());
        ValidationUtil.validateEmail(user.getEmail());
        return handleOperationResult(userManagementService.saveUser(user), "用户创建成功", "用户创建");
    }

    /** 更新用户信息 */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "user", description = "更新用户信息: #user.username", targetName = "#user.username")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody Users user) {
        ValidationUtil.validateId(id, "用户ID");
        ValidationUtil.validateNotNull(user, "用户信息");
        ValidationUtil.validateEmail(user.getEmail());
        user.setId(id);
        preservePasswordIfEmpty(user, id);
        return handleOperationResult(userManagementService.updateUserById(user), "用户更新成功", "用户更新");
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "user", description = "删除用户", targetName = "#id")
    public Result<String> deleteUser(@PathVariable Long id) {
        ValidationUtil.validateId(id, "用户ID");
        return handleOperationResult(userManagementService.removeUserById(id), "用户删除成功", "用户删除");
    }

    /** 批量删除用户 */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "user", description = "批量删除用户")
    public Result<String> batchDeleteUsers(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "用户ID列表");
        return handleOperationResult(userManagementService.removeUsersByIds(ids), "批量删除用户成功", "批量删除用户");
    }

    /** 启用/禁用用户 */
    @PutMapping("/{id}/status")
    @OperationLog(action = "disable", targetType = "user", description = "#enabled ? '启用用户' : '禁用用户'", targetName = "#id")
    public Result<String> updateUserStatus(@PathVariable Long id, @RequestParam Boolean enabled) {
        ValidationUtil.validateId(id, "用户ID");
        ValidationUtil.validateNotNull(enabled, "用户状态");
        Users user = buildUserStatusUpdate(id, enabled);
        boolean success = userManagementService.updateUserById(user);
        return handleOperationResult(success, enabled ? "用户已启用" : "用户已禁用", "用户状态更新");
    }

    /** 批量启用/禁用用户 */
    @PutMapping("/batch/status")
    @OperationLog(action = "disable", targetType = "user", description = "#enabled ? '批量启用用户' : '批量禁用用户'")
    public Result<String> batchUpdateUserStatus(@RequestBody List<Long> ids, @RequestParam Boolean enabled) {
        ValidationUtil.validateNotEmpty(ids, "用户ID列表");
        ValidationUtil.validateNotNull(enabled, "用户状态");
        boolean success = userManagementService.batchUpdateUserStatus(ids, enabled);
        return handleOperationResult(success, enabled ? "批量启用用户成功" : "批量禁用用户成功", "批量用户状态更新");
    }

    /** 恢复已删除的用户 */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "user", description = "恢复用户", targetName = "#id")
    public Result<String> restoreUser(@PathVariable Long id) {
        ValidationUtil.validateId(id, "用户ID");
        return handleOperationResult(userManagementService.restoreUser(id), "用户恢复成功", "用户恢复");
    }

    /** 批量恢复已删除的用户 */
    @PutMapping("/batch/restore")
    @OperationLog(action = "restore", targetType = "user", description = "批量恢复用户")
    public Result<String> batchRestoreUsers(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "用户ID列表");
        return handleOperationResult(userManagementService.restoreUsers(ids), "批量恢复用户成功", "批量恢复用户");
    }

    /** 彻底删除用户（物理删除） */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "user", description = "彻底删除用户", targetName = "#id")
    public Result<String> permanentDeleteUser(@PathVariable Long id) {
        ValidationUtil.validateId(id, "用户ID");
        return handleOperationResult(userManagementService.permanentDeleteUser(id), "用户彻底删除成功", "用户彻底删除");
    }

    /** 批量彻底删除用户（物理删除） */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "user", description = "批量彻底删除用户")
    public Result<String> batchPermanentDeleteUsers(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "用户ID列表");
        return handleOperationResult(userManagementService.batchPermanentDeleteUsers(ids), "批量彻底删除用户成功", "批量彻底删除用户");
    }

    /** 如果密码为空则保留原密码 */
    private void preservePasswordIfEmpty(Users user, Long id) {
        if (user.getPasswordHash() == null || user.getPasswordHash().trim().isEmpty()) {
            Users existingUser = userManagementService.findUserById(id);
            if (existingUser != null) {
                user.setPasswordHash(existingUser.getPasswordHash());
            }
        }
    }

    /** 构建用户状态更新对象 */
    private Users buildUserStatusUpdate(Long id, Boolean enabled) {
        Users user = new Users();
        user.setId(id);
        user.setStatus(enabled ? 1 : 0);
        return user;
    }
}
