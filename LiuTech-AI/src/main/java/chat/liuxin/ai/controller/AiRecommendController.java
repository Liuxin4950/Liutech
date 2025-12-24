package chat.liuxin.ai.controller;

import chat.liuxin.ai.mcp.BlogMcpTools;
import chat.liuxin.ai.req.RecommendRequest;
import chat.liuxin.ai.resp.RecommendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI推荐控制器
 * 提供文章推荐相关的REST API接口
 *
 * 核心功能：
 * 根据前端传递的参数，调用MCP工具获取推荐内容
 *
 * 作者：刘鑫
 * 时间：2025-12-23
 */
@Slf4j
@RestController
@RequestMapping("/ai/recommend")
@RequiredArgsConstructor
public class AiRecommendController {

    private final BlogMcpTools blogMcpTools;

    /**
     * 获取推荐内容
     *
     * 业务流程：
     * 1. 接收前端传递的推荐参数（类型、关键词、分类ID等）
     * 2. 根据类型调用对应的MCP工具
     * 3. 构建推荐响应返回给前端
     *
     * @param request 推荐请求参数
     * @return 推荐内容
     */
    @PostMapping
    public ResponseEntity<RecommendResponse> getRecommendations(@RequestBody RecommendRequest request) {
        log.info("获取推荐内容 - type={}, keyword={}, categoryId={}",
                request.getType(), request.getKeyword(), request.getCategoryId());

        try {
            int limit = request.getLimit() != null ? request.getLimit() : 5;
            RecommendResponse response;

            switch (request.getType().toLowerCase()) {
                case "search":
                    // 搜索推荐
                    List searchResults = blogMcpTools.searchPosts(request.getKeyword(), limit);
                    response = RecommendResponse.search(request.getKeyword(), searchResults);
                    break;

                case "category":
                    // 分类推荐
                    var categoryPosts = blogMcpTools.getPostsByCategory(request.getCategoryId(), limit);
                    // 获取分类信息
                    var categories = blogMcpTools.getAllCategories();
                    var category = categories.stream()
                            .filter(c -> c.getId().equals(request.getCategoryId()))
                            .findFirst()
                            .orElse(null);
                    response = RecommendResponse.category(category, categoryPosts);
                    break;

                case "latest":
                    // 最新推荐
                    List latestPosts = blogMcpTools.getLatestPosts(limit);
                    response = RecommendResponse.latest(latestPosts);
                    break;

                case "hot":
                    // 热门推荐
                    List hotPosts = blogMcpTools.getHotPosts(limit);
                    response = RecommendResponse.hot(hotPosts);
                    break;

                default:
                    log.warn("未知的推荐类型: {}", request.getType());
                    return ResponseEntity.badRequest().build();
            }

            log.info("推荐内容获取成功 - type={}, posts={}",
                    request.getType(), response.getPosts().size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取推荐内容失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
