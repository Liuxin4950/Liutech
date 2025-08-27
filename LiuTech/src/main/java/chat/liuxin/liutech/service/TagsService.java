package chat.liuxin.liutech.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.TagsMapper;
import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.resl.PageResl;
import chat.liuxin.liutech.resl.TagResl;

/**
 * 标签服务类
 */
@Service
public class TagsService extends ServiceImpl<TagsMapper, Tags> {

    @Autowired
    private TagsMapper tagsMapper;

    /**
     * 查询所有标签（包含文章数量）
     * @return 标签列表
     */
    public List<TagResl> getAllTagsWithPostCount() {
        return tagsMapper.selectTagsWithPostCount();
    }

    /**
     * 根据文章ID查询标签列表
     * @param postId 文章ID
     * @return 标签列表
     */
    public List<TagResl> getTagsByPostId(Long postId) {
        return tagsMapper.selectTagsByPostId(postId);
    }

    /**
     * 获取热门标签（按文章数量排序）
     * @param limit 限制数量
     * @return 热门标签列表
     */
    @Cacheable(value = "hotTags", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<TagResl> getHotTags(Integer limit) {
        return tagsMapper.selectHotTags(limit);
    }

    /**
     * 根据ID查询标签详情（包含文章数量）
     * @param id 标签ID
     * @return 标签详情
     */
    public TagResl getTagByIdWithPostCount(Long id) {
        return tagsMapper.selectTagByIdWithPostCount(id);
    }

    /**
     * 根据标签名字搜索标签（包含文章数量）
     * @param name 标签名字（支持模糊搜索）
     * @return 标签列表
     */
    public List<TagResl> getTagsByName(String name) {
        return tagsMapper.selectTagsByName(name);
    }

    /**
     * 管理端分页查询标签列表
     * @param page 页码
     * @param size 每页大小
     * @param name 标签名称（可选，模糊搜索）
     * @return 分页标签列表
     */
    public PageResl<TagResl> getTagListForAdmin(Integer page, Integer size, String name) {
        // 计算偏移量
        Integer offset = (page - 1) * size;
        
        // 查询标签列表
        List<TagResl> tagList = tagsMapper.selectTagsForAdmin(offset, size, name);
        
        // 查询总数
        Integer total = tagsMapper.countTagsForAdmin(name);
        
        // 构建分页结果
        PageResl<TagResl> pageResl = new PageResl<>();
        pageResl.setRecords(tagList);
        pageResl.setTotal(total.longValue());
        pageResl.setCurrent(page.longValue());
        pageResl.setSize(size.longValue());
        pageResl.setPages((long) Math.ceil((double) total / size));
        pageResl.setHasNext(page.longValue() < pageResl.getPages());
        pageResl.setHasPrevious(page.longValue() > 1);
        
        return pageResl;
    }

    /**
     * 根据ID查询标签详情（返回TagResl）
     * @param id 标签ID
     * @return 标签详情
     */
    public TagResl getById(Long id) {
        Tags tag = super.getById(id);
        if (tag == null) {
            return null;
        }
        
        TagResl tagResl = new TagResl();
        tagResl.setId(tag.getId());
        tagResl.setName(tag.getName());
        tagResl.setDescription(tag.getDescription());
        tagResl.setCreatedAt(tag.getCreatedAt());
        tagResl.setUpdatedAt(tag.getUpdatedAt());
        // postCount 在单个查询时设为0，如需要可以单独查询
        tagResl.setPostCount(0);
        
        return tagResl;
    }
}