package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;

import chat.liuxin.liutech.utils.UserUtils;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Comments;
import chat.liuxin.liutech.req.CreateCommentReq;
import chat.liuxin.liutech.resp.CommentResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.CommentsService;
import lombok.extern.slf4j.Slf4j;

/**
 * 评论控制器
 * 提供评论相关的REST API接口
 *
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequestMapping("/comments")
public class CommentsController {

    @Autowired
    private CommentsService commentsService;

    @Autowired
    private UserUtils userUtils;

    /**
     * 分页查询文章评论
     *
     * @param postId 文章ID
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页评论列表
     */
    @GetMapping("/post/{postId}")
    public Result<PageResp<CommentResp>> getCommentsByPostId(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        PageResp<CommentResp> result = commentsService.getCommentsByPostId(postId, page, size);

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
    public Result<List<CommentResp>> getTreeCommentsByPostId(@PathVariable Long postId) {

        List<CommentResp> comments = commentsService.getTopLevelCommentsByPostId(postId);

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

        Integer count = commentsService.countCommentsByPostId(postId);

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

        List<Comments> children = commentsService.getChildCommentsByParentId(parentId);

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

        List<Comments> comments = commentsService.getLatestComments(limit);

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

        Comments comment = commentsService.getById(id);
        if (comment == null) {
            log.warn("评论不存在 - ID: {}", id);
            return Result.fail(ErrorCode.NOT_FOUND, "评论不存在");
        }

        return Result.success("查询成功", comment);
    }

    /**
     * 创建评论
     *
     * @param createCommentReq 创建评论请求
     * @return 创建的评论
     */
    @PostMapping
    @OperationLog(action = "create", targetType = "comment", description = "发表评论")
    public Result<CommentResp> createComment(@Valid @RequestBody CreateCommentReq createCommentReq) {

        try {
            // 方法级认证确认：确保用户已登录
            Long currentUserId = userUtils.getCurrentUserId();
            if (currentUserId == null) {
                log.warn("用户未登录，无法发表评论");
                return Result.fail(ErrorCode.UNAUTHORIZED);
            }

            CommentResp comment = commentsService.createComment(createCommentReq);
            return Result.success("创建成功", comment);
        } catch (Exception e) {
            log.error("创建评论失败", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "创建评论失败: " + e.getMessage());
        }
    }
}
