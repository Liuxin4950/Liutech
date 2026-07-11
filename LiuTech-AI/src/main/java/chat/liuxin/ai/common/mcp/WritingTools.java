package chat.liuxin.ai.common.mcp;

import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.dto.CategoryDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.FieldUpdatePayload;
import chat.liuxin.ai.service.FieldUpdateCollector;
import chat.liuxin.ai.service.WritingToolEventSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 写作专用 MCP 工具。
 *
 * 分两类：
 * - 只读工具：listCategories / listTags / getArticleDetail，获取分类标签和文章内容
 * - 写工具：applyArticleUpdate，把内容写入管理员编辑器的字段（function calling 即操作页面）
 *
 * 写工具通过 ToolContext 拿到 FieldUpdateCollector，把字段压入收集器，
 * StreamingChatService 流结束后发 field-update SSE 事件给前端回写表单。
 * 这样 AI 是"执行者"而非"顾问"，不再依赖 ---field-update--- 文本标记。
 */
@Slf4j
@Component
public class WritingTools implements ToolGroup {

    /** 仅管理员可用（写作助手工具） */
    @Override
    public java.util.Set<String> allowedRoles() {
        return java.util.Set.of("ADMIN");
    }

    private final BlogApiClient blogApiClient;

    public WritingTools(BlogApiClient blogApiClient) {
        this.blogApiClient = blogApiClient;
    }

    /**
     * 写作流程中给 AI 使用:列出所有博客分类,让模型自主为文章挑选合适分类。
     *
     * 复用 {@link BlogApiClient#getAllCategories()},走公开接口无需管理员 token。
     */
    @Tool(description = "获取博客所有分类列表（含ID和名称），用于为文章选择最合适的分类。返回分类ID、名称和描述。")
    public List<CategoryDTO> listCategories(ToolContext toolContext) {
        WritingToolEventSink sink = resolveSink(toolContext);
        if (sink != null) sink.fireStart("admin.listCategories", "读取分类列表", null);
        try {
            log.debug("写作工具调用: listCategories");
            List<CategoryDTO> result = blogApiClient.getAllCategories();
            if (sink != null) sink.fireSuccess("admin.listCategories", "读取分类列表",
                    result == null ? "0 个分类" : result.size() + " 个分类");
            return result;
        } catch (Exception e) {
            if (sink != null) sink.fireError("admin.listCategories", "读取分类列表", e.getMessage());
            throw e;
        }
    }

    /**
     * 写作流程中给 AI 使用:列出所有标签,让模型自主为文章挑 1-6 个合适标签。
     *
     * 复用主后端公开的 GET /tags 接口,返回结构与前端标签选择器一致。
     */
    @Tool(description = "获取博客所有标签列表（含ID和名称），用于为文章选择最合适的标签（1-6个）。返回标签ID和名称。")
    public List<Object> listTags(ToolContext toolContext) {
        WritingToolEventSink sink = resolveSink(toolContext);
        if (sink != null) sink.fireStart("admin.listTags", "读取标签列表", null);
        try {
            log.debug("写作工具调用: listTags");
            List<Object> result = blogApiClient.getAllTags();
            if (sink != null) sink.fireSuccess("admin.listTags", "读取标签列表",
                    result == null ? "0 个标签" : result.size() + " 个标签");
            return result;
        } catch (Exception e) {
            if (sink != null) sink.fireError("admin.listTags", "读取标签列表", e.getMessage());
            throw e;
        }
    }

    /**
     * 写作流程中给 AI 使用:按文章ID读取完整文章内容。
     *
     * 用于润色/改写/续写当前编辑的文章，或参考其他文章。
     * 复用 {@link BlogApiClient#getPostDetail(Long)}。
     */
    @Tool(description = "根据文章ID获取文章完整内容（标题、正文、摘要、分类、标签），用于读取当前编辑的文章或参考其他文章。")
    public PostDetailDTO getArticleDetail(@ToolParam(description = "文章ID") Long postId, ToolContext toolContext) {
        WritingToolEventSink sink = resolveSink(toolContext);
        if (sink != null) sink.fireStart("public.getArticleDetail", "读取文章详情", "postId=" + postId);
        try {
            log.debug("写作工具调用: getArticleDetail, postId={}", postId);
            PostDetailDTO result = blogApiClient.getPostDetail(postId);
            if (sink != null) sink.fireSuccess("public.getArticleDetail", "读取文章详情",
                    result == null ? "未找到" : "已读取: " + (result.getTitle() == null ? "" : result.getTitle()));
            return result;
        } catch (Exception e) {
            if (sink != null) sink.fireError("public.getArticleDetail", "读取文章详情", e.getMessage());
            throw e;
        }
    }

