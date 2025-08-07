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
}