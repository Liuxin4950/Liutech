package chat.liuxin.ai.dto;

import lombok.Data;
import java.util.List;

/**
 * 文章摘要DTO
 * 用于搜索结果展示
 */
@Data
public class PostSummaryDTO {

    private Long id;
    private String title;
    private String summary;
    private String categoryName;
    private String authorName;
    private List<String> tags;
    private Integer viewCount;
    private Integer likeCount;
    private String createdAt;

    /**
     * 转换为AI友好的文本格式
     */
    public String toAiReadableFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("- ID: ").append(id);
        sb.append(" | 标题: ").append(title);
        sb.append(" | 分类: ").append(categoryName);
        if (tags != null && !tags.isEmpty()) {
            sb.append(" | 标签: ").append(String.join(",", tags));
        }
        sb.append(" | 浏览: ").append(viewCount);
        return sb.toString();
    }
}
