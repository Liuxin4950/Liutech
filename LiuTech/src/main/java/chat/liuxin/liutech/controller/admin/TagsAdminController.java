package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.TagResp;
import chat.liuxin.liutech.service.TagsService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理控制器
 * 提供标签的增删改查功能
 */
@RestController
@RequestMapping("/admin/tags")
@PreAuthorize("hasRole('ADMIN')")
public class TagsAdminController extends BaseAdminController {

    @Autowired
    private TagsService tagsService;

    /**
     * 分页查询标签列表
     */
    @GetMapping
    public Result<PageResp<TagResp>> getTagList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        try {
            PageResp<TagResp> result = tagsService.getTagListForAdmin(page, size, name, includeDeleted);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询标签列表");
        }
    }

    /**
     * 分页查询标签列表（兼容/list路径）
     */
    @GetMapping("/list")
    public Result<PageResp<TagResp>> getTagListCompat(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        return getTagList(page, size, name, includeDeleted);
    }

    /**
     * 根据ID查询标签详情
     */
    @GetMapping("/{id}")
    public Result<TagResp> getTagById(@PathVariable Long id) {
        try {
            TagResp tag = tagsService.getById(id);
            return checkResourceExists(tag, ErrorCode.TAG_NOT_FOUND);
        } catch (Exception e) {
            return handleException(e, "查询标签详情");
        }
    }

    /**
     * 创建标签
     */
    @PostMapping
    @OperationLog(action = "create", targetType = "tag", description = "创建标签: #tag.name", targetName = "#tag.name")
    public Result<String> createTag(@RequestBody TagResp tag) {
        try {
            boolean success = tagsService.save(tag);
            return handleOperationResult(success, "标签创建成功", "标签创建");
        } catch (Exception e) {
            return handleException(e, "标签创建");
        }
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "tag", description = "更新标签: #tag.name", targetName = "#tag.name")
    public Result<String> updateTag(@PathVariable Long id, @RequestBody TagResp tag) {
        try {
            tag.setId(id);
            boolean success = tagsService.updateById(tag);
            return handleOperationResult(success, "标签更新成功", "标签更新");
        } catch (Exception e) {
            return handleException(e, "标签更新");
        }
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "tag", description = "删除标签", targetName = "#id")
    public Result<String> deleteTag(@PathVariable Long id) {
        try {
            List<Long> ids = List.of(id);
            boolean success = tagsService.removeByIds(ids);
            return handleOperationResult(success, "标签删除成功", "标签删除");
        } catch (Exception e) {
            return handleException(e, "标签删除");
        }
    }

    /**
     * 批量删除标签
     */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "tag", description = "批量删除标签")
    public Result<String> batchDeleteTags(@RequestBody List<Long> ids) {
        try {
            boolean success = tagsService.removeByIds(ids);
            return handleOperationResult(success, "批量删除标签成功", "批量删除标签");
        } catch (Exception e) {
            return handleException(e, "批量删除标签");
        }
    }

    /**
     * 恢复已删除的标签
     */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "tag", description = "恢复标签", targetName = "#id")
    public Result<String> restoreTag(@PathVariable Long id) {
        try {
            boolean success = tagsService.restoreTag(id);
            return handleOperationResult(success, "标签恢复成功", "标签恢复");
        } catch (Exception e) {
            return handleException(e, "标签恢复");
        }
    }

    /**
     * 彻底删除标签（物理删除）
     */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "tag", description = "彻底删除标签", targetName = "#id")
    public Result<String> permanentDeleteTag(@PathVariable Long id) {
        try {
            boolean success = tagsService.permanentDeleteTag(id);
            return handleOperationResult(success, "标签彻底删除成功", "标签彻底删除");
        } catch (Exception e) {
            return handleException(e, "标签彻底删除");
        }
    }

    /**
     * 批量彻底删除标签（物理删除）
     */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "tag", description = "批量彻底删除标签")
    public Result<String> batchPermanentDeleteTags(@RequestBody List<Long> ids) {
        try {
            boolean success = tagsService.batchPermanentDeleteTags(ids);
            return handleOperationResult(success, "批量彻底删除标签成功", "批量彻底删除标签");
        } catch (Exception e) {
            return handleException(e, "批量彻底删除标签");
        }
    }
}
