package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.CategoryResp;
import chat.liuxin.liutech.service.CategoriesService;
import chat.liuxin.liutech.utils.ValidationUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 分类控制器
 * 提供文章分类相关的REST API接口
 *
 * @author liuxin
 */
@Slf4j
@RestController
@RequestMapping("/categories")
public class CategoriesController {

    @Autowired
    private CategoriesService categoriesService;

    /**
     * 查询所有分类（包含文章数量）
     *
     * @return 分类列表
     */
    @GetMapping
    public Result<List<CategoryResp>> getAllCategories() {
        log.info("查询所有分类");

        List<CategoryResp> categories = categoriesService.getAllCategoriesWithPostCount();
        log.info("查询分类成功 - 数量: {}", categories.size());

        return Result.success("查询成功", categories);
    }

    /**
     * 根据ID查询分类详情
     *
     * @param id 分类ID
     * @return 分类详情
     */
    @GetMapping("/{id}")
    public Result<CategoryResp> getCategoryById(@PathVariable Long id) {
        log.info("查询分类详情 - ID: {}", id);

        CategoryResp category = categoriesService.getById(id);
        if (category == null) {
            log.warn("分类不存在 - ID: {}", id);
            return Result.fail(ErrorCode.CATEGORY_NOT_FOUND);
        }

        log.info("查询分类详情成功 - 名称: {}", category.getName());
        return Result.success("查询成功", category);
    }

    /**
     * 创建分类（需要登录）
     *
     * @param category 分类信息
     * @return 创建结果
     */
    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public Result<CategoryResp> createCategory(@RequestBody CategoryResp category) {
        log.info("创建分类 - 名称: {}", category.getName());
        
        ValidationUtil.validateNotNull(category, "分类信息");
        ValidationUtil.validateNotBlank(category.getName(), "分类名称");
        
        try {
            boolean success = categoriesService.save(category);
            if (success) {
                log.info("分类创建成功 - 名称: {}", category.getName());
                return Result.success("分类创建成功", category);
            } else {
                log.warn("分类创建失败 - 名称: {}", category.getName());
                return Result.fail(ErrorCode.CATEGORY_CREATE_FAILED, "分类创建失败");
            }
        } catch (Exception e) {
            log.error("分类创建异常 - 名称: {}, 错误:", category.getName(), e);
            
            // 检查是否是重复名称错误
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry") && e.getMessage().contains("categories.name")) {
                return Result.fail(ErrorCode.CATEGORY_CREATE_FAILED, "分类名称已存在，请使用其他名称");
            }
            
            return Result.fail(ErrorCode.CATEGORY_CREATE_FAILED, "系统错误，请稍后重试");
        }
    }
}
