package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.resl.TagResl;
import chat.liuxin.liutech.service.TagsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理控制器
 * 提供标签的增删改查功能
 * 
 * @author 刘鑫
 * @date 2024-01-30
 */
@RestController
@RequestMapping("/admin/tags")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('ADMIN')")
public class TagsAdminController extends BaseAdminController {

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
            return handleException(e, "查询标签列表");
        }
    }

    /**
     * 分页查询标签列表（兼容/list路径）
     * 
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param name 标签名称（可选，模糊搜索）
     * @return 分页标签列表
     */
    @GetMapping("/list")
    public Result<PageResl<TagResl>> getTagListCompat(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name) {
        return getTagList(page, size, name);
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
            return checkResourceExists(tag, ErrorCode.TAG_NOT_FOUND);
        } catch (Exception e) {
            return handleException(e, "查询标签详情");
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
            return handleOperationResult(success, "标签创建成功", "标签创建");
        } catch (Exception e) {
            return handleException(e, "标签创建");
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
            return handleOperationResult(success, "标签更新成功", "标签更新");
        } catch (Exception e) {
            return handleException(e, "标签更新");
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
            // 使用批量删除方法来正确处理外键约束
            List<Long> ids = List.of(id);
            boolean success = tagsService.removeByIds(ids);
            return handleOperationResult(success, "标签删除成功", "标签删除");
        } catch (Exception e) {
            return handleException(e, "标签删除");
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
            return handleOperationResult(success, "批量删除标签成功", "批量删除标签");
        } catch (Exception e) {
            return handleException(e, "批量删除标签");
        }
    }
}