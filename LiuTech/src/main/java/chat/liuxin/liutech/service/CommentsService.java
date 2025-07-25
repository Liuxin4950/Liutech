package chat.liuxin.liutech.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.resl.PageResl;

/**
 * 评论服务类
 */
@Service
public class CommentsService extends ServiceImpl<CommentsMapper, Comments> {

    @Autowired
    private CommentsMapper commentsMapper;

    /**
     * 分页查询文章评论
     * @param postId 文章ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页评论列表
     */
    public PageResl<Comments> getCommentsByPostId(Long postId, Integer page, Integer size) {
        Page<Comments> pageParam = new Page<>(page, size);
        IPage<Comments> result = commentsMapper.selectCommentsByPostId(pageParam, postId);
        
        return new PageResl<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 查询文章的顶级评论（树形结构）
     * @param postId 文章ID
     * @return 顶级评论列表（包含子评论）
     */
    public List<Comments> getTopLevelCommentsByPostId(Long postId) {
        List<Comments> topComments = commentsMapper.selectTopLevelCommentsByPostId(postId);
        
        // 为每个顶级评论加载子评论
        for (Comments comment : topComments) {
            List<Comments> children = commentsMapper.selectChildCommentsByParentId(comment.getId());
            comment.setChildren(children);
        }
        
        return topComments;
    }

    /**
     * 统计文章评论数量
     * @param postId 文章ID
     * @return 评论数量
     */
    public Integer countCommentsByPostId(Long postId) {
        return commentsMapper.countCommentsByPostId(postId);
    }

    /**
     * 查询最新评论
     * @param limit 限制数量
     * @return 最新评论列表
     */
    public List<Comments> getLatestComments(Integer limit) {
        return commentsMapper.selectLatestComments(limit);
    }

    /**
     * 查询某个评论的子评论
     * @param parentId 父评论ID
     * @return 子评论列表
     */
    public List<Comments> getChildCommentsByParentId(Long parentId) {
        return commentsMapper.selectChildCommentsByParentId(parentId);
    }
}