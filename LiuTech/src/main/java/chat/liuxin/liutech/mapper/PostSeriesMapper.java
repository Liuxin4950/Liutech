package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.PostSeries;
import chat.liuxin.liutech.resp.PostSeriesResp;

/**
 * 文章系列 Mapper 接口
 *
 * @author 刘鑫
 */
@Mapper
public interface PostSeriesMapper extends BaseMapper<PostSeries> {

    /**
     * 查询所有系列（包含已发布文章数量），web 端列表用
     *
     * @return 系列列表
     */
    List<PostSeriesResp> selectSeriesWithPostCount();

    /**
     * 管理端分页查询系列列表（包含创建者信息与文章数）
     *
     * @param offset        偏移量
     * @param limit         限制数量
     * @param name          系列名称（可选，模糊搜索）
     * @param includeDeleted 是否包含已删除系列
     * @return 系列列表
     */
    List<PostSeriesResp> selectSeriesForAdmin(@Param("offset") Integer offset,
                                              @Param("limit") Integer limit,
                                              @Param("name") String name,
                                              @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 管理端查询系列总数
     *
     * @param name          系列名称（可选，模糊搜索）
     * @param includeDeleted 是否包含已删除系列
     * @return 总数
     */
    Integer countSeriesForAdmin(@Param("name") String name, @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 根据ID查询系列详情（含已发布文章数）
     *
     * @param id 系列ID
     * @return 系列详情
     */
    PostSeriesResp selectSeriesDetailById(@Param("id") Long id);

    /**
     * 恢复已删除的系列
     *
     * @param id 系列ID
     * @return 影响的行数
     */
    int restoreSeriesById(@Param("id") Long id);

    /**
     * 根据ID列表物理删除系列
     *
     * @param ids 系列ID列表
     * @return 影响的行数
     */
    int deleteBatchIds(@Param("ids") List<Long> ids);
}
