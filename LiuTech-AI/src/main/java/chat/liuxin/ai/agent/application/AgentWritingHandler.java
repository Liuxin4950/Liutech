package chat.liuxin.ai.agent.application;
import chat.liuxin.ai.dto.AgentUserContext;

import chat.liuxin.ai.agent.application.AdminArticleDraftRequest;
import chat.liuxin.ai.agent.application.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.agent.response.ArticleResultItem;
import chat.liuxin.ai.agent.response.ArticleResultsPayload;
import chat.liuxin.ai.agent.response.FieldUpdatePayload;
import chat.liuxin.ai.agent.response.WritingDraftPayload;
import chat.liuxin.ai.agent.application.AdminBlogClient;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.infra.security.AiCapabilityResolver;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.AiPromptSecurityPolicy;
import chat.liuxin.ai.infra.security.AiSystemPromptProvider;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * WRITE_ARTICLE 意图处理器。
 *
 * 管理员写作辅助的核心处理器。流程：
 * 1. 权限校验（必须是管理员）
 * 2. 读取当前文章上下文
 * 3. 推断写作指令（标题/摘要/正文/润色等）
 * 4. 调用 AI 模型生成 HTML 内容
 * 5. 解析 AI 输出中的 JSON 元数据（分类/标签）
 * 6. 通过 TaxonomyService 匹配分类和标签
 * 7. 构建字段级更新响应
 *
 * 设计说明：
 * - 使用 chatForWriting() 调用模型，不注册 MCP 工具
 * - 分类和标签由 Java 代码处理（TaxonomyService），模型只生成 HTML
 * - 每个 Step 独立 try-catch，失败不阻塞整流
 *
 * @author liuxin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentWritingHandler implements AgentIntentHandler {

    private final TaxonomyService taxonomyService;

    @Value("${spring.ai.agent.persona.name:看板娘}")
    private String personaName;
    private final chat.liuxin.ai.agent.application.PublicArticleTool publicArticleTool;
    private final SiliconFlowChatClient siliconFlowChatClient;
    private final AiSystemPromptProvider systemPromptProvider;
    private final AiPromptSecurityPolicy promptSecurityPolicy;
    private final AiModelPolicy aiModelPolicy;
    private final AiCapabilityResolver capabilityResolver;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.agent.max-context-chars:6000}")
    private int maxContextChars;

    

    @Override
    public AgentChatResponse handle(AgentChatRequest request, AgentHandlerContext ctx) {
        AgentUserContext user = ctx.getUser();

        // 非管理员降级为普通写作建议
        if (!ctx.isAdmin()) {
            return AgentChatResponse.builder()
                    .success(true)
                    .taskId(ctx.getTaskId())
                    .conversationId(ctx.getConversationId())
                    .handlerName("writing")
                    
                    .message("请以普通写作建议的方式回复。不要返回管理员草稿结构，不要声称可以保存、发布或修改博客后台数据。")
                    .build();
        }

        // 读取当前文章上下文
        PostDetailDTO currentArticle = resolveCurrentArticle(request);

        // 推断写作指令
        String instruction = inferWritingInstruction(request.getMessage());

        // 判断字段范围
        WritingFieldScope fieldScope = inferWritingFieldScope(request);
        if (fieldScope.checkOnly()) {
            return buildWritingCheckResponse(request, ctx, currentArticle);
        }

        // 调用 AI 生成写作内容
        WritingDraftPayload writingDraft = buildWritingDraft(request, user, currentArticle, instruction);

        // 构建字段级更新
        AdminArticleDraftRequest draft = request.getDraft();
        FieldUpdatePayload fieldUpdate = buildScopedFieldUpdate(writingDraft, draft, fieldScope);
        boolean hasConcreteFieldUpdate = hasFieldUpdates(fieldUpdate);
        boolean hasAnyFieldUpdate = hasConcreteFieldUpdate || hasFieldSuggestions(fieldUpdate);

        String answer = hasConcreteFieldUpdate
                ? buildWritingAnswer(fieldScope)
                : buildWritingSuggestionAnswer(fieldUpdate);

        // 构建文章卡片（如果有当前文章）
        ArticleResultsPayload articlePayload = null;
        if (currentArticle != null) {
            ArticleResultItem card = publicArticleTool.currentArticleCard(currentArticle, "当前正在编辑或阅读的文章");
            if (card != null) {
                articlePayload = ArticleResultsPayload.builder()
                        .source("current")
                        .query(String.valueOf(currentArticle.getId()))
                        .reason("当前文章")
                        .items(List.of(card))
                        .build();
            }
        }

        return AgentChatResponse.builder()
                .success(true)
                .taskId(ctx.getTaskId())
                .conversationId(ctx.getConversationId())
                .handlerName("writing")
                
                .message(answer)
                .fieldUpdate(hasAnyFieldUpdate ? fieldUpdate : null)
                .articleResults(articlePayload)
                .build();
    }

    // ===== 写作内容生成 =====

    /**
     * 构建写作草稿：调用 AI 生成 HTML + 匹配分类/标签。
     */
    private WritingDraftPayload buildWritingDraft(
            AgentChatRequest request, AgentUserContext user,
            PostDetailDTO currentArticle, String instruction) {

        // 读取分类和标签
        List<AdminBlogClient.AdminTaxonomyItem> categories = taxonomyService.readCategories(user);
        List<AdminBlogClient.AdminTaxonomyItem> tags = taxonomyService.readTags(user);

        String draftContext = buildDraftContext(request.getDraft());
        String articleContext = currentArticle == null ? "" : limitText(currentArticle.toAiReadableFormat(), maxContextChars);

        // 构建写作提示词
        String prompt = promptSecurityPolicy.wrapUntrustedContent("ADMIN_WRITING_DRAFT_INPUT", """
                你是 LiuTech 博客管理员的文章写作助手。请生成可以直接应用到 TinyMCE 富文本编辑器的文章草稿。

                严格要求：
                - 正文必须是安全 HTML，不要输出 Markdown。
                - 允许使用 <h2>、<h3>、<p>、<ul>、<ol>、<li>、<blockquote>、<pre><code>、<strong>、<em>。
                - 禁止 <script>、<iframe>、style、onload、onclick 等事件属性。
                - 代码示例必须放在 <pre><code>...</code></pre> 中。
                - 不要声称已经保存、发布或修改线上数据。
                - 禁止输出寒暄、工具说明、执行说明或"我来帮你/先让我获取分类标签"等助手旁白。
                - 第一个可见内容必须是文章标题或正文，不要把执行过程写进正文。

                分类和标签：
                - 从下面提供的列表中选择最匹配的分类和标签。
                - 如果没有合适的，在输出末尾用建议名称标注。

                输出格式（严格遵守）：
                只输出正文 HTML，然后在最后一行输出一个 JSON 元数据块：
                ```json
                {"categoryId": null, "categoryName": "建议分类名", "tagIds": [], "tagNames": ["标签1", "标签2"]}
                ```
                categoryId 和 tagIds 填写匹配到的已有分类/标签的 ID（数字），没有匹配到则为 null。

                写作任务：%s
                用户要求：%s
                当前编辑器草稿：%s
                当前文章详情：%s
                """.formatted(instruction, request.getMessage(), draftContext, articleContext));

        // 调用写作模型
        String generatedContent = generateWritingText(prompt, 1800);

        // 解析 AI 输出
        return parseWritingOutput(request, generatedContent, categories, tags);
    }

    /**
     * 解析 AI 写作输出，提取 HTML 内容和 JSON 元数据。
     */
    private WritingDraftPayload parseWritingOutput(
            AgentChatRequest request, String generatedContent,
            List<AdminBlogClient.AdminTaxonomyItem> categories,
            List<AdminBlogClient.AdminTaxonomyItem> tags) {

        Long aiCategoryId = null;
        String aiCategoryName = null;
        List<Long> aiTagIds = List.of();
        List<String> aiTagNames = List.of();
        String htmlOnly = generatedContent;

        // 解析末尾的 JSON 元数据
        Matcher metaMatcher = HtmlSanitizer.metadataMatcher(generatedContent);
        if (metaMatcher.find()) {
            htmlOnly = generatedContent.substring(0, metaMatcher.start()).trim();
            try {
                JsonNode meta = objectMapper.readTree(HtmlSanitizer.unescapeMetadataJson(metaMatcher.group(1)));
                if (meta.has("categoryId") && !meta.get("categoryId").isNull()) {
                    aiCategoryId = meta.get("categoryId").asLong();
                }
                if (meta.has("categoryName") && !meta.get("categoryName").isNull()) {
                    aiCategoryName = meta.get("categoryName").asText();
                }
                if (meta.has("tagIds") && meta.get("tagIds").isArray()) {
                    List<Long> ids = new ArrayList<>();
                    for (JsonNode id : meta.get("tagIds")) { if (!id.isNull()) ids.add(id.asLong()); }
                    aiTagIds = ids;
                }
                if (meta.has("tagNames") && meta.get("tagNames").isArray()) {
                    List<String> names = new ArrayList<>();
                    for (JsonNode name : meta.get("tagNames")) { if (!name.isNull()) names.add(name.asText()); }
                    aiTagNames = names;
                }
            } catch (Exception e) {
                log.warn("解析 AI 分类标签元数据失败: {}", e.getMessage());
            }
        }

        // 清理 HTML
        String contentHtml = HtmlSanitizer.sanitize(htmlOnly);
        String plain = HtmlSanitizer.stripTags(contentHtml);

        // 提取标题和摘要
        String title = firstNonBlank(
                request.getDraft() == null ? null : request.getDraft().getTitle(),
                HtmlSanitizer.extractHeading(contentHtml),
                extractTitle(request.getMessage()));
        String summary = firstNonBlank(
                request.getDraft() == null ? null : request.getDraft().getSummary(),
                buildSummary(plain));

        // 匹配分类
        AdminBlogClient.AdminTaxonomyItem category = null;
        String suggestedCategoryName = null;
        if (aiCategoryId != null) { category = taxonomyService.findById(categories, aiCategoryId); }
        if (category == null && aiCategoryName != null && !aiCategoryName.isBlank()) {
            category = taxonomyService.findByName(categories, aiCategoryName);
            if (category == null) { suggestedCategoryName = aiCategoryName; }
        }
        if (category == null) { category = taxonomyService.matchTaxonomy(categories, request.getMessage() + " " + title + " " + plain); }

        // 匹配标签
        List<AdminBlogClient.AdminTaxonomyItem> selectedTags = taxonomyService.findByIds(tags, aiTagIds);
        if (selectedTags.isEmpty() && aiTagNames != null && !aiTagNames.isEmpty()) {
            selectedTags = taxonomyService.findByNames(tags, aiTagNames, 6);
        }
        if (selectedTags.isEmpty()) {
            selectedTags = taxonomyService.matchTaxonomies(tags, request.getMessage() + " " + title + " " + plain, 6);
        }
        List<Long> tagIds = selectedTags.stream().map(AdminBlogClient.AdminTaxonomyItem::getId).toList();
        List<String> tagNames = selectedTags.stream().map(AdminBlogClient.AdminTaxonomyItem::getName).toList();
        List<String> suggestedTagNames = taxonomyService.suggestedTags(request.getMessage(), tagNames, aiTagNames == null ? List.of() : aiTagNames);

        return WritingDraftPayload.builder()
                .title(limitText(title, 120).replace("\n", " ").trim())
                .summary(limitText(summary, 500).replace("\n", " ").trim())
                .contentHtml(contentHtml)
                .categoryId(category == null ? null : category.getId())
                .categoryName(category == null ? null : category.getName())
                .tagIds(tagIds)
                .tagNames(tagNames)
                .suggestedCategoryName(category == null ? firstNonBlank(suggestedCategoryName, taxonomyService.inferSuggestedCategory(request.getMessage())) : null)
                .suggestedTagNames(suggestedTagNames)
                .coverPrompt("为这篇技术博客生成一张简洁、现代、偏工程感的封面图，主题：" + title)
                .notes("正文已按 TinyMCE HTML 生成；发布前建议再人工检查技术事实和代码示例。")
                .checks(List.of(
                        title.isBlank() ? "标题需要补充" : "标题已生成",
                        summary.isBlank() ? "摘要需要补充" : "摘要已生成",
                        category == null ? "未匹配到已有分类，可确认创建建议分类" : "已匹配分类：" + category.getName(),
                        tagNames.isEmpty() ? "未匹配到已有标签，可确认创建建议标签" : "已匹配标签：" + String.join("、", tagNames),
                        "正文使用 HTML 输出，已过滤明显危险片段"))
                .htmlSafe(HtmlSanitizer.isSafe(contentHtml))
                .build();
    }

    // ===== 字段范围推断 =====

    private record WritingFieldScope(boolean title, boolean summary, boolean content,
            boolean category, boolean tags, boolean checkOnly, boolean appendTags) {
        static WritingFieldScope all() { return new WritingFieldScope(true, true, true, true, true, false, false); }
    }

    private WritingFieldScope inferWritingFieldScope(AgentChatRequest request) {
        // 优先从 context 中获取
        WritingFieldScope ctxScope = fieldScopeFromContext(request == null ? null : request.getContext());
        if (ctxScope != null) return ctxScope;

        // 从用户消息推断
        String text = (request == null ? "" : request.getMessage()).toLowerCase();
        boolean all = containsAny(text, "写一篇", "生成一篇", "新文章", "完整文章", "整篇", "全文", "全部", "应用全部");
        boolean checkOnly = containsAny(text, "发布前检查", "检查一下", "帮我检查", "审查一下", "看看有没有问题");
        boolean title = containsAny(text, "标题", "题目", "seo");
        boolean summary = containsAny(text, "摘要", "简介", "概述", "seo", "描述");
        boolean content = containsAny(text, "正文", "富文本", "html", "排版", "格式", "润色", "续写", "扩写", "改写", "章节", "代码", "段落");
        boolean category = containsAny(text, "分类", "栏目");
        boolean tags = containsAny(text, "标签", "tag");
        boolean appendTags = tags && containsAny(text, "加", "增加", "添加", "补", "补充", "追加", "再来");

        if (checkOnly && !containsAny(text, "修复", "修改", "改成", "写入", "应用")) {
            return new WritingFieldScope(false, false, false, false, false, true, false);
        }
        if (all || (!title && !summary && !content && !category && !tags)) {
            return WritingFieldScope.all();
        }
        return new WritingFieldScope(title, summary, content, category, tags, false, appendTags);
    }

    private WritingFieldScope fieldScopeFromContext(Map<String, Object> context) {
        if (context == null) return null;
        Object fieldsValue = context.get("requestedFields");
        if (!(fieldsValue instanceof List<?> fields) || fields.isEmpty()) return null;
        boolean title = fields.contains("title");
        boolean summary = fields.contains("summary");
        boolean content = fields.contains("content") || fields.contains("contentHtml");
        boolean category = fields.contains("category") || fields.contains("categoryId");
        boolean tags = fields.contains("tags") || fields.contains("tagIds");
        boolean checkOnly = fields.contains("check");
        boolean appendTags = Boolean.TRUE.equals(context.get("appendTags"));
        if (checkOnly) return new WritingFieldScope(false, false, false, false, false, true, false);
        return new WritingFieldScope(title, summary, content, category, tags, false, appendTags);
    }

    // ===== 响应构建 =====

    private FieldUpdatePayload buildScopedFieldUpdate(WritingDraftPayload draft, AdminArticleDraftRequest currentDraft, WritingFieldScope scope) {
        FieldUpdatePayload.FieldUpdatePayloadBuilder builder = FieldUpdatePayload.builder();
        if (scope.title()) builder.title(draft.getTitle());
        if (scope.summary()) builder.summary(draft.getSummary());
        if (scope.content()) builder.contentHtml(draft.getContentHtml());
        if (scope.category()) builder.categoryId(draft.getCategoryId()).categoryName(draft.getCategoryName()).suggestedCategoryName(draft.getSuggestedCategoryName());
        if (scope.tags()) {
            List<Long> tagIds = scope.appendTags() ? mergeIds(currentDraft == null ? null : currentDraft.getTagIds(), draft.getTagIds()) : draft.getTagIds();
            builder.tagIds(tagIds).tagNames(draft.getTagNames()).suggestedTagNames(draft.getSuggestedTagNames());
        }
        return builder.build();
    }

    private boolean hasFieldUpdates(FieldUpdatePayload payload) {
        return payload != null && (!isBlank(payload.getTitle()) || !isBlank(payload.getSummary())
                || !isBlank(payload.getContentHtml()) || payload.getCategoryId() != null
                || (payload.getTagIds() != null && !payload.getTagIds().isEmpty()));
    }

    private boolean hasFieldSuggestions(FieldUpdatePayload payload) {
        return payload != null && (!isBlank(payload.getSuggestedCategoryName())
                || (payload.getSuggestedTagNames() != null && !payload.getSuggestedTagNames().isEmpty()));
    }

    private String buildWritingAnswer(WritingFieldScope scope) {
        List<String> fields = new ArrayList<>();
        if (scope.title()) fields.add("标题");
        if (scope.summary()) fields.add("摘要");
        if (scope.content()) fields.add("正文");
        if (scope.category()) fields.add("分类");
        if (scope.tags()) fields.add("标签");
        return fields.isEmpty() ? "我已经检查了你的要求，这次没有需要自动写入的字段。"
                : "已按你的要求更新：" + String.join("、", fields) + "。未请求的字段不会被覆盖。";
    }

    private String buildWritingSuggestionAnswer(FieldUpdatePayload payload) {
        if (!hasFieldSuggestions(payload)) {
            return "我检查了你的要求，但没有匹配到可直接写入的已有分类或标签，所以没有改动编辑器字段。你可以先新增分类/标签，或换一个更明确的名称。";
        }
        List<String> tips = new ArrayList<>();
        if (!isBlank(payload.getSuggestedCategoryName())) tips.add("分类「" + payload.getSuggestedCategoryName() + "」");
        if (payload.getSuggestedTagNames() != null && !payload.getSuggestedTagNames().isEmpty()) {
            tips.add("标签「" + String.join("、", payload.getSuggestedTagNames()) + "」");
        }
        return "我没有找到可直接写入的已有分类或标签，但整理出了建议：" + String.join("，", tips)
                + "。请在编辑器里确认创建并选中，避免我静默改动后台字典。";
    }

    private AgentChatResponse buildWritingCheckResponse(AgentChatRequest request, AgentHandlerContext ctx, PostDetailDTO currentArticle) {
        AdminArticleDraftRequest draft = request.getDraft();
        String title = draft == null ? "" : defaultString(draft.getTitle());
        String summary = draft == null ? "" : defaultString(draft.getSummary());
        String content = draft == null ? "" : defaultString(draft.getContent());
        List<String> checks = new ArrayList<>();
        checks.add(title.isBlank() ? "标题还没有填写。" : "标题已填写，长度约 " + title.length() + " 个字符。");
        checks.add(summary.isBlank() ? "摘要还没有填写，建议补一段 80-160 字的搜索摘要。" : "摘要已填写，长度约 " + summary.length() + " 个字符。");
        checks.add(content.isBlank() ? "正文还没有内容。" : "正文已有内容，纯文本约 " + HtmlSanitizer.stripTags(content).length() + " 个字符。");
        checks.add(draft == null || draft.getCategoryId() == null ? "分类还没有选择。" : "分类已选择。");
        checks.add(draft == null || draft.getTagIds() == null || draft.getTagIds().isEmpty() ? "标签还没有选择。" : "标签已选择 " + draft.getTagIds().size() + " 个。");
        String answer = "发布前检查完成：\n\n- " + String.join("\n- ", checks) + "\n\n我没有改动编辑器字段。";

        ArticleResultsPayload articlePayload = null;
        if (currentArticle != null) {
            ArticleResultItem card = publicArticleTool.currentArticleCard(currentArticle, "当前正在编辑或阅读的文章");
            if (card != null) {
                articlePayload = ArticleResultsPayload.builder().source("current").query(String.valueOf(currentArticle.getId())).reason("当前文章").items(List.of(card)).build();
            }
        }
        return AgentChatResponse.builder().success(true).taskId(ctx.getTaskId()).conversationId(ctx.getConversationId())
                .handlerName("writing").message(answer).articleResults(articlePayload).build();
    }

    // ===== 工具方法 =====

    private String generateWritingText(String prompt, int maxTokens) {
        try {
            List<Message> messages = List.of(new SystemMessage(systemPromptProvider.buildSystemPrompt()), new UserMessage(prompt));
            return siliconFlowChatClient.chatForWriting(messages, aiModelPolicy.resolveModelName((String) null), 0.6, maxTokens);
        } catch (Exception e) {
            log.warn("Agent 写作生成失败: {}", e.getMessage());
            return "<h2>背景</h2><p>这里补充问题背景。</p><h2>核心思路</h2><p>这里展开主要观点。</p>";
        }
    }

    private String inferWritingInstruction(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "标题", "题目")) return "为当前文章生成 3-5 个标题备选，每个标题都要简洁、具体、适合技术博客。";
        if (containsAny(text, "摘要", "简介", "seo")) return "为当前文章生成一段不超过 180 字的摘要，可直接放入摘要字段。";
        if (containsAny(text, "润色", "优化", "改写")) return "润色当前正文，保持原意，提升结构、表达和技术准确性。";
        if (containsAny(text, "扩写", "补充")) return "在当前内容基础上补充更完整的小节和实践说明。";
        return "根据用户要求辅助当前文章写作，输出可以直接应用到编辑器的内容。";
    }

    private PostDetailDTO resolveCurrentArticle(AgentChatRequest request) {
        Long postId = resolvePostId(request);
        return postId == null ? null : publicArticleTool.getArticleDetail(postId);
    }

    private Long resolvePostId(AgentChatRequest request) {
        if (request.getDraft() != null && request.getDraft().getPostId() != null) return request.getDraft().getPostId();
        if (request.getContext() != null) {
            Object postId = request.getContext().get("postId");
            if (postId instanceof Number n) return n.longValue();
            if (postId instanceof String s) { try { return Long.parseLong(s); } catch (NumberFormatException ignore) {} }
        }
        return null;
    }

    private String buildDraftContext(AdminArticleDraftRequest draft) {
        if (draft == null) return "无编辑器草稿。";
        return "文章ID：%s\n标题：%s\n摘要：%s\n分类ID：%s\n标签ID：%s\n状态：%s\n正文：\n%s".formatted(
                draft.getPostId() == null ? "未保存" : draft.getPostId(),
                defaultString(draft.getTitle()), defaultString(draft.getSummary()),
                draft.getCategoryId() == null ? "" : draft.getCategoryId(),
                draft.getTagIds() == null ? "" : draft.getTagIds(),
                defaultString(draft.getStatus()), limitText(defaultString(draft.getContent()), maxContextChars));
    }

    private List<Long> mergeIds(List<Long> existing, List<Long> generated) {
        List<Long> merged = new ArrayList<>();
        if (existing != null) { for (Long id : existing) { if (id != null && !merged.contains(id)) merged.add(id); } }
        if (generated != null) { for (Long id : generated) { if (id != null && !merged.contains(id)) merged.add(id); } }
        return merged;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) { if (v != null && !v.isBlank()) return v.trim(); }
        return personaName + "生成草稿";
    }

    private String limitText(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "\n\n（内容已截断）";
    }

    private String extractTitle(String markdown) {
        if (markdown != null) {
            for (String line : markdown.split("\\R")) {
                String trimmed = line.replaceFirst("^#+\\s*", "").trim();
                if (!trimmed.isBlank()) return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
            }
        }
        return personaName + "生成草稿";
    }

    private String buildSummary(String markdown) {
        String plain = HtmlSanitizer.stripPreamble(markdown == null ? "" : markdown).replaceAll("[#>*`\\-]", "").replaceAll("\\s+", " ").trim();
        return plain.isBlank() ? personaName + "生成的文章草稿" : (plain.length() > 180 ? plain.substring(0, 180) : plain);
    }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String defaultString(String value) { return value == null ? "" : value; }
    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) { if (text.contains(kw)) return true; } return false;
    }
}







