package chat.liuxin.ai.client;

import chat.liuxin.ai.dto.CategoryDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 博客API客户端
 * 调用主服务API获取文章数据
 *
 * Docker部署说明：
 * - 容器间通信使用容器名，如 http://backend:8080
 * - 本地开发可使用 http://localhost:8080
 */
@Slf4j
@Component
public class BlogApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 博客API地址
     * Docker环境使用容器名: http://backend:8080
     * 本地开发使用: http://localhost:8080
     * 可通过环境变量 BLOG_API_URL 覆盖
     */
    @Value("${blog.api.url:http://backend:8080}")
    private String blogApiUrl;

    public BlogApiClient() {
        this.restTemplate = new RestTemplate();//用于调用博客API
        this.objectMapper = new ObjectMapper();//用于解析JSON响应
    }

    /**
     * 获取文章详情
     */
    public PostDetailDTO getPostDetail(Long postId) {
        try {
            String url = blogApiUrl + "/posts/" + postId;
            log.debug("调用博客API获取文章详情: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                return parsePostDetail(data);
            }

            log.warn("获取文章详情失败: postId={}", postId);
            return null;
        } catch (Exception e) {
            log.error("调用博客API异常: postId={}", postId, e);
            return null;
        }
    }

    /**
     * 搜索文章
     */
    public List<PostSummaryDTO> searchPosts(String keyword, Integer limit) {
        try {
            int size = limit != null ? limit : 5;
            String url = blogApiUrl + "/posts/search?keyword=" + keyword + "&size=" + size;
            log.debug("调用博客API搜索文章: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<PostSummaryDTO> results = new ArrayList<>();
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                JsonNode records = data.has("records") ? data.get("records") : data;

                if (records.isArray()) {
                    for (JsonNode record : records) {
                        results.add(parsePostSummary(record));
                    }
                }
            }

            log.debug("搜索文章结果: keyword={}, 找到{}篇", keyword, results.size());
            return results;
        } catch (Exception e) {
            log.error("搜索文章API异常: keyword={}", keyword, e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据分类ID获取文章列表
     */
    public List<PostSummaryDTO> getPostsByCategory(Long categoryId, Integer limit) {
        try {
            int size = limit != null ? limit : 5;
            String url = blogApiUrl + "/posts?categoryId=" + categoryId + "&size=" + size + "&sort=latest";
            log.debug("调用博客API获取分类文章: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<PostSummaryDTO> results = new ArrayList<>();
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                JsonNode records = data.has("records") ? data.get("records") : data;

                if (records.isArray()) {
                    for (JsonNode record : records) {
                        results.add(parsePostSummary(record));
                    }
                }
            }

            log.debug("分类文章结果: categoryId={}, 找到{}篇", categoryId, results.size());
            return results;
        } catch (Exception e) {
            log.error("获取分类文章API异常: categoryId={}", categoryId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取最新发布的文章
     */
    public List<PostSummaryDTO> getLatestPosts(Integer limit) {
        try {
            int size = limit != null ? limit : 5;
            String url = blogApiUrl + "/posts/latest?size=" + size;
            log.debug("调用博客API获取最新文章: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<PostSummaryDTO> results = new ArrayList<>();
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                if (data.isArray()) {
                    for (JsonNode record : data) {
                        results.add(parsePostSummary(record));
                    }
                }
            }

            log.debug("最新文章结果: 找到{}篇", results.size());
            return results;
        } catch (Exception e) {
            log.error("获取最新文章API异常", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取热门文章（按评论数排序）
     */
    public List<PostSummaryDTO> getHotPosts(Integer limit) {
        try {
            int size = limit != null ? limit : 5;
            String url = blogApiUrl + "/posts/hot?limit=" + size;
            log.debug("调用博客API获取热门文章: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<PostSummaryDTO> results = new ArrayList<>();
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                if (data.isArray()) {
                    for (JsonNode record : data) {
                        results.add(parsePostSummary(record));
                    }
                }
            }

            log.debug("热门文章结果: 找到{}篇", results.size());
            return results;
        } catch (Exception e) {
            log.error("获取热门文章API异常", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有分类
     */
    public List<CategoryDTO> getAllCategories() {
        try {
            String url = blogApiUrl + "/categories";
            log.debug("调用博客API获取所有分类: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<CategoryDTO> results = new ArrayList<>();
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                if (data.isArray()) {
                    for (JsonNode record : data) {
                        results.add(parseCategory(record));
                    }
                }
            }

            log.debug("分类结果: 找到{}个", results.size());
            return results;
        } catch (Exception e) {
            log.error("获取分类API异常", e);
            return new ArrayList<>();
        }
    }

    private PostDetailDTO parsePostDetail(JsonNode data) {
        PostDetailDTO dto = new PostDetailDTO();
        dto.setId(data.has("id") ? data.get("id").asLong() : null);
        dto.setTitle(getTextValue(data, "title"));
        dto.setContent(getTextValue(data, "content"));
        dto.setSummary(getTextValue(data, "summary"));
        dto.setViewCount(data.has("viewCount") ? data.get("viewCount").asInt() : 0);
        dto.setLikeCount(data.has("likeCount") ? data.get("likeCount").asInt() : 0);
        dto.setCommentCount(data.has("commentCount") ? data.get("commentCount").asInt() : 0);
        dto.setCreatedAt(getTextValue(data, "createdAt"));

        // 解析分类
        if (data.has("category") && !data.get("category").isNull()) {
            dto.setCategoryName(getTextValue(data.get("category"), "name"));
        }

        // 解析作者
        if (data.has("author") && !data.get("author").isNull()) {
            dto.setAuthorName(getTextValue(data.get("author"), "username"));
        }

        // 解析标签
        if (data.has("tags") && data.get("tags").isArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : data.get("tags")) {
                String tagName = getTextValue(tag, "name");
                if (tagName != null) {
                    tags.add(tagName);
                }
            }
            dto.setTags(tags);
        }

        return dto;
    }

    private PostSummaryDTO parsePostSummary(JsonNode data) {
        PostSummaryDTO dto = new PostSummaryDTO();
        dto.setId(data.has("id") ? data.get("id").asLong() : null);
        dto.setTitle(getTextValue(data, "title"));
        dto.setSummary(getTextValue(data, "summary"));
        dto.setViewCount(data.has("viewCount") ? data.get("viewCount").asInt() : 0);
        dto.setLikeCount(data.has("likeCount") ? data.get("likeCount").asInt() : 0);
        dto.setCreatedAt(getTextValue(data, "createdAt"));

        // 解析分类
        if (data.has("category") && !data.get("category").isNull()) {
            dto.setCategoryName(getTextValue(data.get("category"), "name"));
        }

        // 解析作者
        if (data.has("author") && !data.get("author").isNull()) {
            dto.setAuthorName(getTextValue(data.get("author"), "username"));
        }

        // 解析标签
        if (data.has("tags") && data.get("tags").isArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : data.get("tags")) {
                String tagName = getTextValue(tag, "name");
                if (tagName != null) {
                    tags.add(tagName);
                }
            }
            dto.setTags(tags);
        }

        return dto;
    }

    private String getTextValue(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return null;
    }

    private CategoryDTO parseCategory(JsonNode data) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(data.has("id") ? data.get("id").asLong() : null);
        dto.setName(getTextValue(data, "name"));
        dto.setDescription(getTextValue(data, "description"));
        dto.setPostCount(data.has("postCount") ? data.get("postCount").asInt() : 0);
        return dto;
    }
}
