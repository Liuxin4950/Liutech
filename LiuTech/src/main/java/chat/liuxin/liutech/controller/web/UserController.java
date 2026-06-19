package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.req.ChangePasswordReq;
import chat.liuxin.liutech.req.UpdateProfileReq;
import chat.liuxin.liutech.req.ForgotPasswordReq;
import chat.liuxin.liutech.req.ResetPasswordReq;
import chat.liuxin.liutech.req.EmailLoginReq;
import chat.liuxin.liutech.req.EmailLoginVerifyReq;
import chat.liuxin.liutech.resp.UserResp;
import chat.liuxin.liutech.resp.LoginResp;
import chat.liuxin.liutech.resp.ProfileResp;
import chat.liuxin.liutech.service.UserAuthService;
import chat.liuxin.liutech.service.UserProfileService;
import chat.liuxin.liutech.service.UserManagementService;
import chat.liuxin.liutech.service.VerificationCodeService;
import chat.liuxin.liutech.utils.UserUtils;
import chat.liuxin.liutech.utils.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


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

    // 依赖说明：
    // - 应用服务：UserAuthService（认证流程：注册/登录/改密）、UserProfileService（资料与统计）、UserManagementService（用户管理）
    // - 会话获取：UserUtils（从SecurityContext获取当前用户）
    // 安全说明：
    // - 根据 SecurityConfig：/user/register、/user/login 允许匿名，其余接口默认需要JWT认证
    // - JwtAuthenticationFilter 在每次请求中解析JWT并注入 Authentication，供 UserUtils 与 @PreAuthorize 使用
    // - 管理员接口建议使用 /admin/users 控制器（已加 @PreAuthorize('hasRole('ADMIN')')），本控制器的管理类接口默认仅需认证

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private UserUtils userUtils;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    /**
     * 用户注册接口
     * 创建新用户账户，包括用户名唯一性检查、邮箱唯一性检查、密码加密等
     *
     * @param registerReq 注册请求参数，包含用户名、邮箱、密码等信息
     * @return 注册成功的用户信息（脱敏后）
     */
    @PostMapping("/register")
    public Result<UserResp> register(@Valid @RequestBody RegisterReq registerReq) {
        // 安全：匿名可调用；入参校验由 @Valid 驱动
        log.info("收到用户注册请求，用户名: {}", registerReq.getUsername());
        UserResp userResp = userAuthService.register(registerReq);
        log.info("用户注册成功，用户名: {}", registerReq.getUsername());
        return Result.success("注册成功", userResp);
    }

    /**
     * 注册 - 发送邮箱验证码
     */
    @PostMapping("/register/send-code")
    public Result<String> sendRegisterCode(@Valid @RequestBody ForgotPasswordReq req) {
        log.info("收到注册验证码请求，邮箱: {}", req.getEmail());
        verificationCodeService.sendCode(req.getEmail(), "REGISTER", "注册验证");
        return Result.success("验证码已发送到您的邮箱");
    }

    /**
     * 用户登录接口
     * 验证用户凭据并返回JWT token
     *
     * @param loginReq 登录请求参数，包含用户名和密码
     * @return 包含JWT token的登录响应，客户端需要保存token用于后续API调用
     */
    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq loginReq) {
        // 安全：匿名可调用；返回 JWT token，前端保存并放入 Authorization: Bearer {token}
        log.info("收到用户登录请求，用户名: {}", loginReq.getUsername());
        LoginResp loginResp = userAuthService.login(loginReq);
        log.info("用户登录成功，用户名: {}", loginReq.getUsername());
        return Result.success("登录成功", loginResp);
    }

    /**
     * 获取当前用户信息接口
     * 从Spring Security上下文中获取认证用户信息
     *
     * @return 当前用户信息（脱敏后）
     */
    @GetMapping("/current")
    public Result<UserResp> getCurrentUser() {
        // 安全：默认需认证；依赖 JwtAuthenticationFilter 提供的 Authentication，UserUtils 获取当前用户
        log.info("收到获取当前用户信息请求");
        Users currentUser = userUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        UserResp userResp = new UserResp();
        BeanUtils.copyProperties(currentUser, userResp);
        userResp.setPasswordHash(null);
        log.info("获取当前用户信息成功");
        return Result.success("获取用户信息成功", userResp);
    }

    /**
     * 修改密码接口
     * 从Spring Security上下文中获取认证用户信息
     *
     * @param changePasswordReq 修改密码请求参数
     * @return 修改结果
     */
    @PutMapping("/password")
    @OperationLog(action = "update", targetType = "user", description = "修改密码")
    public Result<String> changePassword(@Valid @RequestBody ChangePasswordReq changePasswordReq) {
        // 安全：默认需认证；UserAuthService.changePasswordWithAuth 内部使用 UserUtils 获取当前用户并完成改密
        log.info("收到修改密码请求");
        userAuthService.changePasswordWithAuth(changePasswordReq);
        log.info("密码修改成功");
        return Result.success("密码修改成功");
    }

    /**
     * 更新个人资料接口
     * 用户更新自己的个人信息
     *
     * @param updateProfileReq 更新资料请求参数
     * @return 更新后的用户信息
     */
    @PutMapping("/profile")
    @OperationLog(action = "update", targetType = "user", description = "更新个人资料")
    public Result<UserResp> updateProfile(@Valid @RequestBody UpdateProfileReq updateProfileReq) {
        // 安全：默认需认证；Profile 更新由 UserProfileService 完成并返回脱敏信息
        log.info("收到更新个人资料请求");
        UserResp userResp = userProfileService.updateProfile(updateProfileReq);
        log.info("个人资料更新成功");
        return Result.success("个人资料更新成功", userResp);
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
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> getUsers(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String username) {
        log.info("收到获取用户信息请求，ID: {}, 用户名: {}", id, username);
        Object result = userManagementService.getUsersByCondition(id, username);
        log.info("获取用户信息成功");
        return Result.success(result);
    }

    /**
     * 根据ID获取单个用户
     * GET /user/{id}
     *
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserResp> getUserById(
            @PathVariable Long id) {
        log.info("根据ID获取用户信息，ID: {}", id);
        Users user = userManagementService.findUserById(id);
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        UserResp userResp = new UserResp();
        BeanUtils.copyProperties(user, userResp);
        userResp.setPasswordHash(null);
        return Result.success(userResp);
    }

    /**
     * 创建新用户
     * POST /user - 管理员接口，直接添加用户到系统中
     *
     * @param user 用户信息
     * @return 操作结果
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "create", targetType = "user", description = "创建用户")
    public Result<String> createUser(@Valid @RequestBody Users user) {
        // 安全：建议改用 /admin/users 控制器（具备管理员校验）；此接口在当前配置下仅需认证，无角色校验
        log.info("管理员创建用户: {}", user.getUsername());
        userManagementService.addUser(user);
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
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody Users user) {
        // 安全：建议改用 /admin/users 控制器（具备管理员校验）；此接口在当前配置下仅需认证，无角色校验
        log.info("更新用户信息，ID: {}, 用户名: {}", id, user.getUsername());
        user.setId(id); // 确保ID一致
        userManagementService.updateUser(user);
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
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteUser(
            @PathVariable Long id) {
        // 安全：建议改用 /admin/users 控制器（具备管理员校验）；此接口在当前配置下仅需认证，无角色校验
        log.info("删除用户，ID: {}", id);
        userManagementService.removeUserById(id);
        return Result.success("用户删除成功");
    }

    /**
     * 获取当前用户统计信息
     * 包括评论数量、文章数量、积分等统计数据
     *
     * @return 用户统计信息
     */
    @GetMapping("/stats")
    public Result<?> getUserStats() {
        // 安全：默认需认证；统计信息由 UserProfileService 聚合
        log.info("收到获取用户统计信息请求");
        Object stats = userProfileService.getCurrentUserStats();
        log.info("获取用户统计信息成功");
        return Result.success("获取统计信息成功", stats);
    }

    /**
     * 获取个人资料接口
     * 用于首页个人信息卡片展示
     *
     * @return 个人资料信息
     */
    @GetMapping("/profile")
    public Result<ProfileResp> getProfile() {
        // 安全：此接口一般允许认证用户调用，返回当前用户资料卡片
        log.info("收到获取个人资料请求");
        ProfileResp profile = userProfileService.getProfile();
        log.info("获取个人资料成功");
        return Result.success("获取个人资料成功", profile);
    }

    /**
     * 获取个人资料接口
     * 用于首页个人信息卡片展示
     *
     * @return 个人资料信息
     */
    @GetMapping("/author/profile")
    public Result<ProfileResp> getAuthorProfile() {
        // 安全：此接口通常为公开展示，可按需在 SecurityConfig 白名单中放行
        log.info("收到获取网站作者资料请求");
        ProfileResp profile = userProfileService.getDefaultProfile();
        log.info("获取网站作者资料成功");
        return Result.success("获取网站作者资料成功", profile);
    }

    // ==================== 忘记密码 ====================

    /**
     * 忘记密码 - 发送验证码
     * 用户输入注册邮箱，系统发送6位验证码
     */
    @PostMapping("/forgot-password")
    public Result<String> forgotPassword(@Valid @RequestBody ForgotPasswordReq req) {
        log.info("收到忘记密码请求，邮箱: {}", req.getEmail());
        // 邮箱未注册时也返回成功（防止邮箱枚举攻击），但不实际发送邮件
        List<Users> users = userManagementService.findUsersByEmail(req.getEmail());
        if (users != null && !users.isEmpty()) {
            verificationCodeService.sendCode(req.getEmail(), "FORGOT_PASSWORD", "忘记密码");
        }
        return Result.success("如果该邮箱已注册，验证码已发送到您的邮箱");
    }

    /**
     * 忘记密码 - 重置密码
     * 验证码校验通过后设置新密码
     */
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        log.info("收到重置密码请求，邮箱: {}", req.getEmail());
        // 校验验证码
        chat.liuxin.liutech.model.VerificationCode vc =
            verificationCodeService.verifyCode(req.getEmail(), "FORGOT_PASSWORD", req.getCode());
        // 查找用户并更新密码
        List<Users> users = userManagementService.findUsersByEmail(req.getEmail());
        if (users == null || users.isEmpty()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        Users user = users.get(0);
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedAt(new java.util.Date());
        userManagementService.updateUser(user);
        // 业务成功后标记验证码已使用
        verificationCodeService.markUsed(vc.getId());
        return Result.success("密码重置成功，请使用新密码登录");
    }

    // ==================== 邮箱验证码登录 ====================

    /**
     * 邮箱登录 - 发送验证码
     * 用户输入邮箱，系统发送6位登录验证码
     */
    @PostMapping("/login/email/send")
    public Result<String> sendEmailLoginCode(@Valid @RequestBody EmailLoginReq req) {
        log.info("收到邮箱登录验证码请求，邮箱: {}", req.getEmail());
        // 邮箱未注册时也返回成功（防止邮箱枚举攻击），但不实际发送邮件
        List<Users> users = userManagementService.findUsersByEmail(req.getEmail());
        if (users != null && !users.isEmpty()) {
            verificationCodeService.sendCode(req.getEmail(), "EMAIL_LOGIN", "邮箱登录");
        }
        return Result.success("如果该邮箱已注册，验证码已发送到您的邮箱");
    }

    /**
     * 邮箱登录 - 验证码校验登录
     * 验证码正确后返回JWT token
     */
    @PostMapping("/login/email/verify")
    public Result<LoginResp> verifyEmailLogin(@Valid @RequestBody EmailLoginVerifyReq req) {
        log.info("收到邮箱验证码登录请求，邮箱: {}", req.getEmail());
        // 校验验证码
        chat.liuxin.liutech.model.VerificationCode vc =
            verificationCodeService.verifyCode(req.getEmail(), "EMAIL_LOGIN", req.getCode());
        // 查找用户
        List<Users> users = userManagementService.findUsersByEmail(req.getEmail());
        if (users == null || users.isEmpty()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        Users user = users.get(0);
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        // 生成JWT token
        String role = user.getRole() != null ? user.getRole() : "user";
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), role, user.getPasswordHash());
        // 更新最后登录时间
        user.setLastLoginAt(new java.util.Date());
        user.setUpdatedAt(new java.util.Date());
        userManagementService.updateUser(user);
        // 业务成功后标记验证码已使用
        verificationCodeService.markUsed(vc.getId());
        return Result.success("登录成功", new LoginResp(token));
    }






}
