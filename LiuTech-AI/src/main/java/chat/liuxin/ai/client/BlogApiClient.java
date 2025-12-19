package chat.liuxin.ai.client;

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
 */
@Slf4j
@Component
public class BlogApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${blog.api.url:http://localhost:8080}")
    private String blogApiUrl;

    public BlogApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
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
}