    /**
     * 写工具：把内容写入管理员当前编辑器的字段。
     *
     * AI 通过 function calling 调用本工具即视为执行一次字段回写。工具内部把
     * {@link FieldUpdatePayload} 压入 {@link FieldUpdateCollector}（通过 ToolContext 传入），
     * 由 StreamingChatService 实时发 field-update SSE 事件给前端。
     *
     * 参数只填需要修改的字段，留空的不动。这是 AI 操作博客页面的唯一入口。
     */
    @Tool(description = """
            更新当前编辑器的结构化字段（标题/摘要/分类/标签）。这是你修改表单字段的工具，正文不要通过本工具传。
            调用一次即完成一次回写，前端会自动更新对应的表单字段。
            参数只填需要修改的字段，其他留空即可；每次调用至少传一个非空字段。

            title/summary 是简短字符串；categoryId 和 tagIds 必须来自 listCategories / listTags 返回的真实 ID，不要编造。
            若目标分类或标签在现有列表中不存在，用 suggestedCategoryName / suggestedTagNames 提交待管理员确认创建，不要编造不存在的 ID。
            正文 HTML 不要通过本工具传递，直接作为正常回复内容输出（流式，含 <h2>/<p>/<pre><code> 等标签），后端会自动写入编辑器。
            """)
    public String applyArticleUpdate(
            @ToolParam(required = false, description = "文章标题") String title,
            @ToolParam(required = false, description = "文章摘要，80-160 字") String summary,
            @ToolParam(required = false, description = "分类 ID（来自 listCategories 返回值）") Long categoryId,
            @ToolParam(required = false, description = "标签 ID 列表（来自 listTags 返回值）") List<Long> tagIds,
            @ToolParam(required = false, description = "建议新增的分类名称（当现有分类都不合适时）") String suggestedCategoryName,
            @ToolParam(required = false, description = "建议新增的标签名称列表（当现有标签都不合适时）") List<String> suggestedTagNames,
            ToolContext toolContext
    ) {
        log.debug("写作工具调用: applyArticleUpdate, title={}, categoryId={}, tagIds={}", title, categoryId, tagIds);

        FieldUpdatePayload payload = new FieldUpdatePayload();
        payload.setTitle(isBlank(title) ? null : title.trim());
        payload.setSummary(isBlank(summary) ? null : summary.trim());
        payload.setCategoryId(categoryId);
        if (tagIds != null && !tagIds.isEmpty()) {
            payload.setTagIds(new ArrayList<>(tagIds));
        }
        payload.setSuggestedCategoryName(isBlank(suggestedCategoryName) ? null : suggestedCategoryName.trim());
        if (suggestedTagNames != null) {
            payload.setSuggestedTagNames(suggestedTagNames.stream()
                    .filter(s -> !isBlank(s))
                    .map(String::trim)
                    .collect(Collectors.toList()));
        }
        WritingToolEventSink sink = resolveSink(toolContext);


        // 早期拦截：所有字段都为空时不写入，避免无效空 payload 触发 SSE
        if (payload.getTitle() == null && payload.getSummary() == null
                && payload.getCategoryId() == null
                && (payload.getTagIds() == null || payload.getTagIds().isEmpty())
                && payload.getSuggestedCategoryName() == null
                && (payload.getSuggestedTagNames() == null || payload.getSuggestedTagNames().isEmpty())) {
            if (sink != null) sink.fireError("admin.applyArticleUpdate", "写入文章字段", "未收到任何有效字段");
            return "未收到任何有效字段。请在调用时至少提供 title/summary/categoryId/tagIds 中的一个字段。";
        }

        if (sink != null) sink.fireStart("admin.applyArticleUpdate", "写入文章字段", summarizeFields(payload));
        FieldUpdateCollector collector = resolveCollector(toolContext);
        if (collector == null) {
            // 同步 /ai/writing 路径不建 SSE 通道，静默接受字段更新（前端走流式接口才有回写）。
            // 返回确认信息防止 AI 误判为失败反复重试。
            log.debug("applyArticleUpdate 被调用但无收集器（同步路径），字段: {}", summarizeFields(payload));
            if (sink != null) sink.fireSuccess("admin.applyArticleUpdate", "写入文章字段", summarizeFields(payload));
            return "已记录：" + summarizeFields(payload) + "（当前为非流式调用，字段将随回复文本返回）";
        }
        collector.add(payload);
        if (sink != null) sink.fireSuccess("admin.applyArticleUpdate", "写入文章字段", summarizeFields(payload));
        return "已写入：" + summarizeFields(payload);
    }

    private WritingToolEventSink resolveSink(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object raw = toolContext.getContext().get(WritingToolEventSink.CONTEXT_KEY);
        return raw instanceof WritingToolEventSink sink ? sink : null;
    }

    /** 从 ToolContext 取出收集器，可能为 null（调用方未注入） */
    private FieldUpdateCollector resolveCollector(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object raw = toolContext.getContext().get(FieldUpdateCollector.CONTEXT_KEY);
        return raw instanceof FieldUpdateCollector collector ? collector : null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 生成简短的已写入字段清单，供 AI 感知本次操作覆盖了哪些字段 */
    private String summarizeFields(FieldUpdatePayload p) {
        List<String> fields = new ArrayList<>();
        if (p.getTitle() != null) fields.add("标题");
        if (p.getSummary() != null) fields.add("摘要");

        if (p.getCategoryId() != null) fields.add("分类");
        if (p.getTagIds() != null && !p.getTagIds().isEmpty()) fields.add("标签");
        if (p.getSuggestedCategoryName() != null) fields.add("建议新分类");
        if (p.getSuggestedTagNames() != null && !p.getSuggestedTagNames().isEmpty()) fields.add("建议新标签");
        return fields.isEmpty() ? "（无有效字段）" : String.join("、", fields);
    }
}





