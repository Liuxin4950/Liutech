package chat.liuxin.liutech.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.Images;

/**
 * 图片表 Mapper 接口
 * @author 刘鑫
 */
@Mapper
public interface ImagesMapper extends BaseMapper<Images> {

    /**
     * 根据文件哈希查询图片（排除软删除）
     *
     * @param fileHash 文件哈希值
     * @return 匹配的记录，不存在返回null
     */
    @Select("SELECT * FROM images WHERE file_hash = #{fileHash} AND deleted_at IS NULL LIMIT 1")
    Images selectByHash(@Param("fileHash") String fileHash);

    /**
     * 增加引用计数
     *
     * @param id 图片ID
     * @param delta 增量（可为负数）
     * @return 影响行数
     */
    @Update("UPDATE images SET usage_count = GREATEST(0, usage_count + #{delta}), updated_at = NOW() WHERE id = #{id} AND deleted_at IS NULL")
    Integer incrementUsageCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 物理删除图片记录（绕过 @TableLogic 软删除）
     *
     * @param id 图片ID
     * @return 影响行数
     */
    @Delete("DELETE FROM images WHERE id = #{id}")
    Integer permanentDeleteById(@Param("id") Long id);

    @Update("UPDATE images SET usage_count = 0, updated_at = NOW() WHERE deleted_at IS NULL")
    Integer resetUsageCount();
}
