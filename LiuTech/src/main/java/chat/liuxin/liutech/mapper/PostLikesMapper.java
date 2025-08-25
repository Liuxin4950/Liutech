package chat.liuxin.liutech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.PostLikes;

/**
 * 文章点赞Mapper接口
 * 
 * @author 刘鑫
 * @date 2025-01-15
 */
@Mapper
public interface PostLikesMapper extends BaseMapper<PostLikes> {

    /**
     * 查询用户对文章的点赞状态
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 点赞记录，如果未点赞则返回null
     */
    PostLikes selectByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    /**
     * 插入或更新点赞记录
     * @param postLikes 点赞记录
     * @return 影响行数
     */
    boolean insertOrUpdate(PostLikes postLikes);

    /**
     * 统计文章的点赞数
     * @param postId 文章ID
     * @return 点赞数
     */
    Integer countLikesByPostId(@Param("postId") Long postId);

    /**
     * 删除用户对文章的点赞记录
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 影响行数
     */
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
    
    /**
     * 获取用户对文章的点赞状态
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 点赞状态：1-已点赞，0-未点赞，null-无记录
     */
    Integer getLikeStatus(@Param("userId") Long userId, @Param("postId") Long postId);
    
    /**
     * 插入或更新点赞记录
     * @param userId 用户ID
     * @param postId 文章ID
     * @param status 点赞状态：1-点赞，0-取消点赞
     * @return 影响行数
     */
    int insertOrUpdateLike(@Param("userId") Long userId, @Param("postId") Long postId, @Param("status") int status);
}