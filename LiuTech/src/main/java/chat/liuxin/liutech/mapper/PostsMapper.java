package chat.liuxin.liutech.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resp.PostListResp;
import chat.liuxin.liutech.resp.PostDetailResp;

/**
 * 文章Mapper接口
 */
@Mapper
public interface PostsMapper extends BaseMapper<Posts> {

    /**
     * 获取所有已发布的文章（用于 sitemap 生成）
     * 只返回状态为 "published" 的文章
     *
     * @return 已发布文章列表
     */
    List<Posts> selectPublishedPosts();
    /**
     * 分页查询文章列表（包含作者和分类信息）
     * 
     * @param page       分页参数
     * @param categoryId 分类ID（可选）
     * @param tagId      标签ID（可选）
     * @param keyword    搜索关键词（可选）
     * @param status     文章状态（可选）
     * @param authorId   作者ID（可选）
     * @return 文章列表
     */
    IPage<Posts> selectPostsWithDetails(Page<Posts> page,
            @Param("categoryId") Long categoryId,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("authorId") Long authorId);

    /**
     * 分页查询文章列表（包含作者、分类信息和用户点赞收藏状态）
     * 
     * @param page       分页参数
     * @param categoryId 分类ID（可选）
     * @param tagId      标签ID（可选）
     * @param keyword    搜索关键词（可选）
     * @param status     文章状态（可选）
     * @param authorId   作者ID（可选）
     * @param userId     当前用户ID（用于查询点赞收藏状态）
     * @return 文章列表
     */
    IPage<Posts> selectPostsWithDetailsAndUserStatus(Page<Posts> page,
            @Param("categoryId") Long categoryId,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("authorId") Long authorId,
            @Param("userId") Long userId);

    /**
     * 查询文章详情（包含作者、分类、标签信息和用户点赞收藏状态）
     * 
     * @param id     文章ID
     * @param userId 当前用户ID（用于查询点赞收藏状态，可为null）
     * @return 文章详情
     */
    Posts selectPostWithDetails(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 分页查询文章列表（返回PostListResl）
     * 
     * @param page       分页参数
     * @param categoryId 分类ID（可选）
     * @param tagId      标签ID（可选）
     * @param keyword    搜索关键词（可选）
     * @param status     文章状态（可选）
     * @param authorId   作者ID（可选）
     * @param userId     当前用户ID（用于查询点赞收藏状态）
     * @return 文章列表
     */
    IPage<PostListResp> selectPostListResl(Page<PostListResp> page,
            @Param("categoryId") Long categoryId,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("authorId") Long authorId,
            @Param("userId") Long userId);

    /**
     * 查询文章详情（返回PostDetailResl）
     * 
     * @param id     文章ID
     * @param userId 当前用户ID（用于查询点赞收藏状态，可为null）
     * @return 文章详情
     */
    PostDetailResp selectPostDetailResl(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 查询热门文章列表（返回PostListResl）
     * 
     * @param limit  限制数量
     * @param userId 当前用户ID（用于查询点赞收藏状态，可为null）
     * @return 热门文章列表
     */
    List<PostListResp> selectHotPostListResl(@Param("limit") Integer limit, @Param("userId") Long userId);

    /**
     * 管理端分页查询文章列表（返回PostListResl）
     * 
     * @param page           分页参数
     * @param categoryId     分类ID（可选）
     * @param keyword        搜索关键词（可选）
     * @param status         文章状态（可选）
     * @param authorId       作者ID（可选）
     * @param includeDeleted 是否包含已删除文章
     * @return 文章列表
     */
    IPage<PostListResp> selectPostListForAdmin(Page<PostListResp> page,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("authorId") Long authorId,
            @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 查询最新文章列表（返回PostListResl）
     * 
     * @param limit  限制数量
     * @param userId 当前用户ID（用于查询点赞收藏状态，可为null）
     * @return 最新文章列表
     */
    List<PostListResp> selectLatestPostListResl(@Param("limit") Integer limit, @Param("userId") Long userId);

    /**
     * 查询热门文章（根据评论数排序）
     * 
     * @param limit 限制数量
     * @return 热门文章列表
     */
    List<Posts> selectHotPosts(@Param("limit") Integer limit);

    /**
     * 查询最新文章
     * 
     * @param limit 限制数量
     * @return 最新文章列表
     */
    List<Posts> selectLatestPosts(@Param("limit") Integer limit);

    /**
     * 查询用户收藏的文章列表
     * @param page
     * @param userId
     * @param keyword
     * @return
     */

    IPage<PostListResp> selectFavoritePostList(
            Page<PostListResp> page,
            @Param("userId") Long userId,
            @Param("keyword") String keyword);

    /**
     * 根据分类ID查询文章数量
     * 
     * @param categoryId 分类ID
     * @return 文章数量
     */
    Integer countPostsByCategory(@Param("categoryId") Long categoryId);

    /**
     * 根据标签ID查询文章数量
     * 
     * @param tagId 标签ID
     * @return 文章数量
     */
    Integer countPostsByTagId(@Param("tagId") Long tagId);

    /**
     * 恢复已删除的文章（绕过逻辑删除限制）
     *
     * @param id 文章ID
     * @return 影响的行数
     */
    int restorePostById(@Param("id") Long id);

    /**
     * 批量恢复已删除的文章
     *
     * @param ids 文章ID列表
     * @return 影响的行数
     */
    int restorePostsByIds(@Param("ids") List<Long> ids);

    /**
     * 根据ID软删除文章
     *
     * @param id        文章ID
     * @param deletedAt 删除时间
     * @param updatedBy 更新人ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id,
            @Param("deletedAt") java.util.Date deletedAt,
            @Param("updatedBy") Long updatedBy);

    /**
     * 根据用户ID和状态统计文章数量
     *
     * @param userId 用户ID
     * @param status 文章状态
     * @return 文章数量
     */
    Integer countPostsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 获取用户最后发文时间
     *
     * @param userId 用户ID
     * @return 最后发文时间
     */
    Date getLastPostTimeByUserId(@Param("userId") Long userId);

    /**
     * 统计全站已发布文章数量
     *
     * @return 已发布文章数量
     */
    Integer countPublishedPosts();

    /**
     * 统计全站已发布文章数量（管理端）
     *
     * @return 已发布文章数量
     */
    Integer countAllPublishedPosts();

    /**
     * 管理端统计文章总数
     * 
     * @param categoryId     分类ID（可选）
     * @param keyword        搜索关键词（可选）
     * @param status         文章状态（可选）
     * @param authorId       作者ID（可选）
     * @param includeDeleted 是否包含已删除文章
     * @return 文章总数
     */
    Integer countPostsForAdmin(@Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("authorId") Long authorId,
            @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 统计全站文章总浏览量
     *
     * @return 总浏览量
     */
    Long countAllViews();

    /**
     * 统计指定日期发布的文章数量
     *
     * @param date 日期（只比较年月日）
     * @return 文章数量
     */
    Integer countPostsByDate(@Param("date") java.util.Date date);

    /**
     * 统计用户所有文章的浏览量总和
     * 
     * @param userId 用户ID
     * @return 用户文章总浏览量
     */
    Long countViewsByUserId(@Param("userId") Long userId);

    /**
     * 根据分类ID列表物理删除文章
     * 
     * @param categoryIds 分类ID列表
     * @return 影响的行数
     */
    int deletePostsByCategoryIds(@Param("categoryIds") List<Long> categoryIds);

    /**
     * 根据标签ID列表删除文章标签关联关系
     * 
     * @param tagIds 标签ID列表
     * @return 影响的行数
     */
    int deletePostTagsByTagIds(@Param("tagIds") List<Long> tagIds);

    /**
     * 根据文章ID物理删除文章
     * 
     * @param id 文章ID
     * @return 影响的行数
     */
    int permanentDeleteById(@Param("id") Long id);

    /**
     * 根据文章ID列表物理删除文章
     *
     * @param ids 文章ID列表
     * @return 影响的行数
     */
    int permanentDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 根据ID查询文章（包括已软删除的，绕过 @TableLogic）
     *
     * @param id 文章ID
     * @return 文章记录，不存在返回null
     */
    @Select("SELECT * FROM posts WHERE id = #{id}")
    Posts selectByIdWithDeleted(@Param("id") Long id);

    // ==================== 孤立图片清理相关方法 ====================

    /**
     * 查询所有文章封面图片URL（用于孤立图片清理）
     *
     * @return 封面URL列表
     */
    @Select("SELECT cover_image FROM posts WHERE cover_image IS NOT NULL AND deleted_at IS NULL")
    List<String> selectAllCoverImages();

    /**
     * 查询所有文章缩略图URL（用于孤立图片清理）
     *
     * @return 缩略图URL列表
     */
    @Select("SELECT thumbnail FROM posts WHERE thumbnail IS NOT NULL AND deleted_at IS NULL")
    List<String> selectAllThumbnails();

    /**
     * 查询所有已发布文章的内容（用于孤立图片清理，提取HTML中的内嵌图片）
     *
     * @return 文章列表（包含content字段）
     */
    @Select("SELECT id, cover_image, thumbnail, content FROM posts WHERE status = 'published' AND deleted_at IS NULL")
    List<Posts> selectAllPublishedPostsWithContent();

}
