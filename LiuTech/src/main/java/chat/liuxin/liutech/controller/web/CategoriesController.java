package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.service.CategoriesService;
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
    public Result<List<Categories>> getAllCategories() {
        log.info("查询所有分类");
        
        List<Categories> categories = categoriesService.getAllCategoriesWithPostCount();
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
    public Result<Categories> getCategoryById(@PathVariable Long id) {
        log.info("查询分类详情 - ID: {}", id);
        
        Categories category = categoriesService.getById(id);
        if (category == null) {
            log.warn("分类不存在 - ID: {}", id);
            return Result.fail(ErrorCode.NOT_FOUND, "分类不存在");
        }
        
        log.info("查询分类详情成功 - 名称: {}", category.getName());
        return Result.success("查询成功", category);
    }
}