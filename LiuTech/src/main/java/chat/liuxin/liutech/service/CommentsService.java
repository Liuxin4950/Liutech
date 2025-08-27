package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.req.CreateCommentReq;
import chat.liuxin.liutech.resl.CommentResl;
import chat.liuxin.liutech.resl.PageResl;
import lombok.extern.slf4j.Slf4j;
/**
 * 评论服务类
 */
@Slf4j
@Service
public class CommentsService extends ServiceImpl<CommentsMapper, Comments> {

    @Autowired
    private CommentsMapper commentsMapper;
    
    @Autowired
    private UserService userService;

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
     * 统计用户评论数量
     * @param userId 用户ID
     * @return 评论数量
     */
    public Integer countCommentsByUserId(Long userId) {
        return commentsMapper.countCommentsByUserId(userId);
    }
    
    /**
     * 获取用户最后评论时间
     * @param userId 用户ID
     * @return 最后评论时间
     */
    public Date getLastCommentTimeByUserId(Long userId) {
        return commentsMapper.getLastCommentTimeByUserId(userId);
    }
    
    /**
     * 统计全站评论数量
     * @return 评论数量
     */
    public Integer countAllComments() {
        return commentsMapper.countAllComments();
    }
    
    /**
     * 创建评论
     * @param createCommentReq 创建评论请求
     * @return 创建的评论
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentResl createComment(CreateCommentReq createCommentReq) {
        log.info("开始创建评论，请求参数: {}", createCommentReq);
        
        // 获取当前登录用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("获取到的Authentication: {}", authentication);
        
        if (authentication == null) {
            log.error("Authentication为null");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        
        if (!authentication.isAuthenticated()) {
            log.error("用户未认证，isAuthenticated: {}", authentication.isAuthenticated());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        
        if ("anonymousUser".equals(authentication.getPrincipal())) {
            log.error("用户为匿名用户，principal: {}", authentication.getPrincipal());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        
        String username = authentication.getName();
        log.info("从Authentication中获取的用户名: {}", username);
        
        List<Users> users = userService.findByUserName(username);
        log.info("根据用户名查询到的用户列表: {}", users);
        
        if (users == null || users.isEmpty()) {
            log.error("根据用户名{}未找到用户", username);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Users currentUser = users.get(0);
        log.info("获取到当前用户: {}", currentUser.getUsername());
        
        // 如果是回复评论，验证父评论是否存在
        if (createCommentReq.getParentId() != null) {
            Comments parentComment = this.getById(createCommentReq.getParentId());
            if (parentComment == null) {
                throw new BusinessException(ErrorCode.PARENT_COMMENT_NOT_FOUND);
            }
            // 确保父评论和当前评论属于同一篇文章
            if (!parentComment.getPostId().equals(createCommentReq.getPostId())) {
                throw new BusinessException(ErrorCode.PARENT_COMMENT_MISMATCH);
            }
        }
        
        // 创建评论对象
        Comments comment = new Comments();
        comment.setPostId(createCommentReq.getPostId());
        comment.setContent(createCommentReq.getContent());
        comment.setParentId(createCommentReq.getParentId());
        comment.setUserId(currentUser.getId());
        comment.setCreatedAt(new Date());
        comment.setUpdatedAt(new Date());
        
        // 保存评论
        boolean saved = this.save(comment);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "评论创建失败");
        }
        
        // 设置用户信息并转换为响应对象
        comment.setUser(currentUser);
        return convertToCommentResl(comment);
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