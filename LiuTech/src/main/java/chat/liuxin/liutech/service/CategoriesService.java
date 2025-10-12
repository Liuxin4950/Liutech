package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.*;
import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.model.Posts;
import chat.liuxin.liutech.resp.CategoryResp;
import chat.liuxin.liutech.resp.PageResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired
    private PostFavoritesMapper postFavoritesMapper;

    @Autowired
    private PostLikesMapper postLikesMapper;

    @Autowired
    private CommentsMapper commentsMapper;

    @Autowired
    private PostTagsMapper postTagsMapper;

    @Autowired
    private chat.liuxin.liutech.mapper.PostAttachmentsMapper postAttachmentsMapper;

    /**
     * 查询所有分类（包含文章数量）
     * @return 分类列表
     */
    @Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()")
    public List<CategoryResp> getAllCategoriesWithPostCount() {
        return categoriesMapper.selectCategoriesWithPostCount();
    }

    /**
     * 获取分类列表（管理端）
     * 管理员专用功能，支持分页查询和按名称模糊搜索
     *
     * @param page 页码，从1开始
     * @param size 每页大小，建议10-50之间
     * @param name 分类名称（可选，模糊搜索）
     * @param includeDeleted 是否包含已删除分类
     * @return 分页结果，包含分类列表和分页信息
     * @author 刘鑫
     * @date 2025-01-30
     */
    public PageResp<CategoryResp> getCategoryListForAdmin(Integer page, Integer size, String name, Boolean includeDeleted) {
        // 计算偏移量
        Integer offset = (page - 1) * size;

        // 查询分类列表
        List<CategoryResp> categoryList = categoriesMapper.selectCategoriesForAdmin(offset, size, name, includeDeleted);

        // 查询总数
        Integer total = categoriesMapper.countCategoriesForAdmin(name, includeDeleted);

        // 构建分页结果
        PageResp<CategoryResp> pageResp = new PageResp<>();
        pageResp.setRecords(categoryList);
        pageResp.setTotal(total.longValue());
        pageResp.setCurrent(page.longValue());
        pageResp.setSize(size.longValue());
        pageResp.setPages((long) Math.ceil((double) total / size));
        pageResp.setHasNext(page.longValue() < pageResp.getPages());
        pageResp.setHasPrevious(page.longValue() > 1);

        return pageResp;
    }

    /**
     * 根据ID获取分类
     * 查询指定ID的分类详细信息
     *
     * @param id 分类ID，不能为null
     * @return 分类信息，不存在时返回null
     * @author 刘鑫
     * @date 2025-01-30
     */
    public CategoryResp getById(Long id) {
        Categories category = super.getById(id);
        if (category == null) {
            return null;
        }

        CategoryResp categoryResp = new CategoryResp();
        categoryResp.setId(category.getId());
        categoryResp.setName(category.getName());
        categoryResp.setDescription(category.getDescription());
        categoryResp.setCreatedAt(category.getCreatedAt());
        categoryResp.setUpdatedAt(category.getUpdatedAt());
        // postCount 在单个查询时设为0，如需要可以单独查询
        categoryResp.setPostCount(0);

        return categoryResp;
    }

    /**
     * 根据分类名称查询分类
     * 用于检查分类名称是否已存在
     *
     * @param name 分类名称
     * @return 分类信息，不存在时返回null
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Cacheable(value = "categories", key = "#name")
    public Categories getCategoryByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        LambdaQueryWrapper<Categories> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Categories::getName, name.trim());
        return super.getOne(queryWrapper);
    }

    /**
     * 保存分类
     * 创建新分类，自动设置创建时间和更新时间
     *
     * @param categoryResp 分类信息，必须包含分类名称
     * @return 是否保存成功
     * @throws BusinessException 当分类名称已存在时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    @CacheEvict(value = "categories", allEntries = true)
    public boolean save(CategoryResp categoryResp) {
        // 检查分类名称是否已存在
        if (getCategoryByName(categoryResp.getName()) != null) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }
        
        Categories category = new Categories();
        category.setName(categoryResp.getName());
        category.setDescription(categoryResp.getDescription());
        return super.save(category);
    }

    /**
     * 根据ID更新分类
     * 更新分类信息，自动设置更新时间
     *
     * @param categoryResp 分类信息，必须包含有效的ID
     * @return 是否更新成功
     * @throws BusinessException 当分类不存在时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    @CacheEvict(value = "categories", allEntries = true)
    public boolean updateById(CategoryResp categoryResp) {
        Categories category = new Categories();
        category.setId(categoryResp.getId());
        category.setName(categoryResp.getName());
        category.setDescription(categoryResp.getDescription());
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
    @CacheEvict(value = "categories", allEntries = true)
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

    /**
     * 恢复已删除的分类
     * 将软删除的分类恢复为正常状态
     *
     * @param id 分类ID
     * @return 是否恢复成功
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "categories", allEntries = true)
    public boolean restoreCategory(Long id) {
        try {
            if (id == null) {
                return false;
            }

            // 使用原生SQL恢复分类，绕过MyBatis-Plus的逻辑删除限制
            int result = categoriesMapper.restoreCategoryById(id);

            log.info("恢复分类ID: {}, 结果: {}", id, result > 0 ? "成功" : "失败");
            return result > 0;
        } catch (Exception e) {
            log.error("恢复分类失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 彻底删除分类（物理删除）
     * 永久删除分类及其关联的文章数据
     *
     * @param id 分类ID
     * @return 是否删除成功
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "categories", allEntries = true)
    public boolean permanentDeleteCategory(Long id) {
        log.info("彻底删除分类 - 分类ID: {}", id);

        try {
            if (id == null) {
                return false;
            }

            // 获取该分类下的所有文章ID
            List<Long> postIds = postsMapper.selectList(
                new LambdaQueryWrapper<Posts>()
                    .eq(Posts::getCategoryId, id)
                    .select(Posts::getId)
            ).stream().map(Posts::getId).collect(Collectors.toList());

            if (!postIds.isEmpty()) {
                // 按正确顺序删除文章的关联数据
                for (Long postId : postIds) {
                    // 删除文章收藏记录
                    postFavoritesMapper.deleteByPostId(postId);
                    // 删除文章点赞记录
                    postLikesMapper.deleteByPostId(postId);
                    // 删除文章评论
                    // commentsMapper.deleteByPostId(postId);
                    commentsMapper.deleteChildrenByPostId(postId);
                    // 删除顶级评论
                    commentsMapper.deleteRootsByPostId(postId);
                    // 删除文章标签关联
                    postTagsMapper.deleteByPostId(postId);
                    // 删除文章附件关联
                    postAttachmentsMapper.deleteByPostId(postId);
                }

                // 物理删除该分类下的所有文章
                postsMapper.permanentDeleteByIds(postIds);
                log.info("彻底删除分类关联文章数量: {}", postIds.size());
            }

            // 物理删除分类
            int result = categoriesMapper.deleteBatchIds(Collections.singletonList(id));
            boolean success = result > 0;
            log.info("彻底删除分类{} - 分类ID: {}", success ? "成功" : "失败", id);
            return success;

        } catch (Exception e) {
            log.error("彻底删除分类失败 - 分类ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("彻底删除分类失败: " + e.getMessage());
        }
    }

    /**
     * 批量彻底删除分类（物理删除）
     * 永久删除多个分类及其关联的文章数据
     *
     * @param ids 分类ID列表
     * @return 是否删除成功
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "categories", allEntries = true)
    public boolean batchPermanentDeleteCategories(List<Long> ids) {
        log.info("批量彻底删除分类 - 分类数量: {}", ids.size());

        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            // 获取这些分类下的所有文章ID
            List<Long> postIds = postsMapper.selectList(
                new LambdaQueryWrapper<Posts>()
                    .in(Posts::getCategoryId, ids)
                    .select(Posts::getId)
            ).stream().map(Posts::getId).collect(Collectors.toList());

            if (!postIds.isEmpty()) {
                // 按正确顺序删除文章的关联数据
                for (Long postId : postIds) {
                    // 删除文章收藏记录
                    postFavoritesMapper.deleteByPostId(postId);
                    // 删除文章点赞记录
                    postLikesMapper.deleteByPostId(postId);
                    // 删除文章评论
                    // commentsMapper.deleteByPostId(postId);
                    commentsMapper.deleteChildrenByPostId(postId);
                    // 删除顶级评论
                    commentsMapper.deleteRootsByPostId(postId);
                    // 删除文章标签关联
                    postTagsMapper.deleteByPostId(postId);
                    // 删除文章附件关联
                    postAttachmentsMapper.deleteByPostId(postId);
                }

                // 物理删除这些分类下的所有文章
                postsMapper.permanentDeleteByIds(postIds);
                log.info("批量彻底删除分类关联文章数量: {}", postIds.size());
            }

            // 物理删除分类
            int result = categoriesMapper.deleteBatchIds(ids);
            boolean success = result > 0;
            log.info("批量彻底删除分类{} - 影响分类数: {}", success ? "成功" : "失败", ids.size());
            return success;

        } catch (Exception e) {
            log.error("批量彻底删除分类失败 - 错误: {}", e.getMessage(), e);
            throw new RuntimeException("批量彻底删除分类失败: " + e.getMessage());
        }
    }
}
