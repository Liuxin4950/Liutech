package chat.liuxin.liutech.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.TagsMapper;
import chat.liuxin.liutech.model.Tags;

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
    public List<Tags> getAllTagsWithPostCount() {
        return tagsMapper.selectTagsWithPostCount();
    }

    /**
     * 根据文章ID查询标签列表
     * @param postId 文章ID
     * @return 标签列表
     */
    public List<Tags> getTagsByPostId(Long postId) {
        return tagsMapper.selectTagsByPostId(postId);
    }

    /**
     * 获取热门标签（按文章数量排序）
     * @param limit 限制数量
     * @return 热门标签列表
     */
    @Cacheable(value = "hotTags", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<Tags> getHotTags(Integer limit) {
        return tagsMapper.selectHotTags(limit);
    }

    /**
     * 根据ID查询标签详情（包含文章数量）
     * @param id 标签ID
     * @return 标签详情
     */
    public Tags getTagByIdWithPostCount(Long id) {
        return tagsMapper.selectTagByIdWithPostCount(id);
    }

    /**
     * 根据标签名字搜索标签（包含文章数量）
     * @param name 标签名字（支持模糊搜索）
     * @return 标签列表
     */
    public List<Tags> getTagsByName(String name) {
        return tagsMapper.selectTagsByName(name);
    }
}