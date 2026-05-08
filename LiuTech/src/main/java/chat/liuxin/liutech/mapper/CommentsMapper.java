package chat.liuxin.liutech.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.model.Comments;

/**
 * 评论Mapper接口
 */
@Mapper
public interface CommentsMapper extends BaseMapper<Comments> {

    /**
     * 分页查询文章评论（包含用户信息）
     * @param page 分页参数
     * @param postId 文章ID
     * @return 评论列表
     */
    IPage<Comments> selectCommentsByPostId(Page<Comments> page, @Param("postId") Long postId);

    /**
     * 查询文章的顶级评论（包含用户信息和子评论）
     * @param postId 文章ID
     * @return 顶级评论列表
     */
    List<Comments> selectTopLevelCommentsByPostId(@Param("postId") Long postId);

    /**
     * 查询某个评论的子评论（包含用户信息）
     * @param parentId 父评论ID
     * @return 子评论列表
     */
    List<Comments> selectChildCommentsByParentId(@Param("parentId") Long parentId);

    /**
     * 统计文章评论数量
     * @param postId 文章ID
     * @return 评论数量
     */
    Integer countCommentsByPostId(@Param("postId") Long postId);

    /**
     * 查询最新评论
     * @param limit 限制数量
     * @return 最新评论列表
     */
    List<Comments> selectLatestComments(@Param("limit") Integer limit);
    
    /**
     * 统计用户评论数量
     * @param userId 用户ID
     * @return 评论数量
     */
    Integer countCommentsByUserId(@Param("userId") Long userId);
    
    /**
     * 获取用户最后评论时间
     * @param userId 用户ID
     * @return 最后评论时间
     */
    Date getLastCommentTimeByUserId(@Param("userId") Long userId);
    
    /**
     * 统计全站评论数量
     * @return 评论数量
     */
    Integer countAllComments();
    
    /**
     * 根据文章ID物理删除所有评论
     * @param postId 文章ID
     * @return 影响行数
     */
    int deleteByPostId(@Param("postId") Long postId);

    /**
     * 先删除子评论（parent_id 非空）
     */
    int deleteChildrenByPostId(@Param("postId") Long postId);

    /**
     * 再删除顶级评论（parent_id 为空）
     */
    int deleteRootsByPostId(@Param("postId") Long postId);

    /**
     * 管理端分页查询评论列表（关联用户名和文章标题）
     * @param offset 偏移量
     * @param limit 限制数量
     * @param postId 文章ID（可选）
     * @param userId 用户ID（可选）
     * @param status 状态过滤（可选）：deleted / active
     * @param includeDeleted 是否包含已删除评论
     * @return 评论列表
     */
    List<Comments> selectCommentsForAdmin(@Param("offset") Integer offset,
                                          @Param("limit") Integer limit,
                                          @Param("postId") Long postId,
                                          @Param("userId") Long userId,
                                          @Param("status") String status,
                                          @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 管理端查询评论总数
     * @param postId 文章ID（可选）
     * @param userId 用户ID（可选）
     * @param status 状态过滤（可选）
     * @param includeDeleted 是否包含已删除评论
     * @return 总数
     */
    Integer countCommentsForAdmin(@Param("postId") Long postId,
                                  @Param("userId") Long userId,
                                  @Param("status") String status,
                                  @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 恢复已删除的评论
     * @param id 评论ID
     * @return 影响的行数
     */
    int restoreCommentById(@Param("id") Long id);

    /**
     * 根据ID查询评论详情（管理端，关联用户和文章信息）
     * @param id 评论ID
     * @return 评论信息
     */
    Comments selectCommentsForAdminById(@Param("id") Long id);

    /**
     * 根据ID列表物理删除评论
     * @param ids 评论ID列表
     * @return 影响的行数
     */
    int permanentDeleteByIds(@Param("ids") List<Long> ids);
}