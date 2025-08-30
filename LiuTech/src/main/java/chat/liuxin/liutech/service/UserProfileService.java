package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.UpdateProfileReq;
import chat.liuxin.liutech.resl.UserResl;
import chat.liuxin.liutech.resl.UserStatsResl;
import chat.liuxin.liutech.resl.ProfileResl;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 用户资料服务类
 * 专门处理用户个人资料、统计信息相关功能
 * 
 * @author 刘鑫
 * @date 2025-08-30
 */
@Slf4j
@Service
public class UserProfileService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private UserUtils userUtils;
    
    @Autowired
    private CommentsService commentsService;
    
    @Autowired
    private PostsService postsService;
    
    /**
     * 更新当前用户个人资料
     * 从Spring Security上下文中获取认证用户信息并更新资料
     * 
     * @param updateProfileReq 更新资料请求参数
     * @return 更新后的用户信息（脱敏后）
     * @throws BusinessException 当用户未认证、邮箱冲突或更新失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "userStats", key = "#root.target.getCurrentUserId()")
    public UserResl updateProfile(UpdateProfileReq updateProfileReq) {
        log.info("开始更新用户个人资料");
        
        // 1. 获取当前用户信息
        Users currentUser = userUtils.getCurrentUser();
        if (currentUser == null) {
            log.warn("用户未认证");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        
        // 2. 验证邮箱是否冲突
        if (StringUtils.hasText(updateProfileReq.getEmail()) && 
            !updateProfileReq.getEmail().equals(currentUser.getEmail())) {
            
            List<Users> existingEmailUsers = userMapper.findByEmail(updateProfileReq.getEmail());
            if (existingEmailUsers != null && !existingEmailUsers.isEmpty()) {
                // 检查是否是其他用户使用了这个邮箱
                boolean emailUsedByOther = existingEmailUsers.stream()
                    .anyMatch(u -> !u.getId().equals(currentUser.getId()));
                if (emailUsedByOther) {
                    log.warn("邮箱已被其他用户使用: {}", updateProfileReq.getEmail());
                    throw new BusinessException(ErrorCode.EMAIL_EXISTS, "邮箱已被其他用户使用");
                }
            }
        }
        
        // 3. 更新用户信息
        if (StringUtils.hasText(updateProfileReq.getEmail())) {
            currentUser.setEmail(updateProfileReq.getEmail());
        }
        if (StringUtils.hasText(updateProfileReq.getAvatarUrl())) {
            currentUser.setAvatarUrl(updateProfileReq.getAvatarUrl());
        }
        if (StringUtils.hasText(updateProfileReq.getNickname())) {
            currentUser.setNickname(updateProfileReq.getNickname());
        }
        if (StringUtils.hasText(updateProfileReq.getBio())) {
            currentUser.setBio(updateProfileReq.getBio());
        }
        currentUser.setUpdatedAt(new Date());
        
        // 4. 保存到数据库
        try {
            userMapper.updateById(currentUser);
            log.info("用户 {} 个人资料更新成功", currentUser.getUsername());
        } catch (Exception e) {
            log.error("个人资料更新失败，用户: {}, 错误: {}", currentUser.getUsername(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "个人资料更新失败");
        }
        
        // 5. 转换为响应对象
        UserResl userResl = new UserResl();
        BeanUtils.copyProperties(currentUser, userResl);
        return userResl;
    }
    

    
    /**
     * 获取当前用户统计信息
     * 从Spring Security上下文中获取认证用户信息并返回统计数据
     * 
     * @return 用户统计信息
     * @throws BusinessException 当用户未认证或不存在时抛出异常
     */
    @Cacheable(value = "userStats", key = "#root.target.getCurrentUserId()", unless = "#result == null")
    public UserStatsResl getCurrentUserStats() {
        log.info("开始获取当前用户统计信息");
        
        // 1. 获取当前用户信息
        Users currentUser = userUtils.getCurrentUser();
        if (currentUser == null) {
            log.warn("用户未认证");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }
        
        // 2. 构建统计信息
        UserStatsResl stats = new UserStatsResl();
        BeanUtils.copyProperties(currentUser, stats);
        
        // 3. 获取详细统计数据
        try {
            // 获取评论数量
            Integer commentCount = commentsService.countCommentsByUserId(currentUser.getId());
            stats.setCommentCount(commentCount != null ? commentCount.longValue() : 0L);
            
            // 获取文章数量（已发布）
            Integer postCount = postsService.countPostsByUserId(currentUser.getId(), "published");
            stats.setPostCount(postCount != null ? postCount.longValue() : 0L);
            
            // 获取草稿数量
            Integer draftCount = postsService.countPostsByUserId(currentUser.getId(), "draft");
            stats.setDraftCount(draftCount != null ? draftCount.longValue() : 0L);
            
            // 访问量暂时设为0（后续可扩展）
            stats.setViewCount(0L);
            
            // 获取最近活动时间
            Date lastCommentAt = commentsService.getLastCommentTimeByUserId(currentUser.getId());
            stats.setLastCommentAt(lastCommentAt);
            
            Date lastPostAt = postsService.getLastPostTimeByUserId(currentUser.getId());
            stats.setLastPostAt(lastPostAt);
            
            log.info("用户 {} 统计信息获取成功 - 评论: {}, 文章: {}, 草稿: {}", 
                    currentUser.getUsername(), commentCount, postCount, draftCount);
            
        } catch (Exception e) {
            log.error("获取用户统计信息失败，用户: {}, 错误: {}", currentUser.getUsername(), e.getMessage(), e);
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
     * 支持已登录用户和未登录用户的不同展示
     * 
     * @return 个人资料信息
     */
    public ProfileResl getProfile() {
        // 检查用户是否已登录
        if (userUtils.isCurrentUserLoggedIn()) {
            try {
                Users currentUser = userUtils.getCurrentUser();
                if (currentUser == null) {
                    log.warn("无法获取当前用户信息，返回默认个人资料");
                    return getDefaultProfile();
                }
                
                // 构建用户个人资料
                ProfileResl profile = new ProfileResl();
                
                // 设置基本信息
                profile.setName(StringUtils.hasText(currentUser.getNickname()) ? currentUser.getNickname() : currentUser.getUsername());
                profile.setTitle("全栈工程师"); // 默认职位，后续可以从用户表扩展字段获取
                profile.setAvatar(StringUtils.hasText(currentUser.getAvatarUrl()) ? currentUser.getAvatarUrl() : "/default-avatar.svg");
                profile.setBio(StringUtils.hasText(currentUser.getBio()) ? currentUser.getBio() : "专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。");
                
                // 获取统计信息
                ProfileResl.Stats stats = new ProfileResl.Stats();
                try {
                    // 获取评论数量
                    Integer commentCount = commentsService.countCommentsByUserId(currentUser.getId());
                    stats.setComments(commentCount != null ? commentCount.longValue() : 0L);
                    
                    // 获取文章数量（已发布）
                    Integer postCount = postsService.countPostsByUserId(currentUser.getId(), "published");
                    stats.setPosts(postCount != null ? postCount.longValue() : 0L);
                    
                    // 获取用户文章总浏览量
                    Long totalViews = postsService.countViewsByUserId(currentUser.getId());
                    stats.setViews(totalViews != null ? totalViews : 0L);
                    
                    log.info("用户 {} 个人资料获取成功 - 评论: {}, 文章: {}", 
                            currentUser.getUsername(), commentCount, postCount);
                    
                } catch (Exception e) {
                    log.error("获取用户统计信息失败，用户: {}, 错误: {}", currentUser.getUsername(), e.getMessage(), e);
                    // 如果统计信息获取失败，设置默认值
                    stats.setComments(0L);
                    stats.setPosts(0L);
                    stats.setViews(0L);
                }
                
                profile.setStats(stats);
                return profile;
                
            } catch (Exception e) {
                log.error("获取用户个人资料失败: {}", e.getMessage(), e);
                return getDefaultProfile();
            }
        } else {
            return getDefaultProfile();
        }
    }
    

    
    /**
     * 获取默认个人资料信息
     * 为未登录用户提供默认的个人资料信息，展示网站基本信息
     * 
     * @return 默认个人资料，包含网站基本信息和统计数据
     * @author 刘鑫
     * @date 2025-01-30
     */
    private ProfileResl getDefaultProfile() {
        ProfileResl profile = new ProfileResl();
        profile.setName("访客");
        profile.setTitle("欢迎访问");
        profile.setAvatar("/default-avatar.svg");
        profile.setBio("欢迎来到我的博客！这里分享技术文章、编程心得和生活感悟。");
        
        // 设置默认统计信息
        ProfileResl.Stats stats = new ProfileResl.Stats();
        
        try {
            // 获取网站总体统计信息
            Integer totalComments = commentsService.countAllComments();
            Integer totalPosts = postsService.countAllPublishedPosts();
            Long totalViews = postsService.countAllViews();
            
            stats.setComments(totalComments != null ? totalComments.longValue() : 0L);
            stats.setPosts(totalPosts != null ? totalPosts.longValue() : 0L);
            stats.setViews(totalViews != null ? totalViews : 0L);
            
            log.debug("默认个人资料统计信息获取成功 - 总评论: {}, 总文章: {}, 总浏览: {}", 
                    totalComments, totalPosts, totalViews);
            
        } catch (Exception e) {
            log.error("获取默认个人资料统计信息失败: {}", e.getMessage(), e);
            // 如果获取失败，设置默认值
            stats.setComments(0L);
            stats.setPosts(0L);
            stats.setViews(0L);
        }
        
        profile.setStats(stats);
        return profile;
    }
    
    /**
     * 获取当前用户ID
     * 从Spring Security上下文中获取认证用户的ID，用于缓存键生成
     * 
     * @return 当前用户ID，如果未认证则返回null
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Long getCurrentUserId() {
        return userUtils.getCurrentUserId();
    }
}