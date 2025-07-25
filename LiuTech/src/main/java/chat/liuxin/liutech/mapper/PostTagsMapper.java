package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.PostTags;

/**
 * 文章标签关联Mapper接口
 */
@Mapper
public interface PostTagsMapper extends BaseMapper<PostTags> {

    /**
     * 批量插入文章标签关联
     * @param postTags 文章标签关联列表
     * @return 插入数量
     */
    int batchInsert(@Param("postTags") List<PostTags> postTags);

    /**
     * 根据文章ID删除所有标签关联
     * @param postId 文章ID
     * @return 删除数量
     */
    int deleteByPostId(@Param("postId") Long postId);

    /**
     * 根据标签ID删除所有文章关联
     * @param tagId 标签ID
     * @return 删除数量
     */
    int deleteByTagId(@Param("tagId") Long tagId);
}