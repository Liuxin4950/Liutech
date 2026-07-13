package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.model.Carousel;

/**
 * 轮播图Mapper接口
 * @author 刘鑫
 */
@Mapper
public interface CarouselMapper extends BaseMapper<Carousel> {

    /**
     * 查询有效的轮播图列表（已启用）
     * @param page 分页参数
     * @return 轮播图列表
     */
    @Select("SELECT * FROM carousels " +
            "WHERE status = 1 " +
            "AND deleted_at IS NULL " +
            "ORDER BY sort_order DESC, created_at DESC")
    IPage<Carousel> selectValidCarousels(Page<Carousel> page);

    /**
     * 查询所有启用的轮播图（用于前台展示）
     * @return 轮播图列表
     */
    @Select("SELECT * FROM carousels " +
            "WHERE status = 1 " +
            "AND deleted_at IS NULL " +
            "ORDER BY sort_order DESC, created_at DESC")
    List<Carousel> selectActiveCarousels();

    /**
     * 查询所有轮播图（包含已删除，用于管理端）
     * @param page 分页参数
     * @param status 状态筛选（可选）
     * @param includeDeleted 是否包含已删除
     * @return 轮播图分页数据
     */
    @Select("<script>" +
            "SELECT * FROM carousels " +
            "<where>" +
            "  <if test='status != null'>AND status = #{status}</if>" +
            "  <if test='!includeDeleted'>AND deleted_at IS NULL</if>" +
            "</where>" +
            "ORDER BY sort_order DESC, created_at DESC" +
            "</script>")
    IPage<Carousel> selectAllCarouselsWithDeleted(Page<Carousel> page,
                                                   @Param("status") Integer status,
                                                   @Param("includeDeleted") boolean includeDeleted);

    /**
     * 根据ID物理删除轮播图
     * @param id 轮播图ID
     * @return 影响行数
     */
    int permanentDeleteById(@Param("id") Long id);

    /**
     * 批量物理删除轮播图
     * @param ids 轮播图ID列表
     * @return 影响行数
     */
    int batchPermanentDelete(@Param("ids") List<Long> ids);

    /**
     * 根据ID查询轮播图（包含已删除，用于恢复操作）
     * @param id 轮播图ID
     * @return 轮播图
     */
    @Select("SELECT * FROM carousels WHERE id = #{id}")
    Carousel selectByIdWithDeleted(@Param("id") Long id);

    @Select("SELECT image_url FROM carousels WHERE image_url IS NOT NULL AND deleted_at IS NULL")
    List<String> selectAllImageUrls();

    /**
     * 恢复已删除的轮播图（原生SQL，绕过 @TableLogic）
     * @param id 轮播图ID
     * @return 影响的行数
     */
    int restoreCarouselById(@Param("id") Long id);
}
