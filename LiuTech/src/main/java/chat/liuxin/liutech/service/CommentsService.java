package chat.liuxin.liutech.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.resl.CommentResl;
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
    public PageResl<CommentResl> getCommentsByPostId(Long postId, Integer page, Integer size) {
        Page<Comments> pageParam = new Page<>(page, size);
        IPage<Comments> result = commentsMapper.selectCommentsByPostId(pageParam, postId);
        
        List<CommentResl> commentResls = result.getRecords().stream()
                .map(this::convertToCommentResl)
                .collect(Collectors.toList());
        
        return new PageResl<>(commentResls, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 查询文章的顶级评论（树形结构）
     * @param postId 文章ID
     * @return 顶级评论列表（包含子评论）
     */
    public List<CommentResl> getTopLevelCommentsByPostId(Long postId) {
        List<Comments> topComments = commentsMapper.selectTopLevelCommentsByPostId(postId);
        
        // 为每个顶级评论加载子评论并转换为响应对象
        return topComments.stream().map(comment -> {
            List<Comments> children = commentsMapper.selectChildCommentsByParentId(comment.getId());
            comment.setChildren(children);
            return convertToCommentResl(comment);
        }).collect(Collectors.toList());
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

    /**
     * 将Comments实体转换为CommentResl响应对象
     * @param comment Comments实体
     * @return CommentResl响应对象
     */
    private CommentResl convertToCommentResl(Comments comment) {
        CommentResl commentResl = new CommentResl();
        commentResl.setId(comment.getId());
        commentResl.setPostId(comment.getPostId());
        commentResl.setContent(comment.getContent());
        commentResl.setParentId(comment.getParentId());
        commentResl.setCreatedAt(comment.getCreatedAt());
        
        // 转换用户信息
        if (comment.getUser() != null) {
            CommentResl.UserInfo userInfo = new CommentResl.UserInfo();
            userInfo.setId(comment.getUser().getId());
            userInfo.setUsername(comment.getUser().getUsername());
            userInfo.setAvatarUrl(comment.getUser().getAvatarUrl());
            commentResl.setUser(userInfo);
        }
        
        // 转换子评论
        if (comment.getChildren() != null) {
            List<CommentResl> childrenResl = comment.getChildren().stream()
                    .map(this::convertToCommentResl)
                    .collect(Collectors.toList());
            commentResl.setChildren(childrenResl);
        }
        
        return commentResl;
    }
}