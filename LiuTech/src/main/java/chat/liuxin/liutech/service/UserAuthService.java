package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.req.ChangePasswordReq;
import chat.liuxin.liutech.resp.UserResp;
import chat.liuxin.liutech.resp.LoginResp;
import chat.liuxin.liutech.utils.JwtUtil;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 用户认证服务类
 * 专门处理用户登录、注册、密码相关功能
 *
 * @author 刘鑫
 * @date 2025-08-30
 */
@Slf4j
@Service
public class UserAuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserUtils userUtils;

    /**
     * 密码加密器，使用BCrypt算法进行密码加密
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     * 验证用户信息并创建新用户账户
     *
     * @param registerReq 注册请求参数
     * @return 注册成功的用户信息（脱敏后）
     * @throws BusinessException 当用户名已存在或邮箱已被注册时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public UserResp register(RegisterReq registerReq) {
        log.info("开始用户注册流程，用户名: {}", registerReq.getUsername());

        // 1. 验证用户名和邮箱是否已存在
        validateUserNotExists(registerReq.getUsername(), registerReq.getEmail());

        // 2. 创建用户对象
        Users user = createUserFromRegisterReq(registerReq);

        // 3. 保存用户到数据库
        saveUserWithExceptionHandling(user);

        // 4. 转换为响应对象
        return convertToUserResl(user);
    }

    /**
     * 验证用户名和邮箱是否已存在
     * 在用户注册前检查用户名和邮箱的唯一性
     *
     * @param username 用户名
     * @param email 邮箱地址
     * @throws BusinessException 当用户名或邮箱已存在时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    private void validateUserNotExists(String username, String email) {
        // 检查用户名是否已存在
        List<Users> existingUsers = userMapper.findByUserName(username);
        if (existingUsers != null && !existingUsers.isEmpty()) {
            log.warn("注册失败，用户名已存在: {}", username);
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 检查邮箱是否已被注册（如果提供了邮箱）
        if (StringUtils.hasText(email)) {
            List<Users> existingEmailUsers = userMapper.findByEmail(email);
            if (existingEmailUsers != null && !existingEmailUsers.isEmpty()) {
                log.warn("注册失败，邮箱已被注册: {}", email);
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }
    }

    /**
     * 从注册请求创建用户对象
     * 根据注册请求参数创建新用户，设置默认值并加密密码
     *
     * @param registerReq 注册请求参数
     * @return 创建的用户对象
     * @author 刘鑫
     * @date 2025-01-30
     */
    private Users createUserFromRegisterReq(RegisterReq registerReq) {
        Users user = new Users();
        user.setUsername(registerReq.getUsername());
        user.setEmail(registerReq.getEmail());
        user.setPasswordHash(passwordEncoder.encode(registerReq.getPassword()));
        user.setStatus(1); // 默认激活状态
        user.setPoints(BigDecimal.ZERO); // 初始积分为0

        Date now = new Date();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return user;
    }

    /**
     * 保存用户并处理异常
     * 将新用户信息保存到数据库，包含异常处理
     *
     * @param user 用户对象
     * @throws BusinessException 当保存失败时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    private void saveUserWithExceptionHandling(Users user) {
        try {
            userMapper.insert(user);
            log.info("用户注册成功，用户名: {}, ID: {}", user.getUsername(), user.getId());
        } catch (DuplicateKeyException e) {
            handleDuplicateKeyException(e);
        } catch (Exception e) {
            log.error("用户注册失败，数据库操作异常: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，请稍后重试");
        }
    }

    /**
     * 处理数据库唯一约束违反异常
     */
    private void handleDuplicateKeyException(DuplicateKeyException e) {
        String errorMessage = e.getMessage();
        log.error("用户注册失败，数据库约束违反: {}", errorMessage, e);

        if (errorMessage.contains("username")) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        } else if (errorMessage.contains("email")) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        } else {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据重复");
        }
    }

    /**
     * 转换为用户响应对象
     * 将用户实体转换为响应对象，过滤敏感信息
     *
     * @param user 用户实体
     * @return 用户响应对象（脱敏后）
     * @author 刘鑫
     * @date 2025-01-30
     */
    private UserResp convertToUserResl(Users user) {
        UserResp userResp = new UserResp();
        BeanUtils.copyProperties(user, userResp);
        return userResp;
    }

    /**
     * 用户登录
     * 验证用户凭据并返回JWT token
     *
     * @param loginReq 登录请求参数
     * @return 包含JWT token的登录响应
     * @throws BusinessException 当用户名或密码错误、账户被禁用时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResp login(LoginReq loginReq) {
        log.info("用户登录尝试，用户名: {}", loginReq.getUsername());

        // 依赖说明：查询与密码校验依赖 UserMapper/BCrypt；生成token依赖 JwtUtil
        // 授权说明：后续请求由 JwtAuthenticationFilter 解析 token 并注入 Authentication

        // 1. 查询并验证用户（存在且状态为启用）
        Users user = validateUserForLogin(loginReq);

        // 2. 验证密码（BCrypt匹配）
        validatePassword(loginReq.getPassword(), user);

        // 3. 更新最后登录时间（尽力而为，失败不影响登录）
        updateLastLoginTime(user);

        // 4. 生成并返回JWT token（claims: userId/username/passwordHash）
        return generateLoginResponse(user);
    }

    /**
     * 验证用户登录信息
     * 检查用户是否存在以及账户状态是否正常
     *
     * @param loginReq 登录请求参数
     * @return 验证通过的用户对象
     * @throws BusinessException 当用户不存在或账户被禁用时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    private Users validateUserForLogin(LoginReq loginReq) {
        List<Users> users = userMapper.findByUserName(loginReq.getUsername());
        if (users == null || users.isEmpty()) {
            log.warn("登录失败，用户不存在: {}", loginReq.getUsername());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        Users user = users.get(0);
        if (user.getStatus() != 1) {
            log.warn("登录失败，账户已被禁用: {}", loginReq.getUsername());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        return user;
    }

    /**
     * 验证密码
     * 使用BCrypt算法验证用户输入的密码是否正确
     *
     * @param inputPassword 用户输入的密码
     * @param user 用户对象
     * @throws BusinessException 当密码错误时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    private void validatePassword(String inputPassword, Users user) {
        if (!passwordEncoder.matches(inputPassword, user.getPasswordHash())) {
            log.warn("登录失败，密码错误: {}", user.getUsername());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
    }

    /**
     * 更新最后登录时间
     * 记录用户最后一次登录的时间，用于统计和安全审计
     *
     * @param user 用户对象
     * @author 刘鑫
     * @date 2025-01-30
     */
    private void updateLastLoginTime(Users user) {
        try {
            Date now = new Date();
            user.setLastLoginAt(now);
            user.setUpdatedAt(now);
            userMapper.updateById(user);
            log.info("用户登录成功: {}", user.getUsername());
        } catch (Exception e) {
            log.error("更新登录时间失败: {}", e.getMessage(), e);
            // 登录成功但更新时间失败，不影响登录流程
        }
    }

    /**
     * 生成登录响应
     * 创建JWT token并构建登录成功的响应对象
     *
     * @param user 用户对象
     * @return 登录响应，包含JWT token和用户信息
     * @author 刘鑫
     * @date 2025-01-30
     */
    private LoginResp generateLoginResponse(Users user) {
        // 生成访问令牌：当前策略将 passwordHash 放入claims用于失效校验
        // 建议后续替换为 tokenVersion 或 lastPasswordChangeAt，避免敏感信息进token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getPasswordHash());
        log.info("为用户 {} 生成JWT token成功", user.getUsername());

        LoginResp loginResp = new LoginResp();
        loginResp.setToken(token);
        return loginResp;
    }

    /**
     * 修改当前用户密码
     * 从Spring Security上下文中获取认证用户信息并修改密码
     *
     * @param changePasswordReq 修改密码请求参数
     * @throws BusinessException 当验证失败或修改失败时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePasswordWithAuth(ChangePasswordReq changePasswordReq) {
        log.info("开始修改当前用户密码");

        // 1. 验证密码一致性
        if (!changePasswordReq.isPasswordMatch()) {
            log.warn("新密码和确认密码不一致");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码和确认密码不一致");
        }

        // 2. 获取当前用户信息
        Users currentUser = getCurrentUserForPasswordChange();

        // 3. 修改密码
        changePassword(currentUser.getId(), currentUser.getUsername(), currentUser.getPasswordHash(),
                changePasswordReq.getOldPassword(), changePasswordReq.getNewPassword());

        log.info("用户 {} 密码修改成功", currentUser.getUsername());
    }

    /**
     * 获取当前用户信息用于密码修改
     */
    private Users getCurrentUserForPasswordChange() {
        Users currentUser = userUtils.getCurrentUser();
        if (currentUser == null) {
            log.warn("无法获取当前用户信息");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        return currentUser;
    }

    /**
     * 修改用户密码
     * 使用JWT token中的信息验证用户身份并修改密码
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param tokenPasswordHash token中存储的密码哈希
     * @param oldPassword 用户输入的原密码
     * @param newPassword 新密码
     * @throws BusinessException 当验证失败或修改失败时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String username, String tokenPasswordHash,
                              String oldPassword, String newPassword) {
        log.info("开始修改用户密码，用户ID: {}, 用户名: {}", userId, username);

        // 1. 验证用户信息
        Users currentUser = validateUserForPasswordChange(userId, username, tokenPasswordHash);

        // 2. 验证原密码
        validateOldPassword(oldPassword, currentUser);

        // 3. 验证新密码
        validateNewPassword(newPassword, currentUser);

        // 4. 更新密码
        updateUserPassword(currentUser, newPassword);

        log.info("用户 {} 密码修改成功", username);
    }

    /**
     * 验证用户信息用于密码修改
     */
    private Users validateUserForPasswordChange(Long userId, String username, String tokenPasswordHash) {
        Users currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            log.warn("用户不存在，ID: {}", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!currentUser.getUsername().equals(username)) {
            log.warn("用户名不匹配，token中: {}, 数据库中: {}", username, currentUser.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户信息不匹配");
        }

        if (!currentUser.getPasswordHash().equals(tokenPasswordHash)) {
            log.warn("token中的密码哈希与数据库不匹配，可能token已过期或密码已被修改");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "token已失效，请重新登录");
        }

        return currentUser;
    }

    /**
     * 验证原密码
     */
    private void validateOldPassword(String oldPassword, Users currentUser) {
        if (!passwordEncoder.matches(oldPassword, currentUser.getPasswordHash())) {
            log.warn("原密码验证失败，用户: {}", currentUser.getUsername());
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "原密码错误");
        }
    }

    /**
     * 验证新密码
     */
    private void validateNewPassword(String newPassword, Users currentUser) {
        if (passwordEncoder.matches(newPassword, currentUser.getPasswordHash())) {
            log.warn("新密码与原密码相同，用户: {}", currentUser.getUsername());
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能与原密码相同");
        }
    }

    /**
     * 更新用户密码
     */
    private void updateUserPassword(Users currentUser, String newPassword) {
        try {
            String encodedNewPassword = passwordEncoder.encode(newPassword);
            currentUser.setPasswordHash(encodedNewPassword);
            currentUser.setUpdatedAt(new Date());
            userMapper.updateById(currentUser);
        } catch (Exception e) {
            log.error("密码更新失败，用户ID: {}, 错误: {}", currentUser.getId(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "密码更新失败");
        }
    }
}
