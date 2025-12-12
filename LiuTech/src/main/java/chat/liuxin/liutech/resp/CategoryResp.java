package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.Categories;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分类响应类
 * 继承 Categories 实体类，只添加扩展字段
 * 用于管理端分类列表展示
 *
 * @author 刘鑫
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CategoryResp extends Categories {

    /**
     * 创建者用户名
     */
    private String creatorUsername;
}
