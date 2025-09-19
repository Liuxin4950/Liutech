package chat.liuxin.liutech.resp;

import java.util.Date;

import lombok.Data;

/**
 * 文章创建响应
 */
@Data
public class PostCreateResp {
    /**
     * 文章ID
     */
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章状态
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createdAt;
}
