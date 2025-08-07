package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.Tags;

/**
 * 标签Mapper接口
 */
@Mapper
public interface TagsMapper extends BaseMapper<Tags> {

    /**
     * 查询所有标签（包含文章数量）
     * @return 标签列表
     */
    List<Tags> selectTagsWithPostCount();

    /**
     * 根据文章ID查询标签列表
     * @param postId 文章ID
     * @return 标签列表
     */
    List<Tags> selectTagsByPostId(@Param("postId") Long postId);

    /**
     * 查询热门标签（根据文章数量排序）
     * @param limit 限制数量
     * @return 热门标签列表
     */
    List<Tags> selectHotTags(@Param("limit") Integer limit);

    /**
     * 根据ID查询标签详情（包含文章数量）
     * @param id 标签ID
     * @return 标签详情
     */
    Tags selectTagByIdWithPostCount(@Param("id") Long id);

    /**
     * 根据标签名字搜索标签（包含文章数量）
     * @param name 标签名字（支持模糊搜索）
     * @return 标签列表
     */
    List<Tags> selectTagsByName(@Param("name") String name);
}