package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.resl.CategoryResl;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.service.CategoriesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端分类控制器
 * 
 * @author 刘鑫
 */
@RestController
@RequestMapping("/api/admin/categories")
@CrossOrigin(origins = "http://localhost:3000")
public class CategoriesAdminController {

    @Autowired
    private CategoriesService categoriesService;

    /**
     * 分页查询分类列表
     * 
     * @param page 页码，默认1
     * @param size 每页大小，默认10
     * @param name 分类名称（可选，模糊搜索）
     * @return 分页分类列表
     */
    @GetMapping
    public Result<PageResl<CategoryResl>> getCategoryList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name) {
        try {
            PageResl<CategoryResl> result = categoriesService.getCategoryListForAdmin(page, size, name);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "查询分类列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询分类详情
     * 
     * @param id 分类ID
     * @return 分类详情
     */
    @GetMapping("/{id}")
    public Result<CategoryResl> getCategoryById(@PathVariable Long id) {
        try {
            CategoryResl category = categoriesService.getById(id);
            if (category == null) {
                return Result.fail(ErrorCode.CATEGORY_NOT_FOUND);
            }
            return Result.success(category);
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "查询分类详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建分类
     * 
     * @param category 分类信息
     * @return 创建结果
     */
    @PostMapping
    public Result<String> createCategory(@RequestBody Categories category) {
        try {
            boolean success = categoriesService.save(category);
            if (success) {
                return Result.success("分类创建成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "分类创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新分类
     * 
     * @param id 分类ID
     * @param category 分类信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result<String> updateCategory(@PathVariable Long id, @RequestBody Categories category) {
        try {
            category.setId(id);
            boolean success = categoriesService.updateById(category);
            if (success) {
                return Result.success("分类更新成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "分类更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除分类
     * 
     * @param id 分类ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteCategory(@PathVariable Long id) {
        try {
            boolean success = categoriesService.removeById(id);
            if (success) {
                return Result.success("分类删除成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "分类删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除分类
     * 
     * @param ids 分类ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<String> batchDeleteCategories(@RequestBody List<Long> ids) {
        try {
            boolean success = categoriesService.removeByIds(ids);
            if (success) {
                return Result.success("批量删除分类成功");
            } else {
                return Result.fail(ErrorCode.OPERATION_ERROR);
            }
        } catch (Exception e) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "批量删除分类失败: " + e.getMessage());
        }
    }
}