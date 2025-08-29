package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.resl.PostListResl;
import chat.liuxin.liutech.service.PostsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章控制器
 * 需要管理员权限才能访问
 * 
 * @author 刘鑫
 */
@RestController
@RequestMapping("/admin/posts")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('ADMIN')")
public class PostsAdminController {

    @Autowired
    private PostsService postsService;

    /**
     * 分页查询文章列表
     * 
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param title 文章标题（可选，模糊搜索）
     * @param categoryId 分类ID（可选）
     * @param status 文章状态（可选）
     * @param authorId 作者ID（可选）
     * @return 分页文章列表
     */
    @GetMapping
    public Result<PageResl<PostListResl>> getPostList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long authorId) {
        try {
            PageResl<PostListResl> result = postsService.getPostListForAdmin(page, size, title, categoryId, status, authorId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "查询文章列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询文章详情
     * 
     * @param id 文章ID
     * @return 文章详情
     */
    @GetMapping("/{id}")
    public Result<Posts> getPostById(@PathVariable Long id) {
        try {
            Posts post = postsService.getById(id);
            if (post == null) {
                return Result.fail(ErrorCode.ARTICLE_NOT_FOUND);
            }
            return Result.success(post);
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "查询文章详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建文章
     * 
     * @param post 文章信息
     * @return 创建结果
     */
    @PostMapping
    public Result<String> createPost(@RequestBody Posts post) {
        try {
            boolean success = postsService.save(post);
            if (success) {
                return Result.success("文章创建成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "文章创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新文章
     * 
     * @param id 文章ID
     * @param post 文章信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result<String> updatePost(@PathVariable Long id, @RequestBody Posts post) {
        try {
            post.setId(id);
            boolean success = postsService.updateById(post);
            if (success) {
                return Result.success("文章更新成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "文章更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除文章
     * 
     * @param id 文章ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deletePost(@PathVariable Long id) {
        try {
            // 获取当前操作者ID（这里简化处理，实际应从SecurityContext获取）
            Long operatorId = 1L; // TODO: 从SecurityContext获取当前管理员ID
            boolean success = postsService.deletePostForAdmin(id, operatorId);
            if (success) {
                return Result.success("文章删除成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "文章删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除文章
     * 
     * @param ids 文章ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<String> batchDeletePosts(@RequestBody List<Long> ids) {
        try {
            boolean success = postsService.removeByIds(ids);
            if (success) {
                return Result.success("批量删除文章成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "批量删除文章失败: " + e.getMessage());
        }
    }

    /**
     * 更新文章状态
     * 
     * @param id 文章ID
     * @param status 文章状态
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result<String> updatePostStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            // 使用专门的管理端状态更新方法，避免updateById导致其他字段为null
            boolean success = postsService.updatePostStatusForAdmin(id, status, 1L); // TODO: 获取当前管理员ID
            if (success) {
                return Result.success("文章状态更新成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "文章状态更新失败: " + e.getMessage());
        }
    }

    /**
     * 批量更新文章状态
     * 
     * @param ids 文章ID列表
     * @param status 文章状态
     * @return 操作结果
     */
    @PutMapping("/batch/status")
    public Result<String> batchUpdatePostStatus(@RequestBody List<Long> ids, @RequestParam String status) {
        try {
            boolean success = postsService.batchUpdateStatus(ids, status);
            if (success) {
                return Result.success("批量更新文章状态成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "批量更新文章状态失败: " + e.getMessage());
        }
    }
}