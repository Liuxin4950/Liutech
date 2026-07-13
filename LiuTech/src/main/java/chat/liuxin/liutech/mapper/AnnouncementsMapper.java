package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.model.Announcements;

/**
 * 公告Mapper接口
 * @author 刘鑫
 */
@Mapper
public interface AnnouncementsMapper extends BaseMapper<Announcements> {

    /**
     * 查询有效的公告列表（已发布且在有效期内）
     * @param page 分页参数
     * @return 公告列表
     */
    @Select("SELECT * FROM announcements " +
            "WHERE status = 1 " +
            "AND deleted_at IS NULL " +
            "AND (start_time IS NULL OR start_time <= NOW()) " +
            "AND (end_time IS NULL OR end_time >= NOW()) " +
            "ORDER BY is_top DESC, priority DESC, created_at DESC")
    IPage<Announcements> selectValidAnnouncements(Page<Announcements> page);

    /**
     * 查询置顶公告列表
     * @param limit 限制数量
     * @return 置顶公告列表
     */
    @Select("SELECT * FROM announcements " +
            "WHERE status = 1 " +
            "AND is_top = 1 " +
            "AND deleted_at IS NULL " +
            "AND (start_time IS NULL OR start_time <= NOW()) " +
            "AND (end_time IS NULL OR end_time >= NOW()) " +
            "ORDER BY priority DESC, created_at DESC " +
            "LIMIT #{limit}")
    List<Announcements> selectTopAnnouncements(@Param("limit") Integer limit);

    /**
     * 增加公告查看次数
     * @param id 公告ID
     * @return 影响行数
     */
    @Update("UPDATE announcements SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    /**
     * 查询最新公告列表
     * @param limit 限制数量
     * @return 最新公告列表
     */
    @Select("SELECT * FROM announcements " +
            "WHERE status = 1 " +
            "AND deleted_at IS NULL " +
            "AND (start_time IS NULL OR start_time <= NOW()) " +
            "AND (end_time IS NULL OR end_time >= NOW()) " +
            "ORDER BY created_at DESC " +
            "LIMIT #{limit}")
    List<Announcements> selectLatestAnnouncements(@Param("limit") Integer limit);

    /**
     * 物理删除单条公告（绕过 @TableLogic）
     * @param id 公告ID
     * @return 影响行数
     */
    /**
     * 根据ID查询公告（绕过 @TableLogic，包含已删除的公告）
     * @param id 公告ID
     * @return 公告实体（可能为null）
     */
    @Select("SELECT * FROM announcements WHERE id = #{id}")
    Announcements selectAllById(@Param("id") Long id);

    @Delete("DELETE FROM announcements WHERE id = #{id}")
    int permanentDeleteById(@Param("id") Long id);

    /**
     * 批量物理删除公告（绕过 @TableLogic）
     * @param ids 公告ID列表
     * @return 影响行数
     */
    @Delete("<script>DELETE FROM announcements WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int batchPermanentDeleteByIds(@Param("ids") List<Long> ids);
}