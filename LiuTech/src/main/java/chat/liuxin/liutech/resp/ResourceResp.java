package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.Resources;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资源响应类
 * 继承 Resources 实体类，只添加扩展字段
 * 用于管理端资源列表展示
 *
 * @author 刘鑫
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceResp extends Resources {

    /**
     * 上传者用户名
     */
    private String uploaderUsername;
}
