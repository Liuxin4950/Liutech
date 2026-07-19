package chat.liuxin.liutech.vo;

import lombok.Data;

/**
 * 文章-标签关联行（批量查询标签用）
 *
 * 对应 selectTagsByPostIds 的结果集，含 postId 用于按文章分组，替代 Map<String,Object>。
 *
 * @author 刘鑫
 */
@Data
public class PostTagRowVO {
    /** 文章ID */
    private Long postId;
    /** 标签ID */
    private Long id;
    /** 标签名 */
    private String name;
}
