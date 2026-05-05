package chat.liuxin.ai.agent.tool;

import chat.liuxin.ai.agent.request.AdminArticleDraftRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AdminBlogClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${blog.api.url:http://backend:8080}")
    private String blogApiUrl;

    public AdminBlogClient(
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${spring.ai.agent.blog-connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${spring.ai.agent.blog-read-timeout-ms:8000}") long readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    public AdminPostActionResult createDraft(AdminArticleDraftRequest draft, String bearerToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", draft.getTitle());
        body.put("content", draft.getContent());
        body.put("summary", draft.getSummary());
        body.put("categoryId", draft.getCategoryId());
        body.put("tagIds", draft.getTagIds());
        body.put("coverImage", draft.getCoverImage());
        body.put("thumbnail", draft.getThumbnail());
        body.put("status", "draft");
        JsonNode data = exchange("/admin/posts", HttpMethod.POST, body, bearerToken);
        return AdminPostActionResult.builder()
                .postId(readLong(data, "id"))
                .title(readText(data, "title", draft.getTitle()))
                .status(readText(data, "status", "draft"))
                .adminUrl(data != null && data.has("id") ? "/admin/posts?postId=" + data.get("id").asLong() : null)
                .url(data != null && data.has("id") ? "/post/" + data.get("id").asLong() : null)
                .build();
    }

    public AdminPostActionResult publishPost(Long postId, String bearerToken) {
        exchange("/admin/posts/" + postId + "/publish", HttpMethod.PUT, null, bearerToken);
        return AdminPostActionResult.builder()
                .postId(postId)
                .status("published")
                .url("/post/" + postId)
                .adminUrl("/admin/posts?postId=" + postId)
                .build();
    }

    public AdminPostActionResult offlinePost(Long postId, String bearerToken) {
        exchange("/admin/posts/" + postId + "/offline", HttpMethod.PUT, null, bearerToken);
        return AdminPostActionResult.builder()
                .postId(postId)
                .status("draft")
                .adminUrl("/admin/posts?postId=" + postId)
                .build();
    }

    public List<AdminTaxonomyItem> listCategories(String bearerToken) {
        JsonNode data = exchange("/admin/categories?page=1&size=1000", HttpMethod.GET, null, bearerToken);
        return readRecords(data);
    }

    public List<AdminTaxonomyItem> listTags(String bearerToken) {
        JsonNode data = exchange("/admin/tags?page=1&size=1000", HttpMethod.GET, null, bearerToken);
        return readRecords(data);
    }

    private JsonNode exchange(String path, HttpMethod method, Object body, String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("缺少管理员认证 token");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    blogApiUrl + path,
                    method,
                    new HttpEntity<>(body, headers),
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!response.getStatusCode().is2xxSuccessful()
                    || !root.has("code")
                    || root.get("code").asInt() != 200) {
                String message = root.has("message") ? root.get("message").asText() : "主后端操作失败";
                throw new IllegalStateException(message);
            }
            return root.get("data");
        } catch (Exception e) {
            log.error("管理员文章工具调用失败: {} {}", method, path, e);
            throw new IllegalStateException("调用主后端文章管理接口失败: " + e.getMessage(), e);
        }
    }

    private Long readLong(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }

    private String readText(JsonNode node, String field, String fallback) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : fallback;
    }

    private List<AdminTaxonomyItem> readRecords(JsonNode data) {
        JsonNode records = data != null && data.has("records") ? data.get("records") : data;
        if (records == null || !records.isArray()) {
            return List.of();
        }
        List<AdminTaxonomyItem> result = new ArrayList<>();
        for (JsonNode record : records) {
            Long id = readLong(record, "id");
            String name = readText(record, "name", "");
            if (id != null && name != null && !name.isBlank()) {
                result.add(AdminTaxonomyItem.builder()
                        .id(id)
                        .name(name)
                        .description(readText(record, "description", ""))
                        .build());
            }
        }
        return result;
    }

    @Data
    @Builder
    public static class AdminPostActionResult {
        private Long postId;
        private String title;
        private String status;
        private String url;
        private String adminUrl;
    }

    @Data
    @Builder
    public static class AdminTaxonomyItem {
        private Long id;
        private String name;
        private String description;
    }
}
