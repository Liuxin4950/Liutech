package chat.liuxin.ai.common.mcp;

import chat.liuxin.ai.common.client.BlogApiClient;
import chat.liuxin.ai.dto.CategoryDTO;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.dto.TagDTO;
import chat.liuxin.ai.dto.FieldUpdatePayload;
import chat.liuxin.ai.service.FieldUpdateCollector;
import chat.liuxin.ai.service.WritingToolEventSink;
import chat.liuxin.ai.service.WritingContentSession;
import chat.liuxin.ai.dto.WritingContentPatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 写作专用 MCP 工具。
 *
 * 分两类：
 * - 只读工具：listCategories / listTags / getArticleDetail，获取分类标签和文章内容
 * - 建议工具：applyArticleUpdate / editArticleContent，生成字段或局部正文建议
 *
 * 写工具通过 ToolContext 拿到 FieldUpdateCollector，把字段压入收集器，
 * StreamingChatService 以统一 writing-event 下发待采纳建议，成功后由管理员明确采纳。
 */
@Slf4j
@Component
public class WritingTools implements ToolGroup {

    /** 仅管理员可用（写作助手工具） */
    @Override
    public Set<String> allowedRoles() {
        return Set.of("ADMIN");
    }

    @Override
    public Set<String> allowedModes() {
        return Set.of("WRITING");
    }

    private final BlogApiClient blogApiClient;
    private final ToolResultBudget toolResultBudget;

    public WritingTools(BlogApiClient blogApiClient, ToolResultBudget toolResultBudget) {
        this.blogApiClient = blogApiClient;
        this.toolResultBudget = toolResultBudget;
    }

    /**
     * 写作流程中给 AI 使用:列出所有博客分类,让模型自主为文章挑选合适分类。
     *
     * 复用 {@link BlogApiClient#getAllCategories()},走公开接口无需管理员 token。
     */
    @Tool(description = "获取博客所有分类列表（含ID和名称），用于为文章选择最合适的分类。返回分类ID、名称和描述。")
    public List<CategoryDTO> listCategories(ToolContext toolContext) {
        return wrapToolCall(toolContext, "admin.listCategories", "读取分类列表", null,
                () -> {
                    log.debug("写作工具调用: listCategories");
                    return blogApiClient.getAllCategories();
                },
                r -> r == null ? "0 个分类" : r.size() + " 个分类");
    }

    /**
     * 写作流程中给 AI 使用:列出所有标签,让模型自主为文章挑 1-6 个合适标签。
     *
     * 复用主后端公开的 GET /tags 接口,返回结构与前端标签选择器一致。
     */
    @Tool(description = "获取博客所有标签列表（含ID和名称），用于为文章选择最合适的标签（1-6个）。返回标签ID和名称。")
    public List<TagDTO> listTags(ToolContext toolContext) {
        return wrapToolCall(toolContext, "admin.listTags", "读取标签列表", null,
                () -> {
                    log.debug("写作工具调用: listTags");
                    return blogApiClient.getAllTags();
                },
                r -> r == null ? "0 个标签" : r.size() + " 个标签");
    }

    /**
     * 写作流程中给 AI 使用:按文章ID读取完整文章内容。
     *
     * 用于润色/改写/续写当前编辑的文章，或参考其他文章。
     * 复用 {@link BlogApiClient#getPostDetail(Long)}。
     */
    @Tool(description = "根据文章ID获取文章完整内容（标题、正文、摘要、分类、标签），用于读取当前编辑的文章或参考其他文章。")
    public PostDetailDTO getArticleDetail(@ToolParam(description = "文章ID") Long postId, ToolContext toolContext) {
        return wrapToolCall(toolContext, "public.getArticleDetail", "读取文章详情", "postId=" + postId,
                () -> {
                    log.debug("写作工具调用: getArticleDetail, postId={}", postId);
                    // 正文可能上万字，必须按本次输入预算截断后再进上下文（否则模型会卡在上游）
                    return toolResultBudget.truncateArticleContent(blogApiClient.getPostDetail(postId), toolContext);
                },
                r -> r == null ? "未找到" : "已读取: " + (r.getTitle() == null ? "" : r.getTitle()));
    }

