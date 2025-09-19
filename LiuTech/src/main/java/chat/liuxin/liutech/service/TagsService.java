package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.mapper.PostTagsMapper;
import chat.liuxin.liutech.mapper.TagsMapper;
import chat.liuxin.liutech.model.PostTags;
import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.TagResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 标签服务类
 * 提供标签的增删改查、统计、搜索等核心业务功能
 *
 * @author 刘鑫
 * @date 2025-01-30
 */
@Slf4j
@Service
public class TagsService extends ServiceImpl<TagsMapper, Tags> {

    @Autowired
    private TagsMapper tagsMapper;

    @Autowired
    private PostTagsMapper postTagsMapper;

    /**
     * 查询所有标签（包含文章数量）
     * 获取系统中所有标签及其关联的文章数量统计
     *
     * @return 标签列表，包含标签信息和文章数量
     * @author 刘鑫
     * @date 2025-01-30
     */
    public List<TagResp> getAllTagsWithPostCount() {
        return tagsMapper.selectTagsWithPostCount();
    }

    /**
     * 根据文章ID查询标签列表
     * @param postId 文章ID
     * @return 标签列表
     */
    public List<TagResp> getTagsByPostId(Long postId) {
        return tagsMapper.selectTagsByPostId(postId);
    }

    /**
     * 获取热门标签（按文章数量排序）
     * @param limit 限制数量
     * @return 热门标签列表
     */
    @Cacheable(value = "hotTags", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<TagResp> getHotTags(Integer limit) {
        return tagsMapper.selectHotTags(limit);
    }

    /**
     * 根据ID查询标签详情（包含文章数量）
     * 获取标签的详细信息，包括标签名称和关联的文章数量
     *
     * @param id 标签ID
     * @return 标签详情，包含文章数量统计
     */
    public TagResp getTagByIdWithPostCount(Long id) {
        return tagsMapper.selectTagByIdWithPostCount(id);
    }

    /**
     * 根据标签名字搜索标签（包含文章数量）
     * 支持模糊搜索标签名称，返回匹配的标签列表及其文章数量
     *
     * @param name 标签名字，支持模糊搜索
     * @return 匹配的标签列表，包含文章数量统计
     */
    public List<TagResp> getTagsByName(String name) {
        return tagsMapper.selectTagsByName(name);
    }

    /**
     * 管理端分页查询标签列表
     * 管理员专用功能，支持按标签名称搜索和分页查询
     *
     * @param page 页码，从1开始
     * @param size 每页大小，建议10-50之间
     * @param name 标签名称（可选，模糊搜索）
     * @param includeDeleted 是否包含已删除标签
     * @return 分页结果，包含标签列表和分页信息
     * @author 刘鑫
     * @date 2025-01-30
     */
    public PageResp<TagResp> getTagListForAdmin(Integer page, Integer size, String name, Boolean includeDeleted) {
        // 计算偏移量
        Integer offset = (page - 1) * size;

        // 查询标签列表
        List<TagResp> tagList = tagsMapper.selectTagsForAdmin(offset, size, name, includeDeleted);

        // 查询总数
        Integer total = tagsMapper.countTagsForAdmin(name, includeDeleted);

        // 构建分页结果
        PageResp<TagResp> pageResp = new PageResp<>();
        pageResp.setRecords(tagList);
        pageResp.setTotal(total.longValue());
        pageResp.setCurrent(page.longValue());
        pageResp.setSize(size.longValue());
        pageResp.setPages((long) Math.ceil((double) total / size));
        pageResp.setHasNext(page.longValue() < pageResp.getPages());
        pageResp.setHasPrevious(page.longValue() > 1);

        return pageResp;
    }

    /**
     * 根据ID查询标签详情（返回TagResl）
     * @param id 标签ID
     * @return 标签详情
     */
    public TagResp getById(Long id) {
        Tags tag = super.getById(id);
        if (tag == null) {
            return null;
        }

        TagResp tagResp = new TagResp();
        tagResp.setId(tag.getId());
        tagResp.setName(tag.getName());
        tagResp.setDescription(tag.getDescription());
        tagResp.setCreatedAt(tag.getCreatedAt());
        tagResp.setUpdatedAt(tag.getUpdatedAt());
        // postCount 在单个查询时设为0，如需要可以单独查询
        tagResp.setPostCount(0);

        return tagResp;
    }

    /**
     * 保存标签（接受TagResl参数）
     * 创建新标签，自动设置创建时间和更新时间
     *
     * @param tagResp 标签信息，必须包含标签名称
     * @return 是否保存成功
     * @throws BusinessException 当标签名称已存在时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    @CacheEvict(value = "hotTags", allEntries = true)
    public boolean save(TagResp tagResp) {
        Tags tag = new Tags();
        tag.setName(tagResp.getName());
        tag.setDescription(tagResp.getDescription());
        return super.save(tag);
    }

    /**
     * 根据ID更新标签（接受TagResl参数）
     * 更新标签信息，自动设置更新时间
     *
     * @param tagResp 标签信息，必须包含有效的ID
     * @return 是否更新成功
     * @throws BusinessException 当标签不存在时抛出
     * @author 刘鑫
     * @date 2025-01-30
     */
    @CacheEvict(value = "hotTags", allEntries = true)
    public boolean updateById(TagResp tagResp) {
        Tags tag = new Tags();
        tag.setId(tagResp.getId());
        tag.setName(tagResp.getName());
        tag.setDescription(tagResp.getDescription());
        return super.updateById(tag);
    }

    /**
     * 批量删除标签（管理端）- 安全删除
     * 检查是否有关联的文章，如果有则先删除关联关系
     *
     * @author 刘鑫
     * @date 2025-01-17
     * @param ids 标签ID列表
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "hotTags", allEntries = true)
    public boolean removeByIds(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            // 先删除标签与文章的关联关系
            LambdaQueryWrapper<PostTags> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(PostTags::getTagId, ids);
            postTagsMapper.delete(queryWrapper);

            // 使用软删除
            LambdaUpdateWrapper<Tags> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.in(Tags::getId, ids)
                    .set(Tags::getDeletedAt, new Date());

            int result = tagsMapper.update(null, updateWrapper);
            return result > 0;
        } catch (Exception e) {
            log.error("批量删除标签失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 恢复已删除的标签
     * 将软删除的标签恢复为正常状态
     *
     * @param id 标签ID
     * @return 是否恢复成功
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "hotTags", allEntries = true)
    public boolean restoreTag(Long id) {
        try {
            if (id == null) {
                return false;
            }

            // 使用原生SQL恢复标签，绕过MyBatis-Plus的逻辑删除限制
            int result = tagsMapper.restoreTagById(id);

            log.info("恢复标签ID: {}, 结果: {}", id, result > 0 ? "成功" : "失败");
            return result > 0;
        } catch (Exception e) {
            log.error("恢复标签失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 彻底删除标签（物理删除）
     * 永久删除标签及其关联的文章标签关系
     *
     * @param id 标签ID
     * @return 是否删除成功
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "hotTags", allEntries = true)
    public boolean permanentDeleteTag(Long id) {
        log.info("彻底删除标签 - 标签ID: {}", id);

        try {
            if (id == null) {
                return false;
            }

            // 先删除标签与文章的关联关系
            LambdaQueryWrapper<PostTags> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PostTags::getTagId, id);
            int relationResult = postTagsMapper.delete(queryWrapper);
            log.info("彻底删除标签关联关系数量: {}", relationResult);

            // 物理删除标签（使用XML中的deleteBatchIds以确保物理删除）
            int result = tagsMapper.deleteBatchIds(java.util.Collections.singletonList(id));
            boolean success = result > 0;
            log.info("彻底删除标签{} - 标签ID: {}", success ? "成功" : "失败", id);
            return success;

        } catch (Exception e) {
            log.error("彻底删除标签失败 - 标签ID: {}, 错误: {}", id, e.getMessage(), e);
            throw new RuntimeException("彻底删除标签失败: " + e.getMessage());
        }
    }

    /**
     * 批量彻底删除标签（物理删除）
     * 永久删除多个标签及其关联的文章标签关系
     *
     * @param ids 标签ID列表
     * @return 是否删除成功
     * @author 刘鑫
     * @date 2025-01-30
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "hotTags", allEntries = true)
    public boolean batchPermanentDeleteTags(List<Long> ids) {
        log.info("批量彻底删除标签 - 标签数量: {}", ids.size());

        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }

            // 先删除标签与文章的关联关系
            LambdaQueryWrapper<PostTags> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(PostTags::getTagId, ids);
            int relationResult = postTagsMapper.delete(queryWrapper);
            log.info("批量彻底删除标签关联关系数量: {}", relationResult);

            // 物理删除标签
            int result = tagsMapper.deleteBatchIds(ids);
            boolean success = result > 0;
            log.info("批量彻底删除标签{} - 影响标签数: {}", success ? "成功" : "失败", ids.size());
            return success;

        } catch (Exception e) {
            log.error("批量彻底删除标签失败 - 错误: {}", e.getMessage(), e);
            throw new RuntimeException("批量彻底删除标签失败: " + e.getMessage());
        }
    }
}
