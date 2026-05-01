package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ArticleResultItem {
    private Long id;
    private String title;
    private String summary;
    private String status;
    private String categoryName;
    private List<String> tagNames;
    private Integer viewCount;
    private Integer likeCount;
    private String createdAt;
    private String url;
    private String adminUrl;
    private String reason;
    private String source;
}