    /**
     * 工具调用统一模板：按唯一活动 ID 发送实际起止，失败不泄漏上游错误。
     * 消除只读工具的 try/catch + fire 样板代码。
     */
    private <T> T wrapToolCall(ToolContext toolContext, String toolName, String displayName,
                                String inputSummary, java.util.function.Supplier<T> action,
                                java.util.function.Function<T, String> successSummary) {
        WritingToolEventSink sink = resolveSink(toolContext);
        String activityId = sink == null ? null : sink.fireStart(toolName, displayName, inputSummary);
        try {
            T result = action.get();
            if (sink != null) sink.complete(activityId, successSummary.apply(result));
            return result;
        } catch (Exception e) {
            if (sink != null) sink.fail(activityId, "工具执行失败，请稍后重试");
            throw e;
        }
    }

    @Tool(description = """
            局部修改已有正文，仅生成待采纳的段落替换；不会保存文章。
            edits中每项before必须逐字复制本轮草稿中唯一出现的完整HTML段落（可含相邻段落以消除重复），
            after是替换后的完整HTML片段，空字符串代表删除。所有before均基于本轮原稿，不要以已修改内容定位。
            只提交真正需要修改的段落，保留未修改内容；同一轮片段不可重叠。最多32处，可一次提交多处。
            续写可定位最后一段，after保留该段并附上新增段落。空edits表示正文已检查且无需修改。
            本工具只用于局部修改模式；新稿或整篇重写应直接输出完整HTML。
            调用后仅用简短文字说明修改数量或无须修改，禁止重新输出整篇正文。
            """)
    public String editArticleContent(
            @ToolParam(description = "基于本轮原稿的段落替换列表；确认无需修改时传空数组") List<WritingContentPatch.Edit> edits,
            ToolContext toolContext) {
        if (toolContext == null || !(toolContext.getContext().get(WritingContentSession.CONTEXT_KEY) instanceof WritingContentSession session)) {
            throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("当前请求不支持正文修改，请使用写作助手入口");
        }
        Object scope = toolContext.getContext().get("allowedWritingFields");
        if (scope instanceof List<?> fields && !fields.isEmpty()
                && (fields.contains("check") || !fields.contains("content"))) {
            throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("本轮不允许修改正文");
        }
        if ("replace".equals(toolContext.getContext().get("writingContentMode"))) {
            throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("本轮为整篇重写模式，请返回完整 HTML 正文");
        }
        return wrapToolCall(toolContext, "admin.editArticleContent", "生成正文局部修改", null,
                () -> { session.add(edits); return edits.size(); },
                count -> count == 0 ? "已检查，正文无需修改" : "已生成 " + count + " 处局部修改，待采纳")
                + "；不要输出未修改的整篇正文";
    }

