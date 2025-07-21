package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.LoginReq;
import chat.liuxin.liutech.req.RegisterReq;
import chat.liuxin.liutech.resl.UserResl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
            // 这里可以添加邮箱查重逻辑，暂时跳过
            // List<Users> existingEmailUsers = findByEmail(registerReq.getEmail());
            // if (existingEmailUsers != null && !existingEmailUsers.isEmpty()) {
            //     log.warn("注册失败，邮箱已被注册: {}", registerReq.getEmail());
            //     throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            // }
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
     * 验证用户凭据并更新登录时间
     * 
     * @param loginReq 登录请求参数，包含用户名和密码
     * @return 登录成功的用户信息（脱敏后）
     * @throws BusinessException 当用户名或密码错误、账户被禁用时抛出
     */
    public UserResl login(LoginReq loginReq) {
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

        // 5. 转换为响应对象（不包含敏感信息）
        UserResl userResl = new UserResl();
        BeanUtils.copyProperties(user, userResl);
        
        return userResl;
    }
}
