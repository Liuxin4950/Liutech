package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.Posts;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章创建响应
 * 继承 Posts 实体类，直接使用所有基础字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostCreateResp extends Posts {
    // 继承 Posts 的所有字段，无需额外扩展字段
}
