package chat.liuxin.liutech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.PostFavorites;

/**
 * 文章收藏Mapper接口
 * 
 * @author 刘鑫
 * @date 2025-01-15
 */
@Mapper
public interface PostFavoritesMapper extends BaseMapper<PostFavorites> {

    /**
     * 查询用户对文章的收藏状态
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 收藏记录，如果未收藏则返回null
     */
    PostFavorites selectByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    /**
     * 插入或更新收藏记录
     * @param postFavorites 收藏记录
     * @return 影响行数
     */
    boolean insertOrUpdate(PostFavorites postFavorites);

    /**
     * 统计文章的收藏数
     * @param postId 文章ID
     * @return 收藏数
     */
    Integer countFavoritesByPostId(@Param("postId") Long postId);

    /**
     * 删除用户对文章的收藏记录
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 影响行数
     */
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
    
    /**
     * 获取用户对文章的收藏状态
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 收藏状态：1-已收藏，0-未收藏，null-无记录
     */
    Integer getFavoriteStatus(@Param("userId") Long userId, @Param("postId") Long postId);
    
    /**
     * 插入或更新收藏记录
     * @param userId 用户ID
     * @param postId 文章ID
     * @param status 收藏状态：1-收藏，0-取消收藏
     * @return 影响行数
     */
    int insertOrUpdateFavorite(@Param("userId") Long userId, @Param("postId") Long postId, @Param("status") int status);
}