package chat.liuxin.ai.agent.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 写作草稿负载。
 *
 * <p>用于管理员写作助手把模型输出转换成可直接应用到 TinyMCE 表单的结构化结果。
 */
@Data
@Builder
public class WritingDraftPayload {
    private String title;
    private String summary;
    private String contentHtml;
    private Long categoryId;
    private String categoryName;
    private List<Long> tagIds;
    private List<String> tagNames;
    private String suggestedCategoryName;
    private List<String> suggestedTagNames;
    private String coverPrompt;
    private String notes;
    private List<String> checks;
    private boolean htmlSafe;
}
