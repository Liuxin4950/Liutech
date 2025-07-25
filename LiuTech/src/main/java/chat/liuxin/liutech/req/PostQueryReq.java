package chat.liuxin.liutech.req;

import lombok.Data;

/**
 * 文章查询请求
 */
@Data
public class PostQueryReq {
    /**
     * 页码（从1开始）
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 搜索关键词
     */
    private String keyword;

    /**
     * 排序方式（latest: 最新, hot: 热门）
     */
    private String sort = "latest";
}