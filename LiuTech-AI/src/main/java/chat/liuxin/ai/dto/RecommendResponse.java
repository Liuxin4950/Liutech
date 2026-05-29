package chat.liuxin.ai.dto;

import chat.liuxin.ai.dto.CategoryDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import lombok.Data;

import java.util.List;

/**
 * 推荐响应DTO
 * 用于返回推荐内容给前端渲染
 */
@Data
public class RecommendResponse {

    /**
     * 推荐类型: search, category, latest, hot
     */
    private String type;

    /**
     * 搜索关键词（如果有）
     */
    private String keyword;

    /**
     * 分类信息（type=category时使用）
     */
    private CategoryDTO category;

    /**
     * 推荐的文章列表
     */
    private List<PostSummaryDTO> posts;

    /**
     * 推荐理由（用于前端展示）
     */
    private String reason;

    /**
     * 创建搜索类型的推荐响应
     */
    public static RecommendResponse search(String keyword, List<PostSummaryDTO> posts) {
        RecommendResponse response = new RecommendResponse();
        response.setType("search");
        response.setKeyword(keyword);
        response.setPosts(posts);
        response.setReason("搜索关键词: " + keyword);
        return response;
    }

    /**
     * 创建分类推荐的响应
     */
    public static RecommendResponse category(CategoryDTO category, List<PostSummaryDTO> posts) {
        RecommendResponse response = new RecommendResponse();
        response.setType("category");
        response.setCategory(category);
        response.setPosts(posts);
        response.setReason("分类: " + category.getName());
        return response;
    }

    /**
     * 创建最新文章的推荐响应
     */
    public static RecommendResponse latest(List<PostSummaryDTO> posts) {
        RecommendResponse response = new RecommendResponse();
        response.setType("latest");
        response.setPosts(posts);
        response.setReason("最新发布的文章");
        return response;
    }

    /**
     * 创建热门文章的推荐响应
     */
    public static RecommendResponse hot(List<PostSummaryDTO> posts) {
        RecommendResponse response = new RecommendResponse();
        response.setType("hot");
        response.setPosts(posts);
        response.setReason("热门文章（按评论数）");
        return response;
    }
}
