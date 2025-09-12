package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.Tags;
import chat.liuxin.liutech.resl.TagResl;

/**
 * 标签Mapper接口
 */
@Mapper
public interface TagsMapper extends BaseMapper<Tags> {

    /**
     * 查询所有标签（包含文章数量）
     * @return 标签列表
     */
    List<TagResl> selectTagsWithPostCount();

    /**
     * 根据文章ID查询标签列表
     * @param postId 文章ID
     * @return 标签列表
     */
    List<TagResl> selectTagsByPostId(@Param("postId") Long postId);

    /**
     * 查询热门标签（根据文章数量排序）
     * @param limit 限制数量
     * @return 热门标签列表
     */
    List<TagResl> selectHotTags(@Param("limit") Integer limit);

    /**
     * 根据ID查询标签详情（包含文章数量）
     * @param id 标签ID
     * @return 标签详情
     */
    TagResl selectTagByIdWithPostCount(@Param("id") Long id);

    /**
     * 根据标签名字搜索标签（包含文章数量）
     * @param name 标签名字（支持模糊搜索）
     * @return 标签列表
     */
    List<TagResl> selectTagsByName(@Param("name") String name);

    /**
     * 管理端分页查询标签列表（包含创建者信息）
     * @param offset 偏移量
     * @param limit 限制数量
     * @param name 标签名称（可选，模糊搜索）
     * @param includeDeleted 是否包含已删除标签
     * @return 标签列表
     */
    List<chat.liuxin.liutech.resl.TagResl> selectTagsForAdmin(@Param("offset") Integer offset, 
                                                              @Param("limit") Integer limit, 
                                                              @Param("name") String name,
                                                              @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 管理端查询标签总数
     * @param name 标签名称（可选，模糊搜索）
     * @param includeDeleted 是否包含已删除标签
     * @return 总数
     */
    Integer countTagsForAdmin(@Param("name") String name, @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 恢复已删除的标签（绕过逻辑删除限制）
     * @param id 标签ID
     * @return 影响的行数
     */
    int restoreTagById(@Param("id") Long id);

    /**
     * 根据ID列表物理删除标签
     * @param ids 标签ID列表
     * @return 影响的行数
     */
    int deleteBatchIds(@Param("ids") List<Long> ids);
}