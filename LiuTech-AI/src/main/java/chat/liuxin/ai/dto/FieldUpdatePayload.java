package chat.liuxin.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 写作助手 field-update 事件的 payload。
 *
 * 对应前端 {@code Admin/src/types/agent.ts} 的 FieldUpdatePayload。
 * AI 在写作模式下输出结构化字段修改，后端解析为该对象后通过 SSE field-update 事件发送给前端，
 * 前端 onFieldUpdate handler 据此回写表单。
 *
 * 所有字段可选，AI 只填充需要修改的字段，未修改的字段为 null（前端按字段合并）。
 *
 * @author 刘鑫
 */
@Data
public class FieldUpdatePayload {

    /** 修改后的标题 */
    private String title;

    /** 修改后的摘要 */
    private String summary;

    /** 修改后的正文（HTML） */
    private String contentHtml;

    /** 修改后的分类ID */
    private Long categoryId;

    /** 修改后的分类名称 */
    private String categoryName;

    /** 修改后的标签ID列表 */
    private List<Long> tagIds;

    /** 修改后的标签名称列表 */
    private List<String> tagNames;

    /** 建议新增的分类名称（前端确认后创建） */
    private String suggestedCategoryName;

    /** 建议新增的标签名称列表（前端确认后创建） */
    private List<String> suggestedTagNames;
}
