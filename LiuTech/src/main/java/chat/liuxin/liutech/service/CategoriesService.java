package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.CategoriesMapper;
import chat.liuxin.liutech.mapper.PostsMapper;
import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resl.CategoryResl;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 分类服务类
 */
@Slf4j
@Service
public class CategoriesService extends ServiceImpl<CategoriesMapper, Categories> {

    @Autowired
    private CategoriesMapper categoriesMapper;
    
    @Autowired
    private PostsMapper postsMapper;

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

    /**
     * 保存分类（接受CategoryResl参数）
     * @param categoryResl 分类信息
     * @return 保存结果
     */
    public boolean save(CategoryResl categoryResl) {
        Categories category = new Categories();
        category.setName(categoryResl.getName());
        category.setDescription(categoryResl.getDescription());
        return super.save(category);
    }

    /**
     * 根据ID更新分类（接受CategoryResl参数）
     * @param categoryResl 分类信息
     * @return 更新结果
     */
    public boolean updateById(CategoryResl categoryResl) {
        Categories category = new Categories();
        category.setId(categoryResl.getId());
        category.setName(categoryResl.getName());
        category.setDescription(categoryResl.getDescription());
        return super.updateById(category);
    }
    
    /**
     * 批量删除分类（管理端）- 安全删除
     * 检查是否有关联的文章，如果有则抛出异常
     * 
     * @author 刘鑫
     * @date 2025-01-17
     * @param ids 分类ID列表
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByIds(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }
            
            // 先软删除关联的未删除文章
            LambdaUpdateWrapper<Posts> postsUpdateWrapper = new LambdaUpdateWrapper<>();
            postsUpdateWrapper.in(Posts::getCategoryId, ids)
                    .isNull(Posts::getDeletedAt) // 只删除未删除的文章
                    .set(Posts::getDeletedAt, new Date());
            
            int postsResult = postsMapper.update(null, postsUpdateWrapper);
            log.info("删除分类时，软删除关联文章数量: {}", postsResult);
            
            // 然后软删除分类
            LambdaUpdateWrapper<Categories> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(Categories::getId, ids)
                    .set(Categories::getDeletedAt, new Date());
            
            int result = categoriesMapper.update(null, updateWrapper);
            log.info("软删除分类数量: {}", result);
            return result > 0;
        } catch (Exception e) {
            log.error("批量删除分类失败: {}", e.getMessage(), e);
            return false;
        }
    }
}