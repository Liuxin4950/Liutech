package chat.liuxin.ai.dto;

import lombok.Data;
import java.util.List;

/**
 * 文章详情DTO
 * 用于AI工具获取文章内容进行分析
 */
@Data
public class PostDetailDTO {

    private Long id;
    private String title;
    private String content;
    private String summary;
    private String categoryName;
    private String authorName;
    private List<String> tags;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private String createdAt;

    /**
     * 转换为AI友好的文本格式
     */
    public String toAiReadableFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("【文章标题】").append(title).append("\n");
        sb.append("【作者】").append(authorName).append("\n");
        sb.append("【分类】").append(categoryName).append("\n");
        if (tags != null && !tags.isEmpty()) {
            sb.append("【标签】").append(String.join(", ", tags)).append("\n");
        }
        sb.append("【发布时间】").append(createdAt).append("\n");
        sb.append("【阅读数】").append(viewCount).append(" 【点赞数】").append(likeCount).append("\n");
        sb.append("【摘要】").append(summary != null ? summary : "无").append("\n");
        sb.append("【正文】\n").append(content);
        return sb.toString();
    }
}
