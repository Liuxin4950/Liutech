package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;

import chat.liuxin.liutech.resl.TagResl;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.service.TagsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端标签控制器
 * 需要管理员权限才能访问
 * 
 * @author 刘鑫
 */
@RestController
@RequestMapping("/api/admin/tags")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('ADMIN')")
public class TagsAdminController {

    @Autowired
    private TagsService tagsService;

    /**
     * 分页查询标签列表
     * 
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param name 标签名称（可选，模糊搜索）
     * @return 分页标签列表
     */
    @GetMapping
    public Result<PageResl<TagResl>> getTagList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name) {
        try {
            PageResl<TagResl> result = tagsService.getTagListForAdmin(page, size, name);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "查询标签列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询标签详情
     * 
     * @param id 标签ID
     * @return 标签详情
     */
    @GetMapping("/{id}")
    public Result<TagResl> getTagById(@PathVariable Long id) {
        try {
            TagResl tag = tagsService.getById(id);
            if (tag == null) {
                return Result.fail(ErrorCode.TAG_NOT_FOUND);
            }
            return Result.success(tag);
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "查询标签详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建标签
     * 
     * @param tag 标签信息
     * @return 创建结果
     */
    @PostMapping
    public Result<String> createTag(@RequestBody TagResl tag) {
        try {
            boolean success = tagsService.save(tag);
            if (success) {
                return Result.success("标签创建成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "标签创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新标签
     * 
     * @param id 标签ID
     * @param tag 标签信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result<String> updateTag(@PathVariable Long id, @RequestBody TagResl tag) {
        try {
            tag.setId(id);
            boolean success = tagsService.updateById(tag);
            if (success) {
                return Result.success("标签更新成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "标签更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除标签
     * 
     * @param id 标签ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteTag(@PathVariable Long id) {
        try {
            boolean success = tagsService.removeById(id);
            if (success) {
                return Result.success("标签删除成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "标签删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除标签
     * 
     * @param ids 标签ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<String> batchDeleteTags(@RequestBody List<Long> ids) {
        try {
            boolean success = tagsService.removeByIds(ids);
            if (success) {
                return Result.success("批量删除标签成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "批量删除标签失败: " + e.getMessage());
        }
    }
}