package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 字段级更新负载。
 *
 * 只包含 AI 本次修改的字段，null 表示不修改。
 * 前端收到后直接写入表单，无需用户手动"应用"。
 */
@Data
@Builder
public class FieldUpdatePayload {
    private String title;
    private String summary;
    private String contentHtml;
    private Long categoryId;
    private String categoryName;
    private List<Long> tagIds;
    private List<String> tagNames;
    private String suggestedCategoryName;
    private List<String> suggestedTagNames;
}
