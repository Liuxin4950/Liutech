package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.mapper.PostFavoritesMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.mapper.SystemSettingMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.UpdateProfileReq;
import chat.liuxin.liutech.resp.UserResp;
import chat.liuxin.liutech.resp.UserStatsResp;
import chat.liuxin.liutech.resp.ProfileResp;
import chat.liuxin.liutech.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

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
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;
    private final chat.liuxin.liutech.mapper.UserViewHistoryMapper viewHistoryMapper;

    private final UserUtils userUtils;

    private final CommentsMapper commentsMapper;

    private final PostsMapper postsMapper;

    private final PostFavoritesMapper postFavoritesMapper;

    private final ImagesMapper imagesMapper;

    private final ImageReferenceService imageReferenceService;

    private final SystemSettingMapper systemSettingMapper;

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
    public UserResp updateProfile(UpdateProfileReq updateProfileReq) {
        log.debug("开始更新用户个人资料");

        // 1. 获取当前用户信息
        Long userId = userUtils.getCurrentUserId();
        Users currentUser = userId == null ? null : userMapper.selectProfileForUpdate(userId);
        if (currentUser == null) {
            log.warn("用户未认证");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
        }

        // 2. 验证邮箱是否冲突
        if (StringUtils.hasText(updateProfileReq.getEmail()) &&
            !updateProfileReq.getEmail().equals(currentUser.getEmail())) {

            if (!updateProfileReq.getEmail().equalsIgnoreCase(currentUser.getEmail())
                    && userMapper.countEmailIncludingDeleted(updateProfileReq.getEmail()) > 0) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS, "邮箱已被其他用户使用");
            }
        }

        // 3. 处理头像变更，同步 usage_count
        String oldAvatarUrl = currentUser.getAvatarUrl();
        String newAvatarUrl = updateProfileReq.getAvatarUrl();
        if (newAvatarUrl != null && !newAvatarUrl.equals(oldAvatarUrl)) {
            // 减少旧头像引用
            decrementImageReference(oldAvatarUrl);
            // 增加新头像引用
            incrementImageReference(newAvatarUrl);
        }

        // 只写资料字段，不能把查询结果中的余额、权限、密码等写回。
        LambdaUpdateWrapper<Users> update = new LambdaUpdateWrapper<Users>()
                .eq(Users::getId, currentUser.getId())
                .set(Users::getUpdatedAt, new Date());
        if (StringUtils.hasText(updateProfileReq.getEmail())) {
            update.set(Users::getEmail, updateProfileReq.getEmail());
        }
        if (updateProfileReq.getAvatarUrl() != null) {
            update.set(Users::getAvatarUrl, updateProfileReq.getAvatarUrl());
        }
        if (updateProfileReq.getNickname() != null) {
            update.set(Users::getNickname, updateProfileReq.getNickname());
        }
        if (updateProfileReq.getBio() != null) {
            update.set(Users::getBio, updateProfileReq.getBio());
        }

        // 5. 保存到数据库
        try {
            if (userMapper.update(null, update) != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "个人资料更新失败");
            }
            userUtils.clearUserCache(currentUser.getUsername());
            log.debug("用户 {} 个人资料更新成功", currentUser.getUsername());
        } catch (Exception e) {
            log.error("个人资料更新失败，用户: {}, 错误: {}", currentUser.getUsername(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "个人资料更新失败");
        }

        // 6. 转换为响应对象
        UserResp userResp = new UserResp();
        BeanUtils.copyProperties(userMapper.selectById(currentUser.getId()), userResp);
        return userResp;
    }



    /**
     * 获取当前用户统计信息
     * 从Spring Security上下文中获取认证用户信息并返回统计数据
     *
     * @return 用户统计信息
     * @throws BusinessException 当用户未认证或不存在时抛出异常
     */
    /** 个人行为频繁变化，直接读当前数据库；统计错误交由统一异常处理，不能返回伪造的 0。 */
    @Transactional(readOnly = true)
    public UserStatsResp getCurrentUserStats() {
        Long userId = userUtils.getCurrentUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        Users currentUser = userMapper.selectById(userId);
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        UserStatsResp stats = new UserStatsResp();
        BeanUtils.copyProperties(currentUser, stats);
        stats.setCommentCount(commentsMapper.countVisibleCommentsByUserId(userId));
        stats.setPostCount(postsMapper.countPostsByUserIdAndStatus(userId, "published").longValue());
        stats.setDraftCount(postsMapper.countPostsByUserIdAndStatus(userId, "draft").longValue());
        stats.setViewCount(viewHistoryMapper.countVisibleByUserId(userId));
        stats.setFavoriteCount(postFavoritesMapper.countFavoritesByUserId(userId).longValue());
        stats.setLastCommentAt(commentsMapper.getLastCommentTimeByUserId(userId));
        stats.setLastPostAt(postsMapper.getLastPostTimeByUserId(userId));
        return stats;
    }

    /**
     * 获取个人资料信息
     * 作者资料来源为 system_settings，不混入当前访问者的头像与统计
     *
     * @return 个人资料信息
     */
    @Transactional(readOnly = true)
    public ProfileResp getProfile() {
        // 首页侧栏展示配置的博客作者，个人中心使用 /user/current 与 /user/stats。
        return getDefaultProfile();
    }



    /**
     * 获取默认个人资料信息
     * 为未登录用户提供默认的个人资料信息，展示网站基本信息
     *
     * @return 默认个人资料，包含网站基本信息和统计数据
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(readOnly = true)
    public ProfileResp getDefaultProfile() {
        ProfileResp profile = new ProfileResp();

        // 从数据库读取作者资料配置
        String authorName = getSettingValue("author.name", "小鑫同学");
        String authorTitle = getSettingValue("author.title", "欢迎访问");
        String authorAvatar = getSettingValue("author.avatar", "/洛天依.png");
        String authorBio = getSettingValue("author.bio", "欢迎来到我的博客！这里分享技术文章、编程心得和生活感悟。");

        profile.setName(authorName);
        profile.setTitle(authorTitle);
        profile.setAvatar(authorAvatar != null && !authorAvatar.isEmpty() ? authorAvatar : "/洛天依.png");
        profile.setBio(authorBio);

        // 设置默认统计信息
        ProfileResp.Stats stats = new ProfileResp.Stats();

        try {
            Integer totalComments = commentsMapper.countAllComments();
            Integer totalPosts = postsMapper.countAllPublishedPosts();
            Long totalViews = postsMapper.countAllViews();

            stats.setComments(totalComments != null ? totalComments.longValue() : 0L);
            stats.setPosts(totalPosts != null ? totalPosts.longValue() : 0L);
            stats.setViews(totalViews != null ? totalViews : 0L);

            log.debug("默认个人资料统计信息获取成功 - 总评论: {}, 总文章: {}, 总浏览: {}",
                    totalComments, totalPosts, totalViews);

        } catch (Exception e) {
            log.error("获取默认个人资料统计信息失败: {}", e.getMessage(), e);
            stats.setComments(0L);
            stats.setPosts(0L);
            stats.setViews(0L);
        }

        profile.setStats(stats);
        return profile;
    }

    /**
     * 从 system_settings 表读取配置值，不存在时返回默认值
     */
    private String getSettingValue(String key, String defaultValue) {
        try {
            var setting = systemSettingMapper.selectByKey(key);
            if (setting != null && setting.getSettingValue() != null && !setting.getSettingValue().isEmpty()) {
                return setting.getSettingValue();
            }
        } catch (Exception e) {
            log.warn("读取系统设置失败: key={}, error={}", key, e.getMessage());
        }
        return defaultValue;
    }

    /**
     * 获取当前用户ID
     * 从Spring Security上下文中获取认证用户的ID，用于缓存键生成
     *
     * @return 当前用户ID，如果未认证则返回null
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(readOnly = true)
    public Long getCurrentUserId() {
        return userUtils.getCurrentUserId();
    }

    // ==================== 图片引用计数管理 ====================

    /**
     * 增加图片引用计数
     * @param imageUrl 图片URL
     */
    private void incrementImageReference(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        imageReferenceService.addReferences(List.of(imageUrl));
    }

    /**
     * 减少图片引用计数
     * @param imageUrl 图片URL
     */
    private void decrementImageReference(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        imageReferenceService.removeReferences(List.of(imageUrl));
    }
}
