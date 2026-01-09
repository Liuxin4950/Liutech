package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.CategoryResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.CategoriesService;
import chat.liuxin.liutech.utils.ValidationUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端分类控制器
 * 需要管理员权限才能访问
 */
@Slf4j
@RestController
@RequestMapping("/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class CategoriesAdminController extends BaseAdminController {

    @Autowired
    private CategoriesService categoriesService;

    /**
     * 分页查询分类列表
     */
    @GetMapping
    public Result<PageResp<CategoryResp>> getCategoryList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        try {
            PageResp<CategoryResp> result = categoriesService.getCategoryListForAdmin(page, size, name, includeDeleted);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询分类列表");
        }
    }

    /**
     * 根据ID查询分类详情
     */
    @GetMapping("/{id}")
    public Result<CategoryResp> getCategoryById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "分类ID");
        try {
            CategoryResp category = categoriesService.getById(id);
            return checkResourceExists(category, ErrorCode.NOT_FOUND);
        } catch (Exception e) {
            return handleException(e, "查询分类详情");
        }
    }

    /**
     * 创建分类
     */
    @PostMapping
    @OperationLog(action = "create", targetType = "category", description = "创建分类: #category.name", targetName = "#category.name")
    public Result<String> createCategory(@RequestBody CategoryResp category) {
        ValidationUtil.validateNotNull(category, "分类信息");
        try {
            boolean success = categoriesService.save(category);
            return handleOperationResult(success, "分类创建成功", "分类创建");
        } catch (Exception e) {
            return handleException(e, "分类创建");
        }
    }

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "category", description = "更新分类: #category.name", targetName = "#category.name")
    public Result<String> updateCategory(@PathVariable Long id, @RequestBody CategoryResp category) {
        ValidationUtil.validateId(id, "分类ID");
        ValidationUtil.validateNotNull(category, "分类信息");
        try {
            category.setId(id);
            boolean success = categoriesService.updateById(category);
            return handleOperationResult(success, "分类更新成功", "分类更新");
        } catch (Exception e) {
            return handleException(e, "分类更新");
        }
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "category", description = "删除分类", targetName = "#id")
    public Result<String> deleteCategory(@PathVariable Long id) {
        ValidationUtil.validateId(id, "分类ID");
        try {
            boolean success = categoriesService.removeByIds(List.of(id));
            return handleOperationResult(success, "分类删除成功", "分类删除");
        } catch (Exception e) {
            return handleException(e, "分类删除");
        }
    }

    /**
     * 批量删除分类
     */
    @DeleteMapping("/batch")
    @OperationLog(action = "delete", targetType = "category", description = "批量删除分类")
    public Result<String> batchDeleteCategories(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "分类ID列表");
        try {
            boolean success = categoriesService.removeByIds(ids);
            return handleOperationResult(success, "批量删除分类成功", "批量删除分类");
        } catch (Exception e) {
            return handleException(e, "批量删除分类");
        }
    }

    /**
     * 恢复已删除的分类
     */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "category", description = "恢复分类", targetName = "#id")
    public Result<String> restoreCategory(@PathVariable Long id) {
        ValidationUtil.validateId(id, "分类ID");
        try {
            boolean success = categoriesService.restoreCategory(id);
            return handleOperationResult(success, "分类恢复成功", "分类恢复");
        } catch (Exception e) {
            return handleException(e, "分类恢复");
        }
    }

    /**
     * 彻底删除分类（物理删除）
     */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "category", description = "彻底删除分类", targetName = "#id")
    public Result<String> permanentDeleteCategory(@PathVariable Long id) {
        ValidationUtil.validateId(id, "分类ID");
        try {
            boolean success = categoriesService.permanentDeleteCategory(id);
            return handleOperationResult(success, "分类彻底删除成功", "分类彻底删除");
        } catch (Exception e) {
            return handleException(e, "分类彻底删除");
        }
    }

    /**
     * 批量彻底删除分类（物理删除）
     */
    @DeleteMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "category", description = "批量彻底删除分类", targetName = "#ids")
    public Result<String> batchPermanentDeleteCategories(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "分类ID列表");
        try {
            boolean success = categoriesService.batchPermanentDeleteCategories(ids);
            return handleOperationResult(success, "批量彻底删除分类成功", "批量彻底删除分类");
        } catch (Exception e) {
            return handleException(e, "批量彻底删除分类");
        }
    }

}
