package chat.liuxin.liutech.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import chat.liuxin.liutech.model.Categories;

/**
 * 分类Mapper接口
 */
@Mapper
public interface CategoriesMapper extends BaseMapper<Categories> {

    /**
     * 查询所有分类（包含文章数量）
     * @return 分类列表
     */
    List<Categories> selectCategoriesWithPostCount();
}