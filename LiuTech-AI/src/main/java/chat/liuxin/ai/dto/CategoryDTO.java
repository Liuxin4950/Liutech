package chat.liuxin.ai.dto;

import lombok.Data;

/**
 * 分类DTO - 用于AI工具返回
 */
@Data
public class CategoryDTO {

    private Long id;
    private String name;
    private String description;
    private Integer postCount;

    /**
     * 转换为AI友好的文本格式
     */
    public String toAiReadableFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("- ID: ").append(id);
        sb.append(" | 名称: ").append(name);
        if (description != null && !description.isEmpty()) {
            sb.append(" | 描述: ").append(description);
        }
        sb.append(" | 文章数: ").append(postCount);
        return sb.toString();
    }
}
