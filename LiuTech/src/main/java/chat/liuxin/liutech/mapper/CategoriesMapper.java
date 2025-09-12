package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.Categories;
import chat.liuxin.liutech.resl.CategoryResl;

/**
 * 分类Mapper接口
 */
@Mapper
public interface CategoriesMapper extends BaseMapper<Categories> {

    /**
     * 查询所有分类（包含文章数量）
     * @return 分类列表
     */
    List<CategoryResl> selectCategoriesWithPostCount();

    /**
     * 管理端分页查询分类列表（包含创建者信息）
     * @param offset 偏移量
     * @param limit 限制数量
     * @param name 分类名称（可选，模糊搜索）
     * @param includeDeleted 是否包含已删除分类
     * @return 分类列表
     */
    List<chat.liuxin.liutech.resl.CategoryResl> selectCategoriesForAdmin(@Param("offset") Integer offset, 
                                                                         @Param("limit") Integer limit, 
                                                                         @Param("name") String name,
                                                                         @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 管理端查询分类总数
     * @param name 分类名称（可选，模糊搜索）
     * @param includeDeleted 是否包含已删除分类
     * @return 总数
     */
    Integer countCategoriesForAdmin(@Param("name") String name, @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 恢复已删除的分类
     * @param id 分类ID
     * @return 影响的行数
     */
    int restoreCategoryById(@Param("id") Long id);

    /**
     * 根据ID列表物理删除分类
     * @param ids 分类ID列表
     * @return 影响的行数
     */
    int deleteBatchIds(@Param("ids") List<Long> ids);
}