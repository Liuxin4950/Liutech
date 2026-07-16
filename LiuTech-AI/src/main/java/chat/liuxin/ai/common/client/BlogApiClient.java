package chat.liuxin.ai.common.client;

import chat.liuxin.ai.dto.CategoryDTO;
import chat.liuxin.ai.dto.AuthorProfileDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.PostSummaryDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public BlogApiClient(ObjectMapper objectMapper,
                         @Value("${spring.ai.agent.blog-connect-timeout-ms:3000}") long connectTimeoutMs,
                         @Value("${spring.ai.agent.blog-read-timeout-ms:8000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeoutMs);
        factory.setReadTimeout((int) readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
    }

    /**
     * 调用主后端 GET /posts/{id},拉取一篇文章的完整内容(正文、标签、分类、作者、计数)。
     *
     * 用于 AI 追问某篇文章细节、生成摘要或翻译等需要正文的场景。
     * 单实例复用 RestTemplate,超时由构造函数注入的 connectTimeoutMs / readTimeoutMs 控制。
     * 请求失败或响应 code 非 200 返回 null,由调用方决定降级策略。
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
     * 调用主后端 GET /posts/search 做关键词全文检索,返回摘要列表。
     *
     * 关键词做 URL 编码后拼接,limit 未传默认 5。响应可能是分页对象(带 records)或直接数组,两种都兼容。
     * 异常统一吞掉并返回空列表,避免 AI 工具调用因后端抖动整体失败。
     */
    public List<PostSummaryDTO> searchPosts(String keyword, Integer limit) {
        try {
            int size = limit != null ? limit : 5;
            String url = UriComponentsBuilder.fromUriString(blogApiUrl)
                    .path("/posts/search")
                    .queryParam("keyword", keyword == null ? "" : keyword)
                    .queryParam("size", size)
                    .build()
                    .encode()
                    .toUriString();
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
     * 调用主后端 GET /posts?categoryId=...&sort=latest,拉取指定分类下的最新文章。
     *
     * 分页数据结构与 {@link #searchPosts} 相同,失败降级为空列表。
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
     * 调用主后端 GET /posts/latest?limit=...,拉取按发布时间倒序的文章列表。
     *
     * 与 category/hot 不同,该端点响应直接是数组,不是分页对象。
     */
    public List<PostSummaryDTO> getLatestPosts(Integer limit) {
        try {
            int size = limit != null ? limit : 5;
            String url = blogApiUrl + "/posts/latest?limit=" + size;
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
     * 调用主后端 GET /posts/hot?limit=...,按评论数排序拉取热门文章。
     *
     * 热度以评论数为准,不同于以 viewCount 计算的其他排序方式。
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
     * 调用主后端 GET /categories 拉取所有分类,含名称、描述、文章数。
     *
     * AI 常用来展示"博客有哪些方向",或作为分类 ID 到名称的字典。
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

    /**
     * 调用主后端 GET /tags 拉取所有标签,返回 List of Map(id, name)。
     *
     * 因为写作工具只需要 id + name,不引入额外 TagDTO,直接用 Map 组装避免过度设计。
     */
    public List<Object> getAllTags() {
        try {
            String url = blogApiUrl + "/tags";
            log.debug("调用博客API获取所有标签: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            List<Object> results = new ArrayList<>();
            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                JsonNode data = root.get("data");
                if (data.isArray()) {
                    for (JsonNode record : data) {
                        Long id = record.has("id") ? record.get("id").asLong() : null;
                        String name = getTextValue(record, "name");
                        if (id != null && name != null) {
                            results.add(Map.of("id", id, "name", name));
                        }
                    }
                }
            }

            log.debug("标签结果: 找到{}个", results.size());
            return results;
        } catch (Exception e) {
            log.error("获取标签API异常", e);
            return new ArrayList<>();
        }
    }

    /**
     * 调用主后端 GET /user/author/profile 拉取博主资料(昵称、头像、简介、统计数据)。
     *
     * 用户问"作者是谁 / 站点介绍"时给 AI 使用。stats 子对象缺失时会退化到扁平字段解析。
     */
    public AuthorProfileDTO getAuthorProfile() {
        try {
            String url = blogApiUrl + "/user/author/profile";
            log.debug("调用博客API获取作者资料: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("code") && root.get("code").asInt() == 200 && root.has("data")) {
                return parseAuthorProfile(root.get("data"));
            }

            log.warn("获取作者资料失败");
            return null;
        } catch (Exception e) {
            log.error("获取作者资料API异常", e);
            return null;
        }
    }

    /**
     * 把主后端 /posts/{id} 的 JSON 响应节点映射为 {@link PostDetailDTO}。
     *
     * 兼容 category / author / tags 为对象或缺失的情况;计数字段缺失时补 0。
     */
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

    /**
     * 把文章列表节点映射为 {@link PostSummaryDTO},并顺便填好前端跳转 url 与后台管理 url。
     *
     * status 缺失时默认视为 "published",避免下游判空繁琐。
     */
    private PostSummaryDTO parsePostSummary(JsonNode data) {
        PostSummaryDTO dto = new PostSummaryDTO();
        dto.setId(data.has("id") ? data.get("id").asLong() : null);
        dto.setTitle(getTextValue(data, "title"));
        dto.setSummary(getTextValue(data, "summary"));
        dto.setStatus(firstNonBlank(getTextValue(data, "status"), "published"));
        if (dto.getId() != null) {
            dto.setUrl("/post/" + dto.getId());
            dto.setAdminUrl("/admin/posts?postId=" + dto.getId());
        }
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

    /**
     * 空安全地从 JsonNode 读取字符串字段,缺失或 null 时返回 null,减少调用侧的判空样板。
     */
    private String getTextValue(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return null;
    }

    /**
     * 把分类节点映射为 {@link CategoryDTO}。
     */
    private CategoryDTO parseCategory(JsonNode data) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(data.has("id") ? data.get("id").asLong() : null);
        dto.setName(getTextValue(data, "name"));
        dto.setDescription(getTextValue(data, "description"));
        dto.setPostCount(data.has("postCount") ? data.get("postCount").asInt() : 0);
        return dto;
    }

    /**
     * 把作者资料节点映射为 {@link AuthorProfileDTO},兼容新旧字段命名。
     *
     * name 依次回退 name / nickname / username;avatar 回退 avatar / avatarUrl;
     * 统计字段优先取 stats 子对象,老接口把统计打平在根节点上也能识别。
     */
    private AuthorProfileDTO parseAuthorProfile(JsonNode data) {
        AuthorProfileDTO dto = new AuthorProfileDTO();
        dto.setName(firstNonBlank(getTextValue(data, "name"), getTextValue(data, "nickname"), getTextValue(data, "username")));
        dto.setTitle(getTextValue(data, "title"));
        dto.setAvatar(firstNonBlank(getTextValue(data, "avatar"), getTextValue(data, "avatarUrl")));
        dto.setBio(getTextValue(data, "bio"));

        JsonNode stats = data.get("stats");
        if (stats != null && stats.isObject()) {
            dto.setPosts(getLongValue(stats, "posts"));
            dto.setComments(getLongValue(stats, "comments"));
            dto.setViews(getLongValue(stats, "views"));
        } else {
            dto.setPosts(getLongValue(data, "postCount"));
            dto.setComments(getLongValue(data, "commentCount"));
            dto.setViews(firstNonNull(getLongValue(data, "totalViews"), getLongValue(data, "viewCount")));
        }

        return dto;
    }

    private Long getLongValue(JsonNode node, String field) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asLong();
        }
        return null;
    }

    private Long firstNonNull(Long... values) {
        for (Long value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
