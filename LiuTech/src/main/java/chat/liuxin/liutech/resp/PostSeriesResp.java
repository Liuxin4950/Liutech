package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.PostSeries;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章系列响应类
 * 继承 PostSeries 实体类，添加管理端展示用的扩展字段
 *
 * @author 刘鑫
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostSeriesResp extends PostSeries {

    /**
     * 创建者用户名
     */
    private String creatorUsername;
}
