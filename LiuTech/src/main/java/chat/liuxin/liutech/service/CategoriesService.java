package chat.liuxin.liutech.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.CategoriesMapper;
import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.resl.CategoryResl;
import chat.liuxin.liutech.resl.PageResl;

/**
 * 分类服务类
 */
@Service
public class CategoriesService extends ServiceImpl<CategoriesMapper, Categories> {

    @Autowired
    private CategoriesMapper categoriesMapper;

    /**
     * 查询所有分类（包含文章数量）
     * @return 分类列表
     */
    @Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()")
    public List<CategoryResl> getAllCategoriesWithPostCount() {
        return categoriesMapper.selectCategoriesWithPostCount();
    }

    /**
     * 管理端分页查询分类列表
     * @param page 页码
     * @param size 每页大小
     * @param name 分类名称（可选，模糊搜索）
     * @return 分页分类列表
     */
    public PageResl<CategoryResl> getCategoryListForAdmin(Integer page, Integer size, String name) {
        // 计算偏移量
        Integer offset = (page - 1) * size;
        
        // 查询分类列表
        List<CategoryResl> categoryList = categoriesMapper.selectCategoriesForAdmin(offset, size, name);
        
        // 查询总数
        Integer total = categoriesMapper.countCategoriesForAdmin(name);
        
        // 构建分页结果
        PageResl<CategoryResl> pageResl = new PageResl<>();
        pageResl.setRecords(categoryList);
        pageResl.setTotal(total.longValue());
        pageResl.setCurrent(page.longValue());
        pageResl.setSize(size.longValue());
        pageResl.setPages((long) Math.ceil((double) total / size));
        pageResl.setHasNext(page.longValue() < pageResl.getPages());
        pageResl.setHasPrevious(page.longValue() > 1);
        
        return pageResl;
    }

    /**
     * 根据ID查询分类详情（返回CategoryResl）
     * @param id 分类ID
     * @return 分类详情
     */
    public CategoryResl getById(Long id) {
        Categories category = super.getById(id);
        if (category == null) {
            return null;
        }
        
        CategoryResl categoryResl = new CategoryResl();
        categoryResl.setId(category.getId());
        categoryResl.setName(category.getName());
        categoryResl.setDescription(category.getDescription());
        categoryResl.setCreatedAt(category.getCreatedAt());
        categoryResl.setUpdatedAt(category.getUpdatedAt());
        // postCount 在单个查询时设为0，如需要可以单独查询
        categoryResl.setPostCount(0);
        
        return categoryResl;
    }
}