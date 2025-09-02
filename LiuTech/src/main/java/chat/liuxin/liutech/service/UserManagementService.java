package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.utils.BeanConvertUtil;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 用户管理服务类
 * 专门处理用户管理、查询、增删改等管理员功能
 * 
 * @author 刘鑫
 * @date 2025-08-30
 */
@Slf4j
@Service
public class UserManagementService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private UserUtils userUtils;
    
    /**
     * 密码加密器，用于管理员创建/更新用户时的密码加密
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 获取当前用户信息
     * 从Spring Security上下文中获取认证用户信息，返回脱敏后的用户数据
     * 该方法会自动处理用户认证状态验证
     * 
     * @return 当前用户信息（脱敏后），不包含密码等敏感信息
     * @throws BusinessException 当用户未认证或不存在时抛出异常
     */
    public UserResl getCurrentUser() {
        log.info("开始获取当前用户信息");
        
        // 1. 获取当前用户
        Users currentUser = getCurrentUserEntity();
        
        // 2. 转换为响应对象（脱敏）
        return convertToUserResl(currentUser);
    }
    
    /**
     * 获取当前用户实体
     */
    private Users getCurrentUserEntity() {
        Users currentUser = userUtils.getCurrentUser();
        if (currentUser == null) {
            log.warn("用户未认证");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        return currentUser;
    }
    
    /**
     * 转换为用户响应对象（脱敏）
     */
    private UserResl convertToUserResl(Users user) {
        return BeanConvertUtil.convertToUserResl(user);
    }
    
    /**
     * 根据条件查询用户
     * 支持按ID或用户名查询，优先使用ID查询
     * 如果两个参数都为空，则返回所有用户列表
     * 
     * @param id 用户ID（可选），如果提供则按ID精确查询
     * @param username 用户名（可选），如果提供则按用户名精确查询
     * @return 查询结果，可能是单个用户对象或用户列表
     * @throws BusinessException 当查询失败时抛出异常
     */
    public Object getUsersByCondition(Long id, String username) {
        log.info("根据条件查询用户 - ID: {}, 用户名: {}", id, username);
        
        try {
            if (id != null) {
                return getUserById(id);
            } else if (StringUtils.hasText(username)) {
                return getUsersByUsername(username);
            } else {
                return getAllUsers();
            }
        } catch (Exception e) {
            log.error("查询用户失败 - ID: {}, 用户名: {}, 错误: {}", id, username, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询用户失败");
        }
    }
    
    /**
     * 根据ID查询用户
     */
    private Users getUserById(Long id) {
        Users user = userMapper.selectById(id);
        if (user == null) {
            log.warn("用户不存在，ID: {}", id);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        return user;
    }
    
    /**
     * 根据用户名查询用户列表
     */
    private List<Users> getUsersByUsername(String username) {
        List<Users> users = userMapper.findByUserName(username);
        if (users == null || users.isEmpty()) {
            log.warn("用户不存在，用户名: {}", username);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        return users;
    }
    
    /**
     * 查询所有用户
     */
    private List<Users> getAllUsers() {
        return userMapper.selectList(null);
    }
    
    /**
     * 管理端分页查询用户列表
     * 管理员专用功能，支持多条件搜索（用户名、邮箱、状态）和分页查询
     * 
     * @param page 页码（从1开始），必须大于0
     * @param size 每页大小，范围1-100
     * @param username 用户名（可选，模糊搜索）
     * @param email 邮箱（可选，模糊搜索）
     * @param status 用户状态（可选，0-禁用，1-启用）
     * @param includeDeleted 是否包含已删除用户
     * @return 分页用户列表，包含总数、当前页数据等信息
     * @throws BusinessException 当分页参数无效时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    public PageResl<UserResl> getUserListForAdmin(Integer page, Integer size, String username, 
                                                  String email, Integer status, Boolean includeDeleted) {
        log.info("管理端查询用户列表 - 页码: {}, 每页: {}, 用户名: {}, 邮箱: {}, 状态: {}, 包含已删除: {}", 
                page, size, username, email, status, includeDeleted);
        
        try {
            // 1. 参数验证
            validatePaginationParams(page, size);
            
            // 2. 查询用户列表
            List<UserResl> users = queryUsersForAdmin(page, size, username, email, status, includeDeleted);
            
            // 3. 查询总数
            int total = userMapper.countUsersForAdmin(username, email, status, includeDeleted);
            
            // 4. 构建分页结果
            return buildPageResult(users, total, page, size);
            
        } catch (Exception e) {
            log.error("管理端用户列表查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询用户列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证分页参数
     */
    private void validatePaginationParams(Integer page, Integer size) {
        if (page == null || page < 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "页码必须大于0");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页大小必须在1-100之间");
        }
    }
    
    /**
     * 查询管理端用户列表
     */
    private List<UserResl> queryUsersForAdmin(Integer page, Integer size, String username, 
                                              String email, Integer status, Boolean includeDeleted) {
        int offset = (page - 1) * size;
        List<UserResl> users = userMapper.selectUsersForAdmin(offset, size, username, email, status, includeDeleted);
        
        // 不返回密码等敏感信息
        users.forEach(user -> user.setPasswordHash(null));
        
        return users;
    }
    
    /**
     * 构建分页结果
     */
    private PageResl<UserResl> buildPageResult(List<UserResl> users, int total, Integer page, Integer size) {
        PageResl<UserResl> pageResult = new PageResl<>();
        pageResult.setRecords(users);
        pageResult.setTotal((long) total);
        pageResult.setCurrent((long) page);
        pageResult.setSize((long) size);
        pageResult.setPages((long) Math.ceil((double) total / size));
        pageResult.setHasNext((long) page < pageResult.getPages());
        pageResult.setHasPrevious((long) page > 1);
        
        log.info("管理端用户列表查询成功 - 总数: {}, 当前页数据: {}", total, users.size());
        return pageResult;
    }
    
    /**
     * 保存用户（管理端）
     * 管理员创建新用户账户，自动加密密码并设置默认状态和积分
     * 
     * @param user 用户信息，包含用户名、密码、邮箱等必要字段
     * @return 是否保存成功，true-成功，false-失败
     * @throws BusinessException 当用户名或邮箱已存在时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean saveUser(Users user) {
        log.info("管理端创建用户，用户名: {}", user.getUsername());
        
        try {
            // 1. 预处理用户数据
            preprocessUserForSave(user);
            
            // 2. 保存到数据库
            int result = userMapper.insert(user);
            boolean success = result > 0;
            
            log.info("用户创建{} - 用户名: {}, ID: {}", success ? "成功" : "失败", user.getUsername(), user.getId());
            return success;
            
        } catch (Exception e) {
            log.error("保存用户失败，用户名: {}, 错误: {}", user.getUsername(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 预处理用户数据用于保存
     */
    private void preprocessUserForSave(Users user) {
        // 密码加密
        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        
        // 设置时间戳
        Date now = new Date();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        
        // 设置默认值
        if (user.getStatus() == null) {
            user.setStatus(1); // 默认启用
        }
        if (user.getPoints() == null) {
            user.setPoints(BigDecimal.ZERO);
        }
    }
    
    /**
     * 根据ID更新用户（管理端）
     * 管理员更新用户账户信息，支持密码更新、状态修改等操作
     * 
     * @param user 用户信息，必须包含有效的ID，其他字段按需更新
     * @return 是否更新成功，true-成功，false-失败
     * @throws BusinessException 当用户不存在或更新失败时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserById(Users user) {
        log.info("管理端更新用户，用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
        
        try {
            // 1. 预处理用户数据
            preprocessUserForUpdate(user);
            
            // 2. 更新到数据库
            int result = userMapper.updateById(user);
            boolean success = result > 0;
            
            log.info("用户更新{} - 用户ID: {}", success ? "成功" : "失败", user.getId());
            return success;
            
        } catch (Exception e) {
            log.error("更新用户失败，用户ID: {}, 错误: {}", user.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 预处理用户数据用于更新
     */
    private void preprocessUserForUpdate(Users user) {
        // 密码加密（如果提供了新密码）
        if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        
        // 更新时间戳
        user.setUpdatedAt(new Date());
    }
    
    /**
     * 根据ID删除用户（管理端）- 软删除
     * 管理员删除用户账户，使用软删除避免外键约束冲突，设置deleted_at字段
     * 
     * @param id 用户ID，不能为null
     * @return 是否删除成功，true-成功，false-失败
     * @throws BusinessException 当用户不存在或删除失败时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean removeUserById(Long id) {
        log.info("管理端删除用户，用户ID: {}", id);
        
        try {
            if (id == null) {
                log.warn("用户ID不能为空");
                return false;
            }
            
            // 使用软删除，设置deleted_at字段
            LambdaUpdateWrapper<Users> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Users::getId, id)
                    .set(Users::getDeletedAt, new Date())
                    .set(Users::getUpdatedBy, userUtils.getCurrentUserId());
            
            int result = userMapper.update(null, updateWrapper);
            boolean success = result > 0;
            
            log.info("用户删除{} - 用户ID: {}", success ? "成功" : "失败", id);
            return success;
            
        } catch (Exception e) {
            log.error("删除用户失败 - 用户ID: {}, 错误: {}", id, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 批量删除用户（管理端）- 软删除
     * 管理员批量删除用户账户，使用软删除避免外键约束冲突，支持事务回滚
     * 
     * @param ids 用户ID列表，不能为空或null
     * @return 是否删除成功，true-成功，false-失败
     * @throws BusinessException 当参数无效或删除失败时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean removeUsersByIds(List<Long> ids) {
        log.info("管理端批量删除用户，用户ID列表: {}", ids);
        
        try {
            if (ids == null || ids.isEmpty()) {
                log.warn("用户ID列表不能为空");
                return false;
            }
            
            // 使用软删除，设置deleted_at字段
            LambdaUpdateWrapper<Users> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(Users::getId, ids)
                    .set(Users::getDeletedAt, new Date())
                    .set(Users::getUpdatedBy, userUtils.getCurrentUserId());
            
            int result = userMapper.update(null, updateWrapper);
            boolean success = result > 0;
            
            log.info("批量删除用户{} - 删除数量: {}", success ? "成功" : "失败", result);
            return success;
            
        } catch (Exception e) {
            log.error("批量删除用户失败，用户ID列表: {}, 错误: {}", ids, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 查询所有用户
     * 基础查询方法，返回所有用户列表
     * 
     * @return 用户列表
     */
    public List<Users> findAllUsers() {
        log.debug("查询所有用户");
        return userMapper.selectList(null);
    }
    
    /**
     * 根据ID查询用户
     * 基础查询方法
     * 
     * @param id 用户ID
     * @return 用户信息，不存在时返回null
     */
    public Users findUserById(Long id) {
        log.debug("根据ID查询用户: {}", id);
        if (id == null) {
            return null;
        }
        return userMapper.selectById(id);
    }
    
    /**
     * 根据用户名查询用户列表
     * 基础查询方法
     * 
     * @param username 用户名
     * @return 用户列表
     */
    public List<Users> findUsersByUsername(String username) {
        log.debug("根据用户名查询用户: {}", username);
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userMapper.findByUserName(username);
    }
    
    /**
     * 根据邮箱查询用户列表
     * 基础查询方法
     * 
     * @param email 邮箱
     * @return 用户列表
     */
    public List<Users> findUsersByEmail(String email) {
        log.debug("根据邮箱查询用户: {}", email);
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return userMapper.findByEmail(email);
    }
    
    /**
     * 添加用户
     * 基础操作方法
     * 
     * @param user 用户信息
     */
    public void addUser(Users user) {
        log.debug("添加用户: {}", user.getUsername());
        userMapper.insert(user);
    }
    
    /**
     * 更新用户
     * 基础操作方法
     * 
     * @param user 用户信息
     */
    public void updateUser(Users user) {
        log.debug("更新用户: {}", user.getId());
        userMapper.updateById(user);
    }
    
    /**
     * 根据ID删除用户
     * @deprecated 请使用 removeUserById 方法进行软删除，避免外键约束问题
     * 物理删除用户会导致相关文章、点赞、收藏等数据变成孤儿数据
     * 
     * @param id 用户ID
     */
    @Deprecated
    public void deleteUserById(Long id) {
        log.warn("使用了已废弃的物理删除方法，建议使用 removeUserById 进行软删除");
        log.debug("删除用户: {}", id);
        userMapper.deleteById(id);
    }
}