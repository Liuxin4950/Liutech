package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.req.UpdateProfileReq;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.resl.LoginResl;
import chat.liuxin.liutech.resl.UserStatsResl;
import chat.liuxin.liutech.resl.ProfileResl;
import chat.liuxin.liutech.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 用户服务类
 * 提供用户相关的业务逻辑处理，包括注册、登录、用户管理等功能
 * 
 * @author liuxin
 */
@Slf4j
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private CommentsService commentsService;
    
    @Autowired
    private PostsService postsService;

    /**
     * 密码加密器，使用BCrypt算法进行密码加密
     * BCrypt是一种安全的哈希算法，具有自适应性，可以抵御彩虹表攻击
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 查询所有用户
     * @return 用户列表
     */
    public List<Users> findAll() {
        return userMapper.selectList(null);
    }

    /**
     * 根据ID查询单个用户
     * @param id 用户ID
     * @return 用户信息
     * @throws BusinessException 当用户不存在时抛出
     */
    public Users findById(Long id) {
        Users user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 添加用户
     * @param user 用户信息
     */
    public void addUser(Users user) {
        userMapper.insert(user);
    }

    /**
     * 更新用户信息
     * @param user 用户信息
     */
    public void updateUser(Users user) {
        userMapper.updateById(user);
    }

    /**
     * 根据ID删除用户
     * @param id 用户ID
     */
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    /**
     * 根据用户名查询用户
     * @param userName 用户名
     * @return 用户列表
     */
    public List<Users> findByUserName(String userName) {
        return userMapper.findByUserName(userName);
    }
    
    /**
     * 根据邮箱查询用户
     * @param email 邮箱地址
     * @return 用户列表
     */
    public List<Users> findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    /**
     * 用户注册
     * 创建新用户账户，包括用户名唯一性检查、邮箱唯一性检查、密码加密等
     * 
     * @param registerReq 注册请求参数，包含用户名、邮箱、密码等信息
     * @return 注册成功的用户信息（脱敏后）
     * @throws BusinessException 当用户名已存在或邮箱已被注册时抛出
     */
    public UserResl register(RegisterReq registerReq) {
        log.info("开始用户注册流程，用户名: {}", registerReq.getUsername());
        
        // 1. 检查用户名是否已存在
        List<Users> existingUsers = findByUserName(registerReq.getUsername());
        if (existingUsers != null && !existingUsers.isEmpty()) {
            log.warn("注册失败，用户名已存在: {}", registerReq.getUsername());
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 2. 检查邮箱是否已被注册（如果提供了邮箱）
        if (StringUtils.hasText(registerReq.getEmail())) {
            List<Users> existingEmailUsers = findByEmail(registerReq.getEmail());
            if (existingEmailUsers != null && !existingEmailUsers.isEmpty()) {
                log.warn("注册失败，邮箱已被注册: {}", registerReq.getEmail());
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }

        // 3. 创建用户对象并设置属性
        Users user = new Users();
        user.setUsername(registerReq.getUsername());
        user.setEmail(registerReq.getEmail());
        user.setPasswordHash(passwordEncoder.encode(registerReq.getPassword())); // 使用BCrypt加密密码
        user.setStatus(1); // 默认激活状态
        user.setPoints(BigDecimal.ZERO); // 初始积分为0
        
        Date now = new Date();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // 4. 保存用户信息到数据库
        try {
            addUser(user);
            log.info("用户注册成功，用户名: {}, ID: {}", user.getUsername(), user.getId());
        } catch (DuplicateKeyException e) {
            // 处理数据库唯一约束违反异常
            String errorMessage = e.getMessage();
            log.error("用户注册失败，数据库约束违反: {}", errorMessage, e);
            
            if (errorMessage.contains("username")) {
                throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            } else if (errorMessage.contains("email")) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据重复");
            }
        } catch (Exception e) {
            log.error("用户注册失败，数据库操作异常: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，请稍后重试");
        }

        // 5. 转换为响应对象（不包含敏感信息）
        UserResl userResl = new UserResl();
        BeanUtils.copyProperties(user, userResl);
        
        return userResl;
    }

    /**
     * 用户登录
     * 验证用户凭据并返回JWT token
     * 
     * @param loginReq 登录请求参数，包含用户名和密码
     * @return 包含JWT token的登录响应
     * @throws BusinessException 当用户名或密码错误、账户被禁用时抛出
     */
    public LoginResl login(LoginReq loginReq) {
        log.info("用户登录尝试，用户名: {}", loginReq.getUsername());
        
        // 1. 查询用户信息
        List<Users> users = findByUserName(loginReq.getUsername());
        if (users == null || users.isEmpty()) {
            log.warn("登录失败，用户不存在: {}", loginReq.getUsername());
            // 为了安全考虑，不暴露用户是否存在的信息
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        Users user = users.get(0);
        
        // 2. 检查账户状态
        if (user.getStatus() != 1) {
            log.warn("登录失败，账户已被禁用: {}", loginReq.getUsername());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 3. 验证密码
        if (!passwordEncoder.matches(loginReq.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败，密码错误: {}", loginReq.getUsername());
            // 为了安全考虑，统一返回登录失败信息
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 4. 更新最后登录时间
        try {
            Date now = new Date();
            user.setLastLoginAt(now);
            user.setUpdatedAt(now);
            updateUser(user);
            log.info("用户登录成功: {}", loginReq.getUsername());
        } catch (Exception e) {
            log.error("更新登录时间失败: {}", e.getMessage(), e);
            // 登录成功但更新时间失败，不影响登录流程
        }

        // 5. 生成JWT token
        // token中包含用户ID、用户名和密码哈希值，用于后续身份验证和密码修改等操作
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getPasswordHash());
        log.info("为用户 {} 生成JWT token成功", loginReq.getUsername());
        
        // 6. 返回登录响应（只包含token，不包含用户敏感信息）
        LoginResl loginResl = new LoginResl();
        loginResl.setToken(token);
        return loginResl;
    }
    
    /**
     * 获取当前用户信息
     * 从Spring Security上下文中获取认证用户信息并返回脱敏后的用户信息
     * 
     * @return 当前用户信息（脱敏后）
     * @throws BusinessException 当用户未认证、认证信息无效或用户不存在时抛出
     */
    public UserResl getCurrentUser() {
        log.info("开始获取当前用户信息");
        
        // 1. 从Security上下文获取认证信息
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("用户未认证");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        
        // 2. 获取用户名
        String username = authentication.getName();
        if (username == null) {
            log.warn("无法获取用户名");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "认证信息无效");
        }
        
        // 3. 根据用户名查询用户信息
        List<Users> users = findByUserName(username);
        if (users == null || users.isEmpty()) {
            log.warn("用户不存在，用户名: {}", username);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        Users user = users.get(0);
        
        // 4. 转换为响应对象（不包含敏感信息）
        UserResl userResl = new UserResl();
        BeanUtils.copyProperties(user, userResl);
        
        log.info("成功获取当前用户信息，用户名: {}", user.getUsername());
        return userResl;
    }
    
    /**
     * 根据条件获取用户信息
     * 支持根据ID、用户名查询，或获取所有用户列表
     * 
     * @param id 用户ID（可选）
     * @param username 用户名（可选）
     * @return 用户信息或用户列表
     */
    public Object getUsersByCondition(Long id, String username) {
        // 根据ID获取单个用户
        if (id != null) {
            log.info("根据ID获取用户信息，ID: {}", id);
            return findById(id);
        }
        
        // 根据用户名查询用户
        if (username != null && !username.trim().isEmpty()) {
            log.info("根据用户名查询用户，用户名: {}", username);
            return findByUserName(username);
        }
        
        // 获取所有用户列表
        log.info("获取所有用户列表");
        return findAll();
    }
    
    /**
     * 修改当前用户密码
     * 从Spring Security上下文中获取认证用户信息并修改密码
     * 
     * @param changePasswordReq 修改密码请求参数
     * @throws BusinessException 当验证失败或修改失败时抛出异常
     */
    public void changePasswordWithAuth(chat.liuxin.liutech.req.ChangePasswordReq changePasswordReq) {
        log.info("开始修改当前用户密码");
        
        // 1. 验证密码一致性
        if (!changePasswordReq.isPasswordMatch()) {
            log.warn("新密码和确认密码不一致");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码和确认密码不一致");
        }
        
        // 2. 从Security上下文获取认证信息
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("用户未认证");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        
        // 3. 获取用户名
        String username = authentication.getName();
        if (username == null) {
            log.warn("无法获取用户名");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "认证信息无效");
        }
        
        // 4. 根据用户名查询用户信息
        List<Users> users = findByUserName(username);
        if (users == null || users.isEmpty()) {
            log.warn("用户不存在，用户名: {}", username);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        Users user = users.get(0);
        
        // 5. 调用原有的密码修改方法
        try {
            changePassword(user.getId(), username, user.getPasswordHash(), 
                    changePasswordReq.getOldPassword(), changePasswordReq.getNewPassword());
            log.info("用户 {} 密码修改成功", username);
        } catch (BusinessException e) {
            // 直接重新抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("密码修改失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "密码修改失败");
        }
    }
    
    /**
     * 修改用户密码
     * 使用JWT token中的信息验证用户身份并修改密码
     * 
     * @param userId 用户ID（从token中提取）
     * @param username 用户名（从token中提取）
     * @param tokenPasswordHash token中存储的密码哈希（用于验证token的有效性）
     * @param oldPassword 用户输入的原密码
     * @param newPassword 新密码
     * @throws BusinessException 当验证失败或修改失败时抛出异常
     */
    public void changePassword(Long userId, String username, String tokenPasswordHash, 
                              String oldPassword, String newPassword) {
        log.info("开始修改用户密码，用户ID: {}, 用户名: {}", userId, username);
        
        // 1. 查询当前用户信息
        Users currentUser = findById(userId);
        if (currentUser == null) {
            log.warn("用户不存在，ID: {}", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 2. 验证用户名是否匹配（防止token被篡改）
        if (!currentUser.getUsername().equals(username)) {
            log.warn("用户名不匹配，token中: {}, 数据库中: {}", username, currentUser.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户信息不匹配");
        }
        
        // 3. 验证token中的密码哈希是否与数据库中的一致（确保token未过期且有效）
        if (!currentUser.getPasswordHash().equals(tokenPasswordHash)) {
            log.warn("token中的密码哈希与数据库不匹配，可能token已过期或密码已被修改");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "token已失效，请重新登录");
        }
        
        // 4. 验证原密码是否正确
        if (!passwordEncoder.matches(oldPassword, currentUser.getPasswordHash())) {
            log.warn("原密码验证失败，用户: {}", username);
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "原密码错误");
        }
        
        // 5. 检查新密码是否与原密码相同
        if (passwordEncoder.matches(newPassword, currentUser.getPasswordHash())) {
            log.warn("新密码与原密码相同，用户: {}", username);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能与原密码相同");
        }
        
        // 6. 加密新密码
        String encodedNewPassword = passwordEncoder.encode(newPassword);
        
        // 7. 更新密码
        currentUser.setPasswordHash(encodedNewPassword);
        currentUser.setUpdatedAt(new Date());
        
        // 8. 保存到数据库
        try {
            updateUser(currentUser);
            log.info("用户 {} 密码修改成功", username);
        } catch (Exception e) {
            log.error("密码更新失败，用户ID: {}, 错误: {}", userId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "密码更新失败");
        }
    }
    
    /**
     * 更新用户个人资料
     * 从Spring Security上下文中获取认证用户信息并更新个人资料
     * 
     * @param updateProfileReq 更新资料请求参数
     * @return 更新后的用户信息（脱敏后）
     * @throws BusinessException 当验证失败或更新失败时抛出异常
     */
    public UserResl updateProfile(UpdateProfileReq updateProfileReq) {
        log.info("开始更新用户个人资料");
        
        // 1. 从Security上下文获取认证信息
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("用户未认证");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        
        // 2. 获取用户名
        String username = authentication.getName();
        if (username == null) {
            log.warn("无法获取用户名");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "认证信息无效");
        }
        
        // 3. 根据用户名查询用户信息
        List<Users> users = findByUserName(username);
        if (users == null || users.isEmpty()) {
            log.warn("用户不存在，用户名: {}", username);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        Users user = users.get(0);
        
        // 4. 检查邮箱是否已被其他用户使用（如果要更新邮箱）
        if (StringUtils.hasText(updateProfileReq.getEmail()) && 
            !updateProfileReq.getEmail().equals(user.getEmail())) {
            List<Users> existingEmailUsers = findByEmail(updateProfileReq.getEmail());
            if (existingEmailUsers != null && !existingEmailUsers.isEmpty()) {
                // 检查是否是其他用户使用了这个邮箱
                boolean emailUsedByOther = existingEmailUsers.stream()
                    .anyMatch(u -> !u.getId().equals(user.getId()));
                if (emailUsedByOther) {
                    log.warn("邮箱已被其他用户使用: {}", updateProfileReq.getEmail());
                    throw new BusinessException(ErrorCode.EMAIL_EXISTS, "邮箱已被其他用户使用");
                }
            }
        }
        
        // 5. 更新用户信息
        if (StringUtils.hasText(updateProfileReq.getEmail())) {
            user.setEmail(updateProfileReq.getEmail());
        }
        if (StringUtils.hasText(updateProfileReq.getAvatarUrl())) {
            user.setAvatarUrl(updateProfileReq.getAvatarUrl());
        }
        if (StringUtils.hasText(updateProfileReq.getNickname())) {
            user.setNickname(updateProfileReq.getNickname());
        }
        if (StringUtils.hasText(updateProfileReq.getBio())) {
            user.setBio(updateProfileReq.getBio());
        }
        
        user.setUpdatedAt(new Date());
        
        // 6. 保存到数据库
        try {
            updateUser(user);
            log.info("用户 {} 个人资料更新成功", username);
        } catch (Exception e) {
            log.error("个人资料更新失败，用户: {}, 错误: {}", username, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "个人资料更新失败");
        }
        
        // 7. 转换为响应对象（不包含敏感信息）
        UserResl userResl = new UserResl();
        BeanUtils.copyProperties(user, userResl);
        
        return userResl;
    }
    
    /**
     * 获取当前用户统计信息
     * 从Spring Security上下文中获取认证用户信息并返回统计数据
     * 
     * @return 用户统计信息
     * @throws BusinessException 当用户未认证或不存在时抛出异常
     */
    public UserStatsResl getCurrentUserStats() {
        log.info("开始获取当前用户统计信息");
        
        // 1. 从Security上下文获取认证信息
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("用户未认证");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        
        // 2. 获取用户名
        String username = authentication.getName();
        if (username == null) {
            log.warn("无法获取用户名");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "认证信息无效");
        }
        
        // 3. 根据用户名查询用户信息
        List<Users> users = findByUserName(username);
        if (users == null || users.isEmpty()) {
            log.warn("用户不存在，用户名: {}", username);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        Users user = users.get(0);
        Long userId = user.getId();
        
        // 4. 获取统计信息
        UserStatsResl stats = new UserStatsResl();
        
        // 复制用户基本信息
        BeanUtils.copyProperties(user, stats);
        
        try {
            // 获取评论数量
            Integer commentCount = commentsService.countCommentsByUserId(userId);
            stats.setCommentCount(commentCount != null ? commentCount.longValue() : 0L);
            
            // 获取文章数量（已发布）
            Integer postCount = postsService.countPostsByUserId(userId, "published");
            stats.setPostCount(postCount != null ? postCount.longValue() : 0L);
            
            // 获取草稿数量
            Integer draftCount = postsService.countPostsByUserId(userId, "draft");
            stats.setDraftCount(draftCount != null ? draftCount.longValue() : 0L);
            
            // 访问量暂时设为0（后续可扩展）
            stats.setViewCount(0L);
            
            // 获取最近活动时间
            Date lastCommentAt = commentsService.getLastCommentTimeByUserId(userId);
            stats.setLastCommentAt(lastCommentAt);
            
            Date lastPostAt = postsService.getLastPostTimeByUserId(userId);
            stats.setLastPostAt(lastPostAt);
            
            log.info("用户 {} 统计信息获取成功 - 评论: {}, 文章: {}, 草稿: {}", 
                    username, commentCount, postCount, draftCount);
            
        } catch (Exception e) {
            log.error("获取用户统计信息失败，用户: {}, 错误: {}", username, e.getMessage(), e);
            // 如果统计信息获取失败，设置默认值
            stats.setCommentCount(0L);
            stats.setPostCount(0L);
            stats.setDraftCount(0L);
            stats.setViewCount(0L);
        }
        
        return stats;
    }
    
    /**
     * 获取个人资料信息
     * 用于首页个人信息卡片展示
     * 如果用户已登录，返回用户真实信息；否则返回默认信息
     * 
     * @return 个人资料信息
     */
    public ProfileResl getProfile() {
        log.info("开始获取个人资料信息");
        
        // 1. 从Security上下文获取认证信息
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        // 2. 如果用户未认证，返回默认个人资料
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            log.info("用户未认证，返回默认个人资料信息");
            return getDefaultProfile();
        }
        
        // 3. 获取用户名
        String username = authentication.getName();
        if (username == null) {
            log.warn("无法获取用户名，返回默认个人资料信息");
            return getDefaultProfile();
        }
        
        // 4. 根据用户名查询用户信息
        List<Users> users = findByUserName(username);
        if (users == null || users.isEmpty()) {
            log.warn("用户不存在，用户名: {}，返回默认个人资料信息", username);
            return getDefaultProfile();
        }
        
        Users user = users.get(0);
        Long userId = user.getId();
        
        // 5. 构建个人资料响应
        ProfileResl profile = new ProfileResl();
        
        // 设置基本信息
        profile.setName(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        profile.setTitle("全栈工程师"); // 默认职位，后续可以从用户表扩展字段获取
        profile.setAvatar(StringUtils.hasText(user.getAvatarUrl()) ? user.getAvatarUrl() : "/default-avatar.svg");
        profile.setBio(StringUtils.hasText(user.getBio()) ? user.getBio() : "专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。");
        
        // 5. 获取统计信息
        ProfileResl.Stats stats = new ProfileResl.Stats();
        
        try {
            // 获取评论数量
            Integer commentCount = commentsService.countCommentsByUserId(userId);
            stats.setComments(commentCount != null ? commentCount.longValue() : 0L);
            
            // 获取文章数量（已发布）
            Integer postCount = postsService.countPostsByUserId(userId, "published");
            stats.setPosts(postCount != null ? postCount.longValue() : 0L);
            
            // 获取用户文章总浏览量
            Long totalViews = postsService.countViewsByUserId(userId);
            stats.setViews(totalViews != null ? totalViews : 0L);
            
            log.info("用户 {} 个人资料获取成功 - 评论: {}, 文章: {}", 
                    username, commentCount, postCount);
            
        } catch (Exception e) {
            log.error("获取用户统计信息失败，用户: {}, 错误: {}", username, e.getMessage(), e);
            // 如果统计信息获取失败，设置默认值
            stats.setComments(0L);
            stats.setPosts(0L);
            stats.setViews(0L);
        }
        
        profile.setStats(stats);
        
        return profile;
    }
    
    /**
     * 获取默认个人资料信息
     * 用于未登录用户的首页展示
     * 
     * @return 默认个人资料信息
     */
    private ProfileResl getDefaultProfile() {
        ProfileResl profile = new ProfileResl();
        profile.setName("刘鑫同学");
        profile.setTitle("全栈工程师");
        profile.setAvatar("/default-avatar.svg");
        profile.setBio("专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。");
        
        // 设置统计信息（查询全站数据）
         ProfileResl.Stats stats = new ProfileResl.Stats();
         
         try {
             // 查询全站文章总数（已发布）
             Integer totalPosts = postsService.countAllPublishedPosts();
             stats.setPosts(totalPosts != null ? totalPosts.longValue() : 0L);
             
             // 查询全站评论总数
             Integer totalComments = commentsService.countAllComments();
             stats.setComments(totalComments != null ? totalComments.longValue() : 0L);
             
             // 查询全站总浏览量
             Long totalViews = postsService.countAllViews();
             stats.setViews(totalViews != null ? totalViews : 0L);
             
             log.info("获取全站统计数据成功 - 文章: {}, 评论: {}", totalPosts, totalComments);
             
         } catch (Exception e) {
             log.error("获取全站统计数据失败: {}", e.getMessage(), e);
             // 如果查询失败，使用默认值
             stats.setPosts(25L);
             stats.setComments(156L);
             stats.setViews(2580L);
         }
         
         profile.setStats(stats);
        
        return profile;
    }
}
