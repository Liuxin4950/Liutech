package chat.liuxin.liutech.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.CategoriesMapper;
import chat.liuxin.liutech.model.Categories;

/**
 * 分类服务类
 */
@Service
public class CategoriesService extends ServiceImpl<CategoriesMapper, Categories> {

    @Autowired
    private CategoriesMapper categoriesMapper;

    /**
     * 查询所有分类（包含文章数量）
     * @return 分类列表
     */
    public List<Categories> getAllCategoriesWithPostCount() {
        return categoriesMapper.selectCategoriesWithPostCount();
    }
}