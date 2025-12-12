package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.Tags;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标签响应类
 * 继承 Tags 实体类，只添加扩展字段
 * 用于管理端标签列表展示
 *
 * @author 刘鑫
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TagResp extends Tags {

    /**
     * 创建者用户名
     */
    private String creatorUsername;
}