    /**
     * 字段工具：生成管理员当前草稿的字段建议，不直接写编辑器或数据库。
     *
     * 工具内部把
     * {@link FieldUpdatePayload} 压入 {@link FieldUpdateCollector}（通过 ToolContext 传入），
     * 由 StreamingChatService 实时发 proposal 事件给前端暂存。
     *
     * 参数只填需要修改的字段，留空的不动。这是 AI 操作博客页面的唯一入口。
     */
    @Tool(description = """
            更新当前编辑器的结构化字段（标题/摘要/分类/标签）。这是你修改表单字段的工具，正文不要通过本工具传。
            调用形成待采纳修改，管理员预览后应用；不代表保存或发布。
            未改字段传null；summary空字符串或tagIds空数组代表清空，每次至少提供一个修改字段。

            title/summary 是简短字符串；categoryId 和 tagIds 必须来自 listCategories / listTags 返回的真实 ID，不要编造。
            若目标分类或标签在现有列表中不存在，用 suggestedCategoryName / suggestedTagNames 提交待管理员确认创建，不要编造不存在的 ID。
            正文不要通过本工具传递；局部修改调用editArticleContent，新稿或整篇重写才输出完整HTML。
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
        Object rawScope = toolContext == null ? null : toolContext.getContext().get("allowedWritingFields");
        if (rawScope instanceof List<?> scope && !scope.isEmpty()) {
            if (scope.contains("check")) {
                throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("本轮仅检查文章，不能生成字段修改");
            }
            if (!scope.contains("title")) title = null;
            if (!scope.contains("summary")) summary = null;
            if (!scope.contains("category")) { categoryId = null; suggestedCategoryName = null; }
            if (!scope.contains("tags") && !scope.contains("tag")) { tagIds = null; suggestedTagNames = null; }
        }
        if ((title != null && title.length() > 200) || (summary != null && summary.length() > 500)
                || (tagIds != null && tagIds.size() > 20)
                || (suggestedCategoryName != null && suggestedCategoryName.length() > 100)
                || (suggestedTagNames != null && (suggestedTagNames.size() > 20
                || suggestedTagNames.stream().anyMatch(name -> name != null && name.length() > 100)))) {
            throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("生成的文章字段超出允许长度，请精简后重试");
        }
        final Long requestedCategoryId = categoryId;
        if (categoryId != null && blogApiClient.getAllCategories().stream().noneMatch(c -> requestedCategoryId.equals(c.getId()))) {
            throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("生成的分类不存在，请重新选择分类");
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            Set<Long> validIds = blogApiClient.getAllTags().stream().map(TagDTO::getId).collect(Collectors.toSet());
            if (!validIds.containsAll(tagIds)) {
                throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("生成的标签不存在，请重新选择标签");
            }
        }
        log.debug("写作工具调用: applyArticleUpdate, categoryId={}, tagIds={}", categoryId, tagIds);

        FieldUpdatePayload payload = new FieldUpdatePayload();
        payload.setTitle(isBlank(title) ? null : title.trim());
        payload.setSummary(summary == null ? null : summary.trim());
        payload.setCategoryId(categoryId);
        if (tagIds != null) {
            payload.setTagIds(new ArrayList<>(new java.util.LinkedHashSet<>(tagIds)));
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
                && payload.getTagIds() == null
                && payload.getSuggestedCategoryName() == null
                && (payload.getSuggestedTagNames() == null || payload.getSuggestedTagNames().isEmpty())) {
            throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("未收到任何有效字段，请只提交需要修改的文章字段");
        }

        FieldUpdateCollector collector = resolveCollector(toolContext);
        if (collector == null) {
            throw new chat.liuxin.ai.infra.exception.AIServiceException.RequestException("当前请求不支持字段更新，请使用写作助手入口");
        }
        String activityId = sink == null ? null : sink.fireStart("admin.applyArticleUpdate", "生成文章字段建议", summarizeFields(payload));
        collector.add(payload);
        if (sink != null) sink.complete(activityId, summarizeFields(payload) + "，待采纳");
        return "已生成待采纳修改：" + summarizeFields(payload) + "；尚未保存或发布";
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
        if (p.getSummary() != null) fields.add(p.getSummary().isEmpty() ? "清空摘要" : "摘要");

        if (p.getCategoryId() != null) fields.add("分类");
        if (p.getTagIds() != null) fields.add(p.getTagIds().isEmpty() ? "清空标签" : "标签");
        if (p.getSuggestedCategoryName() != null) fields.add("建议新分类");
        if (p.getSuggestedTagNames() != null && !p.getSuggestedTagNames().isEmpty()) fields.add("建议新标签");
        return fields.isEmpty() ? "（无有效字段）" : String.join("、", fields);
    }
}
