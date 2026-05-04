package chat.liuxin.ai.controller;

import chat.liuxin.ai.mcp.BlogMcpTools;
import chat.liuxin.ai.dto.PostSummaryDTO;
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
 * Legacy AI 推荐控制器。
 *
 * Web/Admin 看板娘主链路已统一通过 /ai/agent/chat 或 /ai/agent/stream 返回 article-results。
 * 本接口只作为历史 REST 能力保留，新看板娘功能不得再依赖它。
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

    private static final String LEGACY_ROUTE_HEADER = "X-LiuTech-AI-Route";
    private static final String LEGACY_RECOMMEND_ROUTE = "legacy-recommend";

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
                    List<PostSummaryDTO> searchResults = blogMcpTools.searchPosts(request.getKeyword(), limit);
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
                    List<PostSummaryDTO> latestPosts = blogMcpTools.getLatestPosts(limit);
                    response = RecommendResponse.latest(latestPosts);
                    break;

                case "hot":
                    // 热门推荐
                    List<PostSummaryDTO> hotPosts = blogMcpTools.getHotPosts(limit);
                    response = RecommendResponse.hot(hotPosts);
                    break;

                default:
                    log.warn("未知的推荐类型: {}", request.getType());
                    return ResponseEntity.badRequest()
                            .header(LEGACY_ROUTE_HEADER, LEGACY_RECOMMEND_ROUTE)
                            .build();
            }

            log.info("推荐内容获取成功 - type={}, posts={}",
                    request.getType(), response.getPosts().size());
            return ResponseEntity.ok()
                    .header(LEGACY_ROUTE_HEADER, LEGACY_RECOMMEND_ROUTE)
                    .body(response);

        } catch (Exception e) {
            log.error("获取推荐内容失败", e);
            return ResponseEntity.internalServerError()
                    .header(LEGACY_ROUTE_HEADER, LEGACY_RECOMMEND_ROUTE)
                    .build();
        }
    }
}
