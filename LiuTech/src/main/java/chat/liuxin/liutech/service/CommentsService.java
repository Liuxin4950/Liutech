package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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
import chat.liuxin.liutech.utils.UserUtils;
import chat.liuxin.liutech.req.CreateCommentReq;
import chat.liuxin.liutech.resl.CommentResl;
import chat.liuxin.liutech.resl.PageResl;
import lombok.extern.slf4j.Slf4j;
/**
 * 评论服务类
 * 提供评论的增删改查、树形结构构建、统计等核心业务功能
 * 
 * @author 刘鑫
 * @date 2025-08-30
 */
@Slf4j
@Service
public class CommentsService extends ServiceImpl<CommentsMapper, Comments> {

    @Autowired
    private CommentsMapper commentsMapper;
    
    @Autowired
    private UserUtils userUtils;

    /**
     * 分页查询文章评论
     * 获取指定文章的所有评论，支持分页显示
     * 
     * @param postId 文章ID
     * @param page 页码，从1开始
     * @param size 每页大小
     * @return 分页评论列表，包含评论内容和分页信息
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
     * 获取文章的所有顶级评论，并为每个顶级评论加载其子评论，构建树形结构
     * 
     * @param postId 文章ID
     * @return 顶级评论列表，每个评论包含其所有子评论
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
     * 统计指定文章的所有评论数量（包括子评论）
     * 
     * @param postId 文章ID
     * @return 评论总数量
     */
    public Integer countCommentsByPostId(Long postId) {
        return commentsMapper.countCommentsByPostId(postId);
    }

    /**
     * 查询最新评论
     * 按创建时间降序获取最新的评论列表
     * 
     * @param limit 限制数量，最多返回的评论数
     * @return 最新评论列表，按时间降序排列
     */
    public List<Comments> getLatestComments(Integer limit) {
        return commentsMapper.selectLatestComments(limit);
    }

    /**
     * 查询某个评论的子评论
     * 获取指定父评论下的所有直接子评论
     * 
     * @param parentId 父评论ID，不能为null
     * @return 子评论列表，按创建时间升序排列
     * @author 刘鑫
     * @date 2025-01-30
     */
    public List<Comments> getChildCommentsByParentId(Long parentId) {
        return commentsMapper.selectChildCommentsByParentId(parentId);
    }
    
    /**
     * 统计用户评论数量
     * 统计指定用户发表的所有评论数量
     * 
     * @param userId 用户ID，不能为null
     * @return 用户评论总数量
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Integer countCommentsByUserId(Long userId) {
        return commentsMapper.countCommentsByUserId(userId);
    }
    
    /**
     * 获取用户最后评论时间
     * 查询指定用户最后一次发表评论的时间
     * 
     * @param userId 用户ID，不能为null
     * @return 最后评论时间，如果用户没有评论则返回null
     * @author 刘鑫
     * @date 2025-01-30
     */
    public Date getLastCommentTimeByUserId(Long userId) {
        return commentsMapper.getLastCommentTimeByUserId(userId);
    }
    
    /**
     * 统计全站评论数量
     * 统计系统中所有评论的总数量
     * 
     * @return 系统评论总数量
     * @author 刘鑫
     * @date 2025-01-30
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
        
        // 获取并验证当前用户
        Users currentUser = validateCurrentUser();
        
        // 验证父评论（如果是回复）
        validateParentComment(createCommentReq);
        
        // 创建并保存评论
        Comments comment = buildComment(createCommentReq, currentUser);
        saveComment(comment);
        
        // 设置用户信息并转换为响应对象
        comment.setUser(currentUser);
        return convertToCommentResl(comment);
    }
    
    /**
     * 验证当前用户
     * @return 当前用户
     */
    private Users validateCurrentUser() {
        Users currentUser = userUtils.getCurrentUser();
        if (currentUser == null) {
            log.error("获取当前用户失败");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        log.info("获取到当前用户: {}", currentUser.getUsername());
        return currentUser;
    }
    
    /**
     * 验证父评论
     * 检查父评论是否存在以及是否可以回复
     * 
     * @param createCommentReq 创建评论请求参数
     * @throws BusinessException 当父评论不存在或不可回复时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    private void validateParentComment(CreateCommentReq createCommentReq) {
        if (createCommentReq.getParentId() == null) {
            return;
        }
        
        Comments parentComment = this.getById(createCommentReq.getParentId());
        if (parentComment == null) {
            throw new BusinessException(ErrorCode.PARENT_COMMENT_NOT_FOUND);
        }
        
        // 确保父评论和当前评论属于同一篇文章
        if (!parentComment.getPostId().equals(createCommentReq.getPostId())) {
            throw new BusinessException(ErrorCode.PARENT_COMMENT_MISMATCH);
        }
    }
    
    /**
     * 构建评论对象
     * 根据请求参数和当前用户信息创建评论对象
     * 
     * @param createCommentReq 创建评论请求参数
     * @param currentUser 当前用户
     * @return 构建的评论对象
     * @author 刘鑫
     * @date 2025-01-30
     */
    private Comments buildComment(CreateCommentReq createCommentReq, Users currentUser) {
        Comments comment = new Comments();
        comment.setPostId(createCommentReq.getPostId());
        comment.setContent(createCommentReq.getContent());
        comment.setParentId(createCommentReq.getParentId());
        comment.setUserId(currentUser.getId());
        comment.setCreatedAt(new Date());
        comment.setUpdatedAt(new Date());
        return comment;
    }
    
    /**
     * 保存评论
     * 将评论保存到数据库，包含异常处理
     * 
     * @param comment 评论对象
     * @throws BusinessException 当保存失败时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    private void saveComment(Comments comment) {
        boolean saved = this.save(comment);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "评论创建失败");
        }
    }

    /**
     * 将Comments实体转换为CommentResl响应对象
     * 将评论实体转换为响应对象，包含用户信息和子评论
     * 
     * @param comment 评论实体
     * @return 评论响应对象
     * @author 刘鑫
     * @date 2025-01-30
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