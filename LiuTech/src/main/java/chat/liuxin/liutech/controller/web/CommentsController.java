package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.req.CreateCommentReq;
import chat.liuxin.liutech.resl.CommentResl;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.service.CommentsService;
import lombok.extern.slf4j.Slf4j;

/**
 * 评论控制器
 * 提供评论相关的REST API接口
 * 
 * @author liuxin
 */
@Slf4j
@RestController
@RequestMapping("/comments")
public class CommentsController {

    @Autowired
    private CommentsService commentsService;

    /**
     * 分页查询文章评论
     * 
     * @param postId 文章ID
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页评论列表
     */
    @GetMapping("/post/{postId}")
    public Result<PageResl<CommentResl>> getCommentsByPostId(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        
        log.info("查询文章评论 - 文章ID: {}, 页码: {}, 大小: {}", postId, page, size);
        
        PageResl<CommentResl> result = commentsService.getCommentsByPostId(postId, page, size);
        log.info("查询文章评论成功 - 文章ID: {}, 总数: {}", postId, result.getTotal());
        
        return Result.success("查询成功", result);
    }

    /**
     * 查询文章的树形评论结构
     * 返回顶级评论及其子评论
     * 
     * @param postId 文章ID
     * @return 树形评论列表
     */
    @GetMapping("/post/{postId}/tree")
    public Result<List<CommentResl>> getTreeCommentsByPostId(@PathVariable Long postId) {
        log.info("查询文章树形评论 - 文章ID: {}", postId);
        
        List<CommentResl> comments = commentsService.getTopLevelCommentsByPostId(postId);
        log.info("查询文章树形评论成功 - 文章ID: {}, 顶级评论数: {}", postId, comments.size());
        
        return Result.success("查询成功", comments);
    }

    /**
     * 统计文章评论数量
     * 
     * @param postId 文章ID
     * @return 评论数量
     */
    @GetMapping("/post/{postId}/count")
    public Result<Integer> getCommentCountByPostId(@PathVariable Long postId) {
        log.info("统计文章评论数量 - 文章ID: {}", postId);
        
        Integer count = commentsService.countCommentsByPostId(postId);
        log.info("统计文章评论数量成功 - 文章ID: {}, 数量: {}", postId, count);
        
        return Result.success("查询成功", count);
    }

    /**
     * 查询某个评论的子评论
     * 
     * @param parentId 父评论ID
     * @return 子评论列表
     */
    @GetMapping("/{parentId}/children")
    public Result<List<Comments>> getChildComments(@PathVariable Long parentId) {
        log.info("查询子评论 - 父评论ID: {}", parentId);
        
        List<Comments> children = commentsService.getChildCommentsByParentId(parentId);
        log.info("查询子评论成功 - 父评论ID: {}, 数量: {}", parentId, children.size());
        
        return Result.success("查询成功", children);
    }

    /**
     * 查询最新评论
     * 
     * @param limit 限制数量，默认10
     * @return 最新评论列表
     */
    @GetMapping("/latest")
    public Result<List<Comments>> getLatestComments(
            @RequestParam(defaultValue = "10") Integer limit) {
        
        log.info("查询最新评论 - 限制数量: {}", limit);
        
        List<Comments> comments = commentsService.getLatestComments(limit);
        log.info("查询最新评论成功 - 数量: {}", comments.size());
        
        return Result.success("查询成功", comments);
    }

    /**
     * 根据ID查询评论详情
     * 
     * @param id 评论ID
     * @return 评论详情
     */
    @GetMapping("/{id}")
    public Result<Comments> getCommentById(@PathVariable Long id) {
        log.info("查询评论详情 - ID: {}", id);
        
        Comments comment = commentsService.getById(id);
        if (comment == null) {
            log.warn("评论不存在 - ID: {}", id);
            return Result.fail(ErrorCode.NOT_FOUND, "评论不存在");
        }
        
        log.info("查询评论详情成功 - ID: {}", id);
        return Result.success("查询成功", comment);
    }

    /**
     * 创建评论
     * 
     * @param createCommentReq 创建评论请求
     * @return 创建的评论
     */
    @PostMapping
    public Result<CommentResl> createComment(@Valid @RequestBody CreateCommentReq createCommentReq) {
        log.info("创建评论 - 请求: {}", createCommentReq);
        
        try {
            CommentResl comment = commentsService.createComment(createCommentReq);
            log.info("创建评论成功 - ID: {}", comment.getId());
            return Result.success("创建成功", comment);
        } catch (Exception e) {
            log.error("创建评论失败", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "创建评论失败: " + e.getMessage());
        }
    }
}