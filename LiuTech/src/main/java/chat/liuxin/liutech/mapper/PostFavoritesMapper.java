package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.model.PostFavorites;
import chat.liuxin.liutech.resp.PostFavoriteUserResp;

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
    
    /**
     * 根据文章ID删除所有收藏记录
     * @param postId 文章ID
     * @return 影响行数
     */
    int deleteByPostId(@Param("postId") Long postId);

    /**
     * 根据文章ID列表批量删除收藏记录
     * @param postIds 文章ID列表
     * @return 影响行数
     */
    int deleteByPostIds(@Param("postIds") List<Long> postIds);

    /**
     * 统计用户的收藏数量
     * @param userId 用户ID
     * @return 收藏数量
     */
    Integer countFavoritesByUserId(@Param("userId") Long userId);

    /**
     * 分页查询收藏某篇文章的用户列表（JOIN users 带用户信息与收藏时间）
     * @param page 分页参数
     * @param postId 文章ID
     * @return 收藏用户分页
     */
    IPage<PostFavoriteUserResp> selectFavoriteUsersByPostId(Page<PostFavoriteUserResp> page, @Param("postId") Long postId);
}