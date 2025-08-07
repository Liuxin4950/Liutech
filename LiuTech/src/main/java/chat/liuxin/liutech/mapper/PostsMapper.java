package chat.liuxin.liutech.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.model.Posts;

/**
 * 文章Mapper接口
 */
@Mapper
public interface PostsMapper extends BaseMapper<Posts> {

    /**
     * 分页查询文章列表（包含作者和分类信息）
     * @param page 分页参数
     * @param categoryId 分类ID（可选）
     * @param tagId 标签ID（可选）
     * @param keyword 搜索关键词（可选）
     * @param status 文章状态（可选）
     * @param authorId 作者ID（可选）
     * @return 文章列表
     */
    IPage<Posts> selectPostsWithDetails(Page<Posts> page, 
                                       @Param("categoryId") Long categoryId,
                                       @Param("tagId") Long tagId,
                                       @Param("keyword") String keyword,
                                       @Param("status") String status,
                                       @Param("authorId") Long authorId);

    /**
     * 根据ID查询文章详情（包含作者、分类、标签信息）
     * @param id 文章ID
     * @return 文章详情
     */
    Posts selectPostWithDetails(@Param("id") Long id);

    /**
     * 查询热门文章（根据评论数排序）
     * @param limit 限制数量
     * @return 热门文章列表
     */
    List<Posts> selectHotPosts(@Param("limit") Integer limit);

    /**
     * 查询最新文章
     * @param limit 限制数量
     * @return 最新文章列表
     */
    List<Posts> selectLatestPosts(@Param("limit") Integer limit);

    /**
     * 根据分类ID查询文章数量
     * @param categoryId 分类ID
     * @return 文章数量
     */
    Integer countPostsByCategory(@Param("categoryId") Long categoryId);

    /**
     * 根据标签ID统计文章数量
     * @param tagId 标签ID
     * @return 文章数量
     */
    Integer countPostsByTag(@Param("tagId") Long tagId);

    /**
     * 插入文章
     * @param posts 文章对象
     * @return 影响行数
     */
    int insert(Posts posts);

    /**
     * 根据ID更新文章
     * @param posts 文章对象
     * @return 影响行数
     */
    int updateById(Posts posts);

    /**
     * 根据ID软删除文章
     * @param id 文章ID
     * @param deletedAt 删除时间
     * @param updatedBy 更新人ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id, 
                   @Param("deletedAt") java.util.Date deletedAt,
                   @Param("updatedBy") Long updatedBy);

    /**
     * 更新文章状态
     * @param id 文章ID
     * @param status 文章状态
     * @param updatedAt 更新时间
     * @param updatedBy 更新人ID
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("updatedAt") java.util.Date updatedAt,
                     @Param("updatedBy") Long updatedBy);

    /**
     * 根据ID查询文章（不包含关联信息）
     * @param id 文章ID
     * @return 文章对象
     */
    Posts selectById(@Param("id") Long id);

    /**
     * 检查文章是否存在
     * @param id 文章ID
     * @return 是否存在
     */
    Boolean existsById(@Param("id") Long id);
    
    /**
     * 根据用户ID和状态统计文章数量
     * @param userId 用户ID
     * @param status 文章状态
     * @return 文章数量
     */
    Integer countPostsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
    
    /**
     * 获取用户最后发文时间
     * @param userId 用户ID
     * @return 最后发文时间
     */
    Date getLastPostTimeByUserId(@Param("userId") Long userId);
    
    /**
     * 统计全站已发布文章数量
     * @return 已发布文章数量
     */
    Integer countAllPublishedPosts();
    
    /**
     * 统计全站文章总浏览量
     * @return 总浏览量
     */
    Long countAllViews();
    
    /**
     * 统计用户所有文章的浏览量总和
     * @param userId 用户ID
     * @return 用户文章总浏览量
     */
    Long countViewsByUserId(@Param("userId") Long userId);

}