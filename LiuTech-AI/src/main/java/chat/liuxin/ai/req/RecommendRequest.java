package chat.liuxin.ai.req;

import lombok.Data;

/**
 * 推荐请求DTO
 * 用于前端触发推荐时传递参数
 */
@Data
public class RecommendRequest {

    /**
     * 推荐类型: search(搜索), category(分类), latest(最新), hot(热门)
     */
    private String type;

    /**
     * 搜索关键词 (type=search时使用)
     */
    private String keyword;

    /**
     * 分类ID (type=category时使用)
     */
    private Long categoryId;

    /**
     * 返回数量限制
     */
    private Integer limit;
}
