package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.domain.AgentTask;
import chat.liuxin.ai.agent.domain.AgentTaskStatus;
import chat.liuxin.ai.agent.persistence.AgentTaskMapper;
import chat.liuxin.ai.agent.request.AdminArticleDraftRequest;
import chat.liuxin.ai.agent.request.AgentChatRequest;
import chat.liuxin.ai.agent.response.AgentChatResponse;
import chat.liuxin.ai.agent.response.AgentErrorCode;
import chat.liuxin.ai.agent.response.AgentErrorPayload;
import chat.liuxin.ai.agent.response.AgentErrorStage;
import chat.liuxin.ai.agent.response.AgentPlanStep;
import chat.liuxin.ai.agent.response.AgentResultPayload;
import chat.liuxin.ai.agent.response.ArticleResultItem;
import chat.liuxin.ai.agent.response.ArticleResultsPayload;
import chat.liuxin.ai.agent.response.ConfirmationRequiredPayload;
import chat.liuxin.ai.agent.response.FieldUpdatePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import chat.liuxin.ai.agent.response.WritingDraftPayload;
import chat.liuxin.ai.agent.tool.AdminBlogClient;
import chat.liuxin.ai.agent.tool.PublicArticleTool;
import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.security.AiCapabilityResolver;
import chat.liuxin.ai.security.AiModelPolicy;
import chat.liuxin.ai.security.AiPromptSecurityPolicy;
import chat.liuxin.ai.security.AiSystemPromptProvider;
import chat.liuxin.ai.security.AiToolAccessPolicy;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 编排器。
 *
 * 负责协调 Agent 任务的完整执行流程：
 * - 意图分类
 * - 任务创建
 * - 计划构建
 * - 任务执行
 * - SSE 事件发布
 *
 * SSE 事件发布：
 * - 所有事件通过 AgentStreamPublisher 发送
 * - 所有事件包装为统一 AgentSseEnvelope 格式
 * - 工具执行通过 AgentToolExecutionService 发送 tool-start/tool-result 事件
 *
 * @author liuxin
 * @see AgentStreamPublisher
 * @see AgentToolExecutionService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final String[] ARTICLE_TOPICS = {
            "spring boot", "spring ai", "typescript", "javascript", "docker", "kubernetes",
            "nginx", "mysql", "redis", "java", "vue", "react", "jwt", "agent", "ai", "vite", "maven"
    };
    private static final int MAX_CONTEXT_CHARS = 6000;

    private final AgentIntentClassifier intentClassifier;
    private final AgentPlanService planService;
    private final PublicArticleTool publicArticleTool;
    private final AdminBlogClient adminBlogClient;
    private final AgentActionService agentActionService;
    private final AgentStreamPublisher streamPublisher;
    private final AgentToolExecutionService toolExecutionService;
    private final AgentTaskMapper agentTaskMapper;
    private final SiliconFlowChatClient siliconFlowChatClient;
    private final AgentToolCallRecorder toolCallRecorder;
    private final AiCapabilityResolver capabilityResolver;
    private final AiToolAccessPolicy toolAccessPolicy;
    private final AiPromptSecurityPolicy promptSecurityPolicy;
    private final AiSystemPromptProvider systemPromptProvider;
    private final AiModelPolicy aiModelPolicy;
    private final chat.liuxin.ai.mcp.WritingTools writingTools;
    private final ObjectMapper objectMapper;

    /**
     * 非流式执行。
     *
     * @param request 请求
     * @param user   用户上下文
     * @return 聊天响应
     */
    public AgentChatResponse execute(AgentChatRequest request, AgentUserContext user) {
        AgentIntent intent = isAdminWritingContext(request, user)
                ? AgentIntent.WRITE_ARTICLE
                : intentClassifier.classify(request);
        AgentTask task = createTask(request, user, intent, AgentTaskStatus.RUNNING);
        var plan = planService.buildPlan(intent, user != null && user.isAdmin());
        return executeTask(request, user, intent, task, plan, null);
    }

    public AgentChatResponse executeAdmin(AgentChatRequest request, AgentUserContext user) {
        requireAdmin(user);
        // 管理员入口：通过 context 自动判断写作意图
        AgentIntent intent = isAdminWritingContext(request, user)
                ? AgentIntent.WRITE_ARTICLE
                : intentClassifier.classify(request);
        AgentTask task = createTask(request, user, intent, AgentTaskStatus.RUNNING);
        var plan = planService.buildPlan(intent, true);
        return executeTask(request, user, intent, task, plan, null);
    }

    /**
     * 流式执行，通过 SSE 发送事件。
     *
     * 事件发送顺序：
     * 1. agent-start：任务启动信息
     * 2. agent-plan：执行计划
     * 3. executeTask 执行具体任务，发送中间事件（data、article-results、confirmation-required、error）
     * 4. complete：任务完成
     *
     * @param request 请求
     * @param user    用户上下文
     * @param emitter SSE 发射器
     */
    public void executeStream(AgentChatRequest request, AgentUserContext user, SseEmitter emitter) {
        AgentIntent intent;
        // 管理员在文章编辑页 → 强制写作意图，不走 MCP 工具
        if (isAdminWritingContext(request, user)) {
            intent = AgentIntent.WRITE_ARTICLE;
        } else {
            intent = intentClassifier.classify(request);
        }
        executeStreamWithIntent(request, user, emitter, intent);
    }

    private void executeStreamWithIntent(AgentChatRequest request, AgentUserContext user, SseEmitter emitter, AgentIntent intent) {
        AgentTask task = createTask(request, user, intent, AgentTaskStatus.RUNNING);
        var plan = planService.buildPlan(intent, user != null && user.isAdmin());

        // 创建 SSE 上下文
        AgentSseContext context = AgentSseContext.of(emitter, task, request.getConversationId());

        // 发送 agent-start 事件
        streamPublisher.sendAgentStart(
                emitter,
                taskId(task),
                request.getConversationId(),
                intent.name(),
                capabilityResolver.resolve(user).getRole(),
                capabilityResolver.resolve(user).getCapabilities());

        // 发送 agent-plan 事件
        streamPublisher.sendAgentPlan(emitter, taskId(task), request.getConversationId(), plan);

        // 执行任务
        AgentChatResponse response = executeTask(request, user, intent, task, plan, context);

        // 发布响应事件
        publishResponse(context, response);

        // 发送 complete 事件
        streamPublisher.sendComplete(emitter, taskId(task), request.getConversationId());

        // 关闭 emitter
        emitter.complete();
    }

    /**
     * 管理员流式入口。通过 context 自动判断写作意图。
     */
    public void executeAdminStream(AgentChatRequest request, AgentUserContext user, SseEmitter emitter) {
        requireAdmin(user);
        AgentIntent intent = isAdminWritingContext(request, user)
                ? AgentIntent.WRITE_ARTICLE
                : intentClassifier.classify(request);
        executeStreamWithIntent(request, user, emitter, intent);
    }

    /**
     * 判断是否为管理员写作上下文。
     * 管理员在文章编辑页发消息时，context.page == "admin-post-editor"。
     */
    private boolean isAdminWritingContext(AgentChatRequest request, AgentUserContext user) {
        if (user == null || !user.isAdmin()) return false;
        Map<String, Object> ctx = request.getContext();
        Object page = ctx == null ? null : ctx.get("page");
        return "admin-post-editor".equals(page) || "web-create-post".equals(page);
    }

    /**
     * 执行任务。
     *
     * @return 聊天响应
     */
    private AgentChatResponse executeTask(
            AgentChatRequest request,
            AgentUserContext user,
            AgentIntent intent,
            AgentTask task,
            List<AgentPlanStep> plan,
            AgentSseContext context) {
        try {
            AgentChatResponse response = switch (intent) {
                case IDENTITY -> handleIdentity(request, user, task, plan);
                case SEARCH_ARTICLES -> handleArticleSearch(request, task, plan, context);
                case RECOMMEND_ARTICLES -> handleArticleRecommendation(request, task, plan, context);
                case WRITE_ARTICLE -> handleWritingAssist(request, user, task, plan, context);
                case CREATE_DRAFT -> handleDraft(request, user, task, plan);
                case PUBLISH_POST -> handlePublish(request, user, task, plan);
                case OFFLINE_POST -> handleOffline(request, user, task, plan);
                case SUMMARIZE -> handleSummarize(request, task, plan, context);
                default -> handleTextGeneration(request, task, plan, "请以 LiuTech 博客站内看板娘的口吻自然回复。");
            };
            enrichSecurityContext(response, user);
            if (response.getConfirmation() == null) {
                finishTask(task, AgentTaskStatus.COMPLETED, response.getMessage(), null);
            }
            return response;
        } catch (Exception e) {
            log.error("Agent 执行失败", e);
            finishTask(task, AgentTaskStatus.FAILED, null, e.getMessage());
            return AgentChatResponse.builder()
                    .success(false)
                    .taskId(taskId(task))
                    .conversationId(request.getConversationId())
                    .intent(intent.name())
                    .plan(plan)
                    .message("Agent 执行失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 发布响应事件。
     * 根据响应内容发送对应的 SSE 事件（data、article-results、confirmation-required、error）。
     *
     * @param context  SSE 上下文
     * @param response 聊天响应
     */
    private void publishResponse(AgentSseContext context, AgentChatResponse response) {
        SseEmitter emitter = context.getEmitter();

        // 错误事件
        if (Boolean.FALSE.equals(response.getSuccess())) {
            streamPublisher.error(
                    emitter,
                    AgentErrorCode.AGENT_ERROR.getCode(),
                    response.getMessage(),
                    AgentErrorStage.EXECUTE.getStage(),
                    context.getTaskId(),
                    context.getConversationId());
        }

        // 文本事件
        if (response.getMessage() != null && !response.getMessage().isBlank()) {
            streamPublisher.sendData(emitter, context.getTaskId(), context.getConversationId(), response.getMessage());
        }

        // 文章结果事件
        if (response.getArticleResults() != null) {
            streamPublisher.send(emitter, "article-results", context.getTaskId(), context.getConversationId(), response.getArticleResults());
        }

        // 写作草稿事件（保留兼容）
        if (response.getWritingDraft() != null) {
            streamPublisher.send(emitter, "writing-draft", context.getTaskId(), context.getConversationId(), response.getWritingDraft());
        }

        // 字段级更新事件（新）
        if (response.getFieldUpdate() != null) {
            streamPublisher.send(emitter, "field-update", context.getTaskId(), context.getConversationId(), response.getFieldUpdate());
        }

        // 确认卡片事件
        if (response.getConfirmation() != null) {
            streamPublisher.send(emitter, "confirmation-required", context.getTaskId(), context.getConversationId(), response.getConfirmation());
        }
    }

    /**
     * 处理身份查询。
     */
    private AgentChatResponse handleIdentity(
            AgentChatRequest request,
            AgentUserContext user,
            AgentTask task,
            List<AgentPlanStep> plan) {
        String role = resolveRole(user);
        String message;
        if (user == null || !user.isAuthenticated()) {
            message = "我这边没有识别到你的登录态，所以当前会把你当作访客。访客可以聊天、搜索、推荐和总结公开文章；登录后我就能识别你的账号身份。";
        } else if (user.isAdmin()) {
            String name = isBlank(user.getUsername()) ? "你" : user.getUsername();
            message = "我识别到你已登录，身份是管理员（" + name + "）。你可以让我辅助写文章、创建草稿、管理文章发布或下架；涉及发布和下架时，我会先给你确认，不会直接替你执行。";
        } else {
            String name = isBlank(user.getUsername()) ? "你" : user.getUsername();
            message = "我识别到你已登录，身份是普通用户（" + name + "）。你可以聊天、搜索、推荐和总结文章；写博客、发布或下架文章这类管理操作需要管理员权限。";
        }

        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.IDENTITY.name())
                .plan(plan)
                .message(message)
                .role(role)
                .authenticated(user != null && user.isAuthenticated())
                .admin(user != null && user.isAdmin())
                .capabilities(capabilityResolver.resolve(user).getCapabilities())
                .build();
    }

    /**
     * 处理文章搜索。
     */
    private AgentChatResponse handleArticleSearch(
            AgentChatRequest request,
            AgentTask task,
            List<AgentPlanStep> plan,
            AgentSseContext context) {
        String keyword = normalizeKeyword(request.getMessage());

        List<ArticleResultItem> items = context != null
                ? toolExecutionService.execute(context, "public.searchArticles",
                    Map.of("keyword", keyword, "limit", 6),
                    () -> publicArticleTool.searchArticles(keyword, 6))
                : toolCallRecorder.record(taskId(task), "public.searchArticles",
                    Map.of("keyword", keyword, "limit", 6),
                    () -> publicArticleTool.searchArticles(keyword, 6));

        if (items == null) {
            items = List.of();
        }

        ArticleResultsPayload payload = ArticleResultsPayload.builder()
                .source("search")
                .query(keyword)
                .reason(items.isEmpty() ? "没有找到匹配文章" : "我找到了一些可以继续阅读的文章")
                .items(items)
                .build();

        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.SEARCH_ARTICLES.name())
                .plan(plan)
                .message(items.isEmpty() ? "我暂时没有找到匹配的文章，可以换个关键词试试。" : "我找到了这些相关文章，可以直接点开阅读。")
                .articleResults(payload)
                .build();
    }

    /**
     * 处理文章推荐。
     */
    private AgentChatResponse handleArticleRecommendation(
            AgentChatRequest request,
            AgentTask task,
            List<AgentPlanStep> plan,
            AgentSseContext context) {
        String keyword = extractRecommendationKeyword(request.getMessage());
        boolean topicRecommendation = !keyword.isBlank();
        PostDetailDTO currentArticle = null;

        if (!topicRecommendation && resolvePostId(request) != null) {
            currentArticle = resolveCurrentArticle(request, task, context);
            keyword = recommendationKeywordFromCurrentArticle(currentArticle);
            topicRecommendation = !keyword.isBlank();
        }

        String searchKeyword = keyword;
        List<ArticleResultItem> items = topicRecommendation
                ? (context != null
                    ? toolExecutionService.execute(context, "public.recommendBySearch",
                        Map.of("keyword", searchKeyword, "limit", 5),
                        () -> publicArticleTool.searchArticles(searchKeyword, 5))
                    : toolCallRecorder.record(taskId(task), "public.recommendBySearch",
                        Map.of("keyword", searchKeyword, "limit", 5),
                        () -> publicArticleTool.searchArticles(searchKeyword, 5)))
                : (context != null
                    ? toolExecutionService.execute(context, "public.latestArticles",
                        Map.of("limit", 5),
                        () -> publicArticleTool.latestArticles(5))
                    : toolCallRecorder.record(taskId(task), "public.latestArticles",
                        Map.of("limit", 5),
                        () -> publicArticleTool.latestArticles(5)));

        if (items == null) {
            items = List.of();
        }
        if (currentArticle != null && currentArticle.getId() != null) {
            Long currentId = currentArticle.getId();
            items = items.stream()
                    .filter(item -> item.getId() == null || !item.getId().equals(currentId))
                    .toList();
        }

        if (topicRecommendation) {
            items = rankTopicRecommendationItems(keyword, items);
        }

        String source = currentArticle != null ? "related" : (topicRecommendation ? "search" : "latest");
        String reason = topicRecommendation
                ? (items.isEmpty() ? "没有找到与「" + keyword + "」直接相关的文章" : "与「" + keyword + "」相关的文章")
                : "先给你几篇最近更新的内容";

        ArticleResultsPayload payload = ArticleResultsPayload.builder()
                .source(source)
                .query(topicRecommendation ? keyword : request.getMessage())
                .reason(reason)
                .items(items)
                .build();

        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.RECOMMEND_ARTICLES.name())
                .plan(plan)
                .message(recommendationMessage(topicRecommendation, keyword, items))
                .articleResults(payload)
                .build();
    }

    /**
     * 处理后台编辑器中的写作辅助。
     *
     * 这类请求只返回可应用的写作结果，不创建草稿、不发布文章。
     */
    private AgentChatResponse handleWritingAssist(
            AgentChatRequest request,
            AgentUserContext user,
            AgentTask task,
            List<AgentPlanStep> plan,
            AgentSseContext context) {
        if (user == null || !user.isAdmin()) {
            return handleTextGeneration(request, task, plan, "请以普通写作建议的方式回复。不要返回管理员草稿结构，不要声称可以保存、发布或修改博客后台数据。");
        }

        AdminArticleDraftRequest draft = request.getDraft();
        Long postId = resolvePostId(request);
        PostDetailDTO currentArticle = resolveCurrentArticle(request, task, context);

        String instruction = inferWritingInstruction(request.getMessage());
        WritingFieldScope fieldScope = inferWritingFieldScope(request);
        if (fieldScope.checkOnly()) {
            return buildWritingCheckResponse(request, task, plan, currentArticle);
        }

        WritingDraftPayload writingDraft = buildWritingDraft(request, user, currentArticle, instruction, context);
        FieldUpdatePayload fieldUpdate = buildScopedFieldUpdate(writingDraft, draft, fieldScope);
        boolean hasConcreteFieldUpdate = hasFieldUpdates(fieldUpdate);
        boolean hasAnyFieldUpdate = hasConcreteFieldUpdate || hasFieldSuggestions(fieldUpdate);

        String answer = hasConcreteFieldUpdate
                ? buildWritingAnswer(fieldScope)
                : buildWritingSuggestionAnswer(fieldUpdate);
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
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.WRITE_ARTICLE.name())
                .plan(plan)
                .message(answer)
                .fieldUpdate(hasAnyFieldUpdate ? fieldUpdate : null)
                .articleResults(articlePayload)
                .build();
    }

    private AgentChatResponse buildWritingCheckResponse(
            AgentChatRequest request,
            AgentTask task,
            List<AgentPlanStep> plan,
            PostDetailDTO currentArticle) {
        AdminArticleDraftRequest draft = request.getDraft();
        String title = draft == null ? "" : defaultString(draft.getTitle());
        String summary = draft == null ? "" : defaultString(draft.getSummary());
        String content = draft == null ? "" : defaultString(draft.getContent());
        List<String> checks = new ArrayList<>();
        checks.add(isBlank(title) ? "标题还没有填写。" : "标题已填写，长度约 " + title.length() + " 个字符。");
        checks.add(isBlank(summary) ? "摘要还没有填写，建议补一段 80-160 字的搜索摘要。" : "摘要已填写，长度约 " + summary.length() + " 个字符。");
        checks.add(isBlank(content) ? "正文还没有内容。" : "正文已有内容，纯文本约 " + stripHtml(content).length() + " 个字符。");
        checks.add(draft == null || draft.getCategoryId() == null ? "分类还没有选择。" : "分类已选择。");
        checks.add(draft == null || draft.getTagIds() == null || draft.getTagIds().isEmpty() ? "标签还没有选择。" : "标签已选择 " + draft.getTagIds().size() + " 个。");
        String answer = "发布前检查完成：\n\n- " + String.join("\n- ", checks) + "\n\n我没有改动编辑器字段。";

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
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.WRITE_ARTICLE.name())
                .plan(plan)
                .message(answer)
                .articleResults(articlePayload)
                .build();
    }

    private FieldUpdatePayload buildScopedFieldUpdate(
            WritingDraftPayload draft,
            AdminArticleDraftRequest currentDraft,
            WritingFieldScope scope) {
        FieldUpdatePayload.FieldUpdatePayloadBuilder builder = FieldUpdatePayload.builder();
        if (scope.title()) {
            builder.title(draft.getTitle());
        }
        if (scope.summary()) {
            builder.summary(draft.getSummary());
        }
        if (scope.content()) {
            builder.contentHtml(draft.getContentHtml());
        }
        if (scope.category()) {
            builder.categoryId(draft.getCategoryId())
                    .categoryName(draft.getCategoryName())
                    .suggestedCategoryName(draft.getSuggestedCategoryName());
        }
        if (scope.tags()) {
            List<Long> tagIds = scope.appendTags()
                    ? mergeIds(currentDraft == null ? null : currentDraft.getTagIds(), draft.getTagIds())
                    : draft.getTagIds();
            builder.tagIds(tagIds)
                    .tagNames(draft.getTagNames())
                    .suggestedTagNames(draft.getSuggestedTagNames());
        }
        return builder.build();
    }

    private boolean hasFieldUpdates(FieldUpdatePayload payload) {
        return payload != null
                && (!isBlank(payload.getTitle())
                || !isBlank(payload.getSummary())
                || !isBlank(payload.getContentHtml())
                || payload.getCategoryId() != null
                || (payload.getTagIds() != null && !payload.getTagIds().isEmpty()));
    }

    private boolean hasFieldSuggestions(FieldUpdatePayload payload) {
        return payload != null
                && (!isBlank(payload.getSuggestedCategoryName())
                || (payload.getSuggestedTagNames() != null && !payload.getSuggestedTagNames().isEmpty()));
    }

    private String buildWritingAnswer(WritingFieldScope scope) {
        List<String> fields = new ArrayList<>();
        if (scope.title()) fields.add("标题");
        if (scope.summary()) fields.add("摘要");
        if (scope.content()) fields.add("正文");
        if (scope.category()) fields.add("分类");
        if (scope.tags()) fields.add("标签");
        return fields.isEmpty()
                ? "我已经检查了你的要求，这次没有需要自动写入的字段。"
                : "已按你的要求更新：" + String.join("、", fields) + "。未请求的字段不会被覆盖。";
    }

    private String buildWritingSuggestionAnswer(FieldUpdatePayload payload) {
        if (!hasFieldSuggestions(payload)) {
            return "我检查了你的要求，但没有匹配到可直接写入的已有分类或标签，所以没有改动编辑器字段。你可以先新增分类/标签，或换一个更明确的名称。";
        }
        List<String> tips = new ArrayList<>();
        if (!isBlank(payload.getSuggestedCategoryName())) {
            tips.add("分类「" + payload.getSuggestedCategoryName() + "」");
        }
        if (payload.getSuggestedTagNames() != null && !payload.getSuggestedTagNames().isEmpty()) {
            tips.add("标签「" + String.join("、", payload.getSuggestedTagNames()) + "」");
        }
        return "我没有找到可直接写入的已有分类或标签，但整理出了建议：" + String.join("，", tips)
                + "。请在编辑器里确认创建并选中，避免我静默改动后台字典。";
    }


    private List<Long> mergeIds(List<Long> existing, List<Long> generated) {
        List<Long> merged = new ArrayList<>();
        if (existing != null) {
            for (Long id : existing) {
                if (id != null && !merged.contains(id)) merged.add(id);
            }
        }
        if (generated != null) {
            for (Long id : generated) {
                if (id != null && !merged.contains(id)) merged.add(id);
            }
        }
        return merged;
    }

    private WritingFieldScope inferWritingFieldScope(AgentChatRequest request) {
        WritingFieldScope contextScope = fieldScopeFromContext(request == null ? null : request.getContext());
        if (contextScope != null) {
            return contextScope;
        }
        String message = request == null ? "" : request.getMessage();
        String text = defaultString(message).toLowerCase();
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
        if (context == null) {
            return null;
        }
        Object fieldsValue = context.get("requestedFields");
        if (!(fieldsValue instanceof List<?> fields) || fields.isEmpty()) {
            return null;
        }
        boolean title = fields.contains("title");
        boolean summary = fields.contains("summary");
        boolean content = fields.contains("content") || fields.contains("contentHtml");
        boolean category = fields.contains("category") || fields.contains("categoryId");
        boolean tags = fields.contains("tags") || fields.contains("tagIds");
        boolean checkOnly = fields.contains("check");
        boolean appendTags = Boolean.TRUE.equals(context.get("appendTags"));
        if (checkOnly) {
            return new WritingFieldScope(false, false, false, false, false, true, false);
        }
        return new WritingFieldScope(title, summary, content, category, tags, false, appendTags);
    }

    private record WritingFieldScope(
            boolean title,
            boolean summary,
            boolean content,
            boolean category,
            boolean tags,
            boolean checkOnly,
            boolean appendTags) {
        static WritingFieldScope all() {
            return new WritingFieldScope(true, true, true, true, true, false, false);
        }
    }

    /**
     * 处理当前文章总结。
     */
    private AgentChatResponse handleSummarize(
            AgentChatRequest request,
            AgentTask task,
            List<AgentPlanStep> plan,
            AgentSseContext context) {
        PostDetailDTO currentArticle = resolveCurrentArticle(request, task, context);
        if (currentArticle == null) {
            return handleTextGeneration(request, task, plan, "请结合用户提供的上下文做总结；如果没有文章内容，说明需要先打开具体文章。");
        }

        String prompt = promptSecurityPolicy.wrapUntrustedContent("CURRENT_ARTICLE_SUMMARY_INPUT", """
                请总结当前文章，要求：
                - 先用 2-3 句话说明文章讲了什么。
                - 再列出 3-5 个关键点。
                - 最后给一个适合继续阅读或实践的建议。
                - 只基于下面的文章内容，不要编造未出现的信息。

                用户要求：
                %s

                文章内容：
                %s
                """.formatted(request.getMessage(), limitText(currentArticle.toAiReadableFormat(), MAX_CONTEXT_CHARS)));

        ArticleResultItem card = publicArticleTool.currentArticleCard(currentArticle, "当前文章");
        ArticleResultsPayload payload = card == null ? null : ArticleResultsPayload.builder()
                .source("current")
                .query(String.valueOf(currentArticle.getId()))
                .reason("当前正在阅读的文章")
                .items(List.of(card))
                .build();

        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.SUMMARIZE.name())
                .plan(plan)
                .message(generateText(prompt, 700))
                .articleResults(payload)
                .build();
    }

    /**
     * 处理文章草稿创建。
     */
    private AgentChatResponse handleDraft(
            AgentChatRequest request,
            AgentUserContext user,
            AgentTask task,
            List<AgentPlanStep> plan) {
        if (user == null || !user.isAdmin()) {
            return AgentChatResponse.builder()
                    .success(true)
                    .taskId(taskId(task))
                    .intent(AgentIntent.WRITE_ARTICLE.name())
                    .plan(plan)
                    .message("这类写入能力只有管理员可以使用。我可以继续帮你做只读总结、搜索和推荐。")
                    .role(resolveRole(user))
                    .authenticated(user != null && user.isAuthenticated())
                    .admin(false)
                    .capabilities(capabilityResolver.resolve(user).getCapabilities())
                    .build();
        }

        toolAccessPolicy.assertAllowed(user, chat.liuxin.ai.agent.domain.AgentActionType.CREATE_DRAFT);

        AdminArticleDraftRequest draft = mergeGeneratedDraft(request, user);
        if (draft.getCategoryId() == null) {
            return AgentChatResponse.builder()
                    .success(true)
                    .taskId(taskId(task))
                    .conversationId(request.getConversationId())
                    .intent(AgentIntent.WRITE_ARTICLE.name())
                    .plan(plan)
                    .message("我已经准备好草稿内容了，但创建文章需要先选择分类。请在编辑器里选好分类后再让我保存草稿。\n\n" + draft.getContent())
                    .build();
        }

        ConfirmationRequiredPayload confirmation = agentActionService.createDraftAction(taskId(task), user, draft);
        finishTask(task, AgentTaskStatus.WAITING_CONFIRMATION, "等待管理员确认创建草稿", null);
        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.CREATE_DRAFT.name())
                .plan(plan)
                .message("我已经生成草稿预览。确认后只会保存为草稿，不会直接发布。")
                .confirmation(confirmation)
                .build();
    }

    /**
     * 处理文章发布。
     */
    private AgentChatResponse handlePublish(
            AgentChatRequest request,
            AgentUserContext user,
            AgentTask task,
            List<AgentPlanStep> plan) {
        if (user == null || !user.isAdmin()) {
            return forbiddenWriteResponse(task, request, user, plan, "发布文章需要管理员权限。我可以继续帮你做只读总结、搜索和推荐。");
        }
        toolAccessPolicy.assertAllowed(user, chat.liuxin.ai.agent.domain.AgentActionType.PUBLISH_POST);
        Long postId = resolvePostId(request);
        if (postId == null) {
            return AgentChatResponse.builder()
                    .success(true)
                    .taskId(taskId(task))
                    .conversationId(request.getConversationId())
                    .intent(AgentIntent.PUBLISH_POST.name())
                    .plan(plan)
                    .message("我还不能确定要发布哪篇文章。请先打开已保存的草稿；如果这是新文章，我可以先帮你保存为草稿，等你审查后再发布。")
                    .build();
        }
        ConfirmationRequiredPayload confirmation = agentActionService.createPublishAction(taskId(task), user, postId);
        finishTask(task, AgentTaskStatus.WAITING_CONFIRMATION, "等待管理员确认发布文章", null);
        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.PUBLISH_POST.name())
                .plan(plan)
                .message("我已经定位到目标草稿。发布前请确认你已经审查过正文。")
                .confirmation(confirmation)
                .build();
    }

    /**
     * 处理文章下架。
     */
    private AgentChatResponse handleOffline(
            AgentChatRequest request,
            AgentUserContext user,
            AgentTask task,
            List<AgentPlanStep> plan) {
        if (user == null || !user.isAdmin()) {
            return forbiddenWriteResponse(task, request, user, plan, "下架文章需要管理员权限。我可以继续帮你做只读总结、搜索和推荐。");
        }
        toolAccessPolicy.assertAllowed(user, chat.liuxin.ai.agent.domain.AgentActionType.OFFLINE_POST);
        Long postId = resolvePostId(request);
        if (postId == null) {
            return AgentChatResponse.builder()
                    .success(true)
                    .taskId(taskId(task))
                    .conversationId(request.getConversationId())
                    .intent(AgentIntent.OFFLINE_POST.name())
                    .plan(plan)
                    .message("我还不能确定要下架哪篇文章，请先打开或选择具体文章。")
                    .build();
        }
        ConfirmationRequiredPayload confirmation = agentActionService.createOfflineAction(taskId(task), user, postId);
        finishTask(task, AgentTaskStatus.WAITING_CONFIRMATION, "等待管理员确认下架文章", null);
        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.OFFLINE_POST.name())
                .plan(plan)
                .message("我已经定位到目标文章。下架会把文章状态改为草稿，请确认。")
                .confirmation(confirmation)
                .build();
    }

    /**
     * 处理文本生成。
     */
    private AgentChatResponse handleTextGeneration(
            AgentChatRequest request,
            AgentTask task,
            List<AgentPlanStep> plan,
            String instruction) {
        String answer = generateText(promptSecurityPolicy.wrapUntrustedContent(
                "USER_MESSAGE",
                instruction + "\n\n用户消息：\n" + request.getMessage()), 384);
        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.CHAT.name())
                .plan(plan)
                .message(answer)
                .build();
    }

    /**
     * 合并生成的草稿。
     */
    private AdminArticleDraftRequest mergeGeneratedDraft(AgentChatRequest request, AgentUserContext user) {
        AdminArticleDraftRequest draft = request.getDraft() == null ? new AdminArticleDraftRequest() : request.getDraft();
        if (!isBlank(draft.getContent())) {
            if (isBlank(draft.getTitle())) {
                draft.setTitle(extractTitle(draft.getContent()));
            }
            if (isBlank(draft.getSummary())) {
                draft.setSummary(buildSummary(draft.getContent()));
            }
            draft.setStatus("draft");
            return draft;
        }

        WritingDraftPayload generated = buildWritingDraft(request, user, null, inferWritingInstruction(request.getMessage()), null);
        draft.setContent(generated.getContentHtml());
        if (isBlank(draft.getTitle())) {
            draft.setTitle(generated.getTitle());
        }
        if (isBlank(draft.getSummary())) {
            draft.setSummary(generated.getSummary());
        }
        if (draft.getCategoryId() == null) {
            draft.setCategoryId(generated.getCategoryId());
        }
        if (draft.getTagIds() == null || draft.getTagIds().isEmpty()) {
            draft.setTagIds(generated.getTagIds());
        }
        draft.setStatus("draft");
        return draft;
    }

    private WritingDraftPayload buildWritingDraft(
            AgentChatRequest request,
            AgentUserContext user,
            PostDetailDTO currentArticle,
            String instruction,
            AgentSseContext context) {
        List<AdminBlogClient.AdminTaxonomyItem> categories = context == null
                ? readCategories(user)
                : defaultList(toolExecutionService.execute(
                context,
                "admin.listCategories",
                Map.of("purpose", "writing-taxonomy-match"),
                () -> readCategories(user)));
        List<AdminBlogClient.AdminTaxonomyItem> tags = context == null
                ? readTags(user)
                : defaultList(toolExecutionService.execute(
                context,
                "admin.listTags",
                Map.of("purpose", "writing-taxonomy-match"),
                () -> readTags(user)));
        String draftContext = buildDraftContext(request.getDraft());
        String articleContext = currentArticle == null ? "" : limitText(currentArticle.toAiReadableFormat(), MAX_CONTEXT_CHARS);

        String prompt = promptSecurityPolicy.wrapUntrustedContent("ADMIN_WRITING_DRAFT_INPUT", """
                你是 LiuTech 博客管理员的文章写作助手。请生成可以直接应用到 TinyMCE 富文本编辑器的文章草稿。

                严格要求：
                - 正文必须是安全 HTML，不要输出 Markdown。
                - 允许使用 <h2>、<h3>、<p>、<ul>、<ol>、<li>、<blockquote>、<pre><code>、<strong>、<em>。
                - 禁止 <script>、<iframe>、style、onload、onclick 等事件属性。
                - 代码示例必须放在 <pre><code>...</code></pre> 中。
                - 不要声称已经保存、发布或修改线上数据。
                - 禁止输出寒暄、工具说明、执行说明或“我来帮你/先让我获取分类标签”等助手旁白。
                - 第一个可见内容必须是文章标题或正文，不要把执行过程写进正文。

                分类和标签：
                - 使用 listCategories 工具获取所有可用分类，选择最匹配的一个。
                - 使用 listTags 工具获取所有可用标签，选择最匹配的 1-6 个。
                - 如果没有合适的分类或标签，在输出末尾用建议名称标注。

                输出格式（严格遵守）：
                只输出正文 HTML，然后在最后一行输出一个 JSON 元数据块：
                ```json
                {"categoryId": null, "categoryName": "建议分类名", "tagIds": [], "tagNames": ["标签1", "标签2"]}
                ```
                categoryId 和 tagIds 填写匹配到的已有分类/标签的 ID（数字），没有匹配到则为 null。
                categoryName 和 tagNames 填写建议的名称。

                写作任务：
                %s

                用户要求：
                %s

                当前编辑器草稿：
                %s

                当前文章详情：
                %s
                """.formatted(instruction, request.getMessage(), draftContext, articleContext));

        String generatedContent = context == null
                ? generateWritingText(prompt, 1800)
                : defaultString(toolExecutionService.execute(
                context,
                "admin.generateWritingHtml",
                Map.of(
                        "instruction", limitText(instruction, 120),
                        "messageLength", request.getMessage() == null ? 0 : request.getMessage().length(),
                        "draftChars", request.getDraft() == null || request.getDraft().getContent() == null ? 0 : request.getDraft().getContent().length()),
                () -> generateWritingText(prompt, 1800)));
        // 从 AI 输出中解析 JSON 元数据（分类/标签选择）
        Long aiCategoryId = null;
        String aiCategoryName = null;
        List<Long> aiTagIds = List.of();
        List<String> aiTagNames = List.of();
        String htmlOnly = generatedContent;

        java.util.regex.Matcher metaMatcher = writingMetadataMatcher(generatedContent);
        if (metaMatcher.find()) {
            htmlOnly = generatedContent.substring(0, metaMatcher.start()).trim();
            try {
                com.fasterxml.jackson.databind.JsonNode meta = objectMapper.readTree(unescapeMetadataJson(metaMatcher.group(1)));
                if (meta.has("categoryId") && !meta.get("categoryId").isNull()) {
                    aiCategoryId = meta.get("categoryId").asLong();
                }
                if (meta.has("categoryName") && !meta.get("categoryName").isNull()) {
                    aiCategoryName = meta.get("categoryName").asText();
                }
                if (meta.has("tagIds") && meta.get("tagIds").isArray()) {
                    List<Long> ids = new ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode id : meta.get("tagIds")) {
                        if (!id.isNull()) ids.add(id.asLong());
                    }
                    aiTagIds = ids;
                }
                if (meta.has("tagNames") && meta.get("tagNames").isArray()) {
                    List<String> names = new ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode name : meta.get("tagNames")) {
                        if (!name.isNull()) names.add(name.asText());
                    }
                    aiTagNames = names;
                }
            } catch (Exception e) {
                log.warn("解析 AI 分类标签元数据失败: {}", e.getMessage());
            }
        }
        String contentHtml = normalizeHtml(htmlOnly);

        String plain = stripHtml(contentHtml);
        String title = firstNonBlank(
                request.getDraft() == null ? null : request.getDraft().getTitle(),
                extractHtmlHeading(contentHtml),
                extractTitle(request.getMessage()));
        String summary = firstNonBlank(
                request.getDraft() == null ? null : request.getDraft().getSummary(),
                buildSummary(plain));

        AdminBlogClient.AdminTaxonomyItem category = null;
        String suggestedCategoryName = null;
        if (aiCategoryId != null) {
            category = findTaxonomyById(categories, aiCategoryId);
        }
        if (category == null && !isBlank(aiCategoryName)) {
            category = findTaxonomyByName(categories, aiCategoryName);
            if (category == null) {
                suggestedCategoryName = aiCategoryName;
            }
        }
        if (category == null) {
            category = matchTaxonomy(categories, request.getMessage() + " " + title + " " + plain);
        }

        List<AdminBlogClient.AdminTaxonomyItem> selectedTags = findTaxonomiesByIds(tags, aiTagIds);
        if (selectedTags.isEmpty() && aiTagNames != null && !aiTagNames.isEmpty()) {
            selectedTags = findTaxonomiesByNames(tags, aiTagNames, 6);
        }
        if (selectedTags.isEmpty()) {
            selectedTags = matchTaxonomies(tags, request.getMessage() + " " + title + " " + plain, 6);
        }
        List<Long> tagIds = selectedTags.stream().map(AdminBlogClient.AdminTaxonomyItem::getId).toList();
        List<String> tagNames = selectedTags.stream().map(AdminBlogClient.AdminTaxonomyItem::getName).toList();
        List<String> suggestedTagNames = suggestedTags(
                request.getMessage(),
                tagNames,
                aiTagNames == null ? List.of() : aiTagNames);

        return WritingDraftPayload.builder()
                .title(limitText(title, 120).replace("\n", " ").trim())
                .summary(limitText(summary, 500).replace("\n", " ").trim())
                .contentHtml(contentHtml)
                .categoryId(category == null ? null : category.getId())
                .categoryName(category == null ? null : category.getName())
                .tagIds(tagIds)
                .tagNames(tagNames)
                .suggestedCategoryName(category == null ? firstNonBlank(suggestedCategoryName, inferSuggestedCategory(request.getMessage())) : null)
                .suggestedTagNames(suggestedTagNames)
                .coverPrompt("为这篇技术博客生成一张简洁、现代、偏工程感的封面图，主题：" + title)
                .notes("正文已按 TinyMCE HTML 生成；发布前建议再人工检查技术事实和代码示例。")
                .checks(List.of(
                        isBlank(title) ? "标题需要补充" : "标题已生成",
                        isBlank(summary) ? "摘要需要补充" : "摘要已生成",
                        category == null ? "未匹配到已有分类，可确认创建建议分类" : "已匹配分类：" + category.getName(),
                        tagNames.isEmpty() ? "未匹配到已有标签，可确认创建建议标签" : "已匹配标签：" + String.join("、", tagNames),
                        "正文使用 HTML 输出，已过滤明显危险片段"))
                .htmlSafe(isHtmlSafe(contentHtml))
                .build();
    }

    private java.util.regex.Matcher writingMetadataMatcher(String contentHtml) {
        String source = defaultString(contentHtml);
        String quoted = "(?:\"|&quot;|\\\\\")";
        String maybeQuoted = quoted + "?";
        String taxonomyJson = "(\\{[\\s\\S]*?"
                + maybeQuoted + "categoryId" + maybeQuoted
                + "[\\s\\S]*?" + maybeQuoted + "(?:tagIds|tagNames|categoryName)" + maybeQuoted
                + "[\\s\\S]*?\\})";
        List<java.util.regex.Pattern> patterns = List.of(
                java.util.regex.Pattern.compile("(?is)(?:<p[^>]*>\\s*)?```\\s*json\\s*(?:</p>\\s*<p[^>]*>\\s*)?" + taxonomyJson + "\\s*```\\s*(?:</p>)?\\s*$"),
                java.util.regex.Pattern.compile("(?is)(?:<p[^>]*>\\s*)?json\\s*(?:</p>\\s*<p[^>]*>\\s*)?" + taxonomyJson + "\\s*(?:```)?\\s*(?:</p>)?\\s*$"),
                java.util.regex.Pattern.compile("(?is)(?:^|\\R|<p[^>]*>\\s*)" + taxonomyJson + "\\s*(?:</p>)?\\s*$")
        );
        for (java.util.regex.Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(source);
            if (matcher.find()) {
                matcher.reset();
                return matcher;
            }
        }
        return java.util.regex.Pattern.compile("a^").matcher(source);
    }

    private String unescapeMetadataJson(String value) {
        return defaultString(value)
                .replace("&quot;", "\"")
                .replace("&amp;quot;", "\"")
                .trim();
    }

    /**
     * 生成文本。
     */
    private String generateText(String prompt) {
        return generateText(prompt, 800);
    }

    /**
     * 生成文本（通用，带 MCP 工具）。
     * 用于 CHAT、SUMMARIZE 等意图，模型可以调用博客工具。
     */
    private String generateText(String prompt, int maxTokens) {
        try {
            List<Message> messages = List.of(
                    new SystemMessage(systemPromptProvider.buildSystemPrompt()),
                    new UserMessage(prompt));
            return siliconFlowChatClient.chat(messages, aiModelPolicy.resolveModelName((String) null), 0.6, maxTokens);
        } catch (Exception e) {
            log.warn("Agent 文本生成失败: {}", e.getMessage());
            return "抱歉，AI 服务暂时不可用，请稍后再试。";
        }
    }

    /**
     * 写作专用文本生成，不注册 MCP 工具。
     * 用于 WRITE_ARTICLE 意图，模型只生成 HTML 内容，不调用博客探索工具。
     * 分类和标签由 Java 代码处理（readCategories/readTags/matchTaxonomy）。
     */
    private String generateWritingText(String prompt, int maxTokens) {
        try {
            List<Message> messages = List.of(
                    new SystemMessage(systemPromptProvider.buildSystemPrompt()),
                    new UserMessage(prompt));
            return siliconFlowChatClient.chatForWriting(messages, aiModelPolicy.resolveModelName((String) null), 0.6, maxTokens);
        } catch (Exception e) {
            log.warn("Agent 写作生成失败，使用降级草稿: {}", e.getMessage());
            return "<h2>背景</h2><p>这里补充问题背景。</p><h2>核心思路</h2><p>这里展开主要观点。</p><h2>实践步骤</h2><ol><li>梳理目标。</li><li>设计实现路径。</li><li>验证结果。</li></ol><h2>总结</h2><p>这篇文章可以继续补充真实案例和代码细节。</p>";
        }
    }

    /**
     * 解析文章 ID。
     */
    private Long resolvePostId(AgentChatRequest request) {
        if (request.getDraft() != null && request.getDraft().getPostId() != null) {
            return request.getDraft().getPostId();
        }
        if (request.getContext() != null) {
            Object postId = request.getContext().get("postId");
            if (postId instanceof Number n) {
                return n.longValue();
            }
            if (postId instanceof String s) {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return null;
    }

    /**
     * 读取当前文章详情。
     */
    private PostDetailDTO resolveCurrentArticle(AgentChatRequest request, AgentTask task, AgentSseContext context) {
        Long postId = resolvePostId(request);
        if (postId == null) {
            return null;
        }
        return context != null
                ? toolExecutionService.execute(context, "public.getArticleDetail",
                    Map.of("postId", postId),
                    () -> publicArticleTool.getArticleDetail(postId))
                : toolCallRecorder.record(taskId(task), "public.getArticleDetail",
                    Map.of("postId", postId),
                    () -> publicArticleTool.getArticleDetail(postId));
    }

    /**
     * 构建后台编辑器草稿上下文。
     */
    private String buildDraftContext(AdminArticleDraftRequest draft) {
        if (draft == null) {
            return "无编辑器草稿。";
        }
        return """
                文章ID：%s
                标题：%s
                摘要：%s
                分类ID：%s
                标签ID：%s
                状态：%s
                正文：
                %s
                """.formatted(
                draft.getPostId() == null ? "未保存" : draft.getPostId(),
                nullToEmpty(draft.getTitle()),
                nullToEmpty(draft.getSummary()),
                draft.getCategoryId() == null ? "" : draft.getCategoryId(),
                draft.getTagIds() == null ? "" : draft.getTagIds(),
                nullToEmpty(draft.getStatus()),
                limitText(nullToEmpty(draft.getContent()), MAX_CONTEXT_CHARS));
    }

    /**
     * 根据用户话术推断写作辅助目标。
     */
    private String inferWritingInstruction(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "标题", "题目")) {
            return "为当前文章生成 3-5 个标题备选，每个标题都要简洁、具体、适合技术博客。";
        }
        if (containsAny(text, "摘要", "简介", "seo")) {
            return "为当前文章生成一段不超过 180 字的摘要，可直接放入摘要字段。";
        }
        if (containsAny(text, "润色", "优化", "改写")) {
            return "润色当前正文，保持原意，提升结构、表达和技术准确性。";
        }
        if (containsAny(text, "扩写", "补充")) {
            return "在当前内容基础上补充更完整的小节和实践说明。";
        }
        return "根据用户要求辅助当前文章写作，输出可以直接应用到编辑器的内容。";
    }

    /**
     * 限制注入模型的上下文长度。
     */
    private String limitText(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n\n（内容已截断）";
    }

    /**
     * 标准化关键词。
     */
    private String normalizeKeyword(String message) {
        return extractArticleKeyword(message);
    }

    /**
     * 提取推荐关键词。
     */
    private String extractRecommendationKeyword(String message) {
        return extractArticleKeyword(message);
    }

    /**
     * 根据当前文章推断相关文章查询词。
     */
    private String recommendationKeywordFromCurrentArticle(PostDetailDTO article) {
        if (article == null) {
            return "";
        }
        if (article.getTags() != null) {
            for (String tag : article.getTags()) {
                if (!isBlank(tag)) {
                    return tag.trim();
                }
            }
        }
        if (!isBlank(article.getCategoryName())) {
            return article.getCategoryName().trim();
        }
        return isBlank(article.getTitle()) ? "" : article.getTitle().trim();
    }

    /**
     * 从自然语言中提取文章搜索/推荐关键词。
     */
    private String extractArticleKeyword(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String normalized = message.toLowerCase();
        for (String topic : ARTICLE_TOPICS) {
            if (normalized.contains(topic)) {
                return topic;
            }
        }
        String keyword = normalized
                .replaceAll("[，。！？、,.!?]", " ")
                .replace("相关文章", "")
                .replace("类似文章", "")
                .replace("推荐", "")
                .replace("搜索", "")
                .replace("查找", "")
                .replace("找一下", "")
                .replace("找找", "")
                .replace("文章", "")
                .replace("博客", "")
                .replace("教程", "")
                .replace("内容", "")
                .replace("我在", "")
                .replace("我想", "")
                .replace("正在", "")
                .replace("学习", "")
                .replace("了解", "")
                .replace("关于", "")
                .replace("相关", "")
                .replace("有没有", "")
                .replace("有", "")
                .replace("你", "")
                .replace("给我", "")
                .replace("帮我", "")
                .replace("几篇", "")
                .replace("一些", "")
                .replace("一下", "")
                .replace("几个", "")
                .replace("哪些", "")
                .replace("什么", "")
                .replace("有什么", "")
                .replace("的", "")
                .replace("吗", "")
                .replace("呢", "")
                .replace("啊", "")
                .trim()
                .replaceAll("\\s+", " ");
        return keyword.length() > 40 ? keyword.substring(0, 40).trim() : keyword;
    }

    /**
     * 排序推荐文章。
     */
    private List<ArticleResultItem> rankTopicRecommendationItems(String keyword, List<ArticleResultItem> items) {
        if (keyword == null || keyword.isBlank() || items == null || items.isEmpty()) {
            return items == null ? List.of() : items;
        }
        List<ScoredArticle> scored = items.stream()
                .map(item -> new ScoredArticle(item, relevanceScore(keyword, item)))
                .filter(scoredArticle -> scoredArticle.score() > 0)
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .toList();
        boolean hasStrongMatch = scored.stream().anyMatch(item -> item.score() >= 5);
        return scored.stream()
                .filter(item -> !hasStrongMatch || item.score() >= 5)
                .map(scoredArticle -> {
                    ArticleResultItem item = scoredArticle.item();
                    item.setReason(recommendationReason(keyword, item));
                    return item;
                })
                .toList();
    }

    /**
     * 计算相关性分数。
     */
    private int relevanceScore(String keyword, ArticleResultItem item) {
        String normalizedKeyword = keyword.toLowerCase();
        int score = 0;
        if (containsIgnoreCase(item.getTitle(), normalizedKeyword)) {
            score += 10;
        }
        if (containsIgnoreCase(item.getCategoryName(), normalizedKeyword)) {
            score += 5;
        }
        if (item.getTagNames() != null) {
            for (String tag : item.getTagNames()) {
                if (containsIgnoreCase(tag, normalizedKeyword)) {
                    score += 8;
                }
            }
        }
        if (containsIgnoreCase(item.getSummary(), normalizedKeyword)) {
            score += 2;
        }
        return score;
    }

    /**
     * 判断是否包含（忽略大小写）。
     */
    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && keyword != null && !keyword.isBlank() && value.toLowerCase().contains(keyword);
    }

    /**
     * 判断文本是否包含任一关键词。
     */
    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成推荐原因。
     */
    private String recommendationReason(String keyword, ArticleResultItem item) {
        String normalizedKeyword = keyword.toLowerCase();
        if (containsIgnoreCase(item.getTitle(), normalizedKeyword)) {
            return "标题包含「" + keyword + "」";
        }
        if (item.getTagNames() != null) {
            for (String tag : item.getTagNames()) {
                if (containsIgnoreCase(tag, normalizedKeyword)) {
                    return "标签包含「" + tag + "」";
                }
            }
        }
        if (containsIgnoreCase(item.getCategoryName(), normalizedKeyword)) {
            return "分类匹配「" + keyword + "」";
        }
        if (containsIgnoreCase(item.getSummary(), normalizedKeyword)) {
            return "摘要提到「" + keyword + "」";
        }
        return "与「" + keyword + "」相关";
    }

    /**
     * 生成推荐消息。
     */
    private String recommendationMessage(boolean topicRecommendation, String keyword, List<ArticleResultItem> items) {
        if (topicRecommendation) {
            return items.isEmpty()
                    ? "我没有找到和「" + keyword + "」直接相关的文章，先不拿不相干的内容糊弄你。可以换个关键词，我再帮你找。"
                    : "我按「" + keyword + "」帮你找了几篇相关内容，可以直接点开看。";
        }
        return items.isEmpty() ? "我暂时没有拿到可推荐的文章。" : "我先给你挑了几篇最近更新的文章。";
    }

    /**
     * 计分文章记录。
     */
    private record ScoredArticle(ArticleResultItem item, int score) {
    }

    /**
     * 创建任务。
     */
    private AgentTask createTask(AgentChatRequest request, AgentUserContext user, AgentIntent intent, AgentTaskStatus status) {
        AgentTask task = new AgentTask();
        task.setUserId(user == null ? null : user.userIdString());
        task.setConversationId(request.getConversationId());
        task.setIntent(intent.name());
        task.setStatus(status.name());
        task.setInput(request.getMessage());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.insert(task);
        return task;
    }

    /**
     * 解析角色。
     */
    private String resolveRole(AgentUserContext user) {
        if (user == null || !user.isAuthenticated()) {
            return "guest";
        }
        return user.isAdmin() ? "admin" : "user";
    }

    /**
     * 丰富安全上下文。
     */
    private void enrichSecurityContext(AgentChatResponse response, AgentUserContext user) {
        if (response == null) {
            return;
        }
        var context = capabilityResolver.resolve(user);
        response.setRole(context.getRole());
        response.setAuthenticated(context.isAuthenticated());
        response.setAdmin(context.isAdmin());
        response.setCapabilities(context.getCapabilities());
    }

    /**
     * 生成禁止写入响应。
     */
    private AgentChatResponse forbiddenWriteResponse(
            AgentTask task,
            AgentChatRequest request,
            AgentUserContext user,
            List<AgentPlanStep> plan,
            String message) {
        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
                .conversationId(request.getConversationId())
                .intent(AgentIntent.CHAT.name())
                .plan(plan)
                .message(message)
                .role(resolveRole(user))
                .authenticated(user != null && user.isAuthenticated())
                .admin(user != null && user.isAdmin())
                .capabilities(capabilityResolver.resolve(user).getCapabilities())
                .build();
    }

    private void requireAdmin(AgentUserContext user) {
        if (user == null || !user.isAdmin()) {
            throw new AccessDeniedException("管理员写作助手需要管理员权限");
        }
    }

    private List<AdminBlogClient.AdminTaxonomyItem> readCategories(AgentUserContext user) {
        try {
            if (user == null || isBlank(user.getBearerToken())) {
                return List.of();
            }
            return adminBlogClient.listCategories(user.getBearerToken());
        } catch (Exception e) {
            log.warn("读取分类列表失败，降级为名称建议: {}", e.getMessage());
            return List.of();
        }
    }

    private List<AdminBlogClient.AdminTaxonomyItem> readTags(AgentUserContext user) {
        try {
            if (user == null || isBlank(user.getBearerToken())) {
                return List.of();
            }
            return adminBlogClient.listTags(user.getBearerToken());
        } catch (Exception e) {
            log.warn("读取标签列表失败，降级为名称建议: {}", e.getMessage());
            return List.of();
        }
    }

    private AdminBlogClient.AdminTaxonomyItem matchTaxonomy(List<AdminBlogClient.AdminTaxonomyItem> items, String text) {
        if (items == null || items.isEmpty() || text == null) {
            return null;
        }
        String normalized = text.toLowerCase();
        // 优先精确匹配（名称作为子串出现在文本中）
        AdminBlogClient.AdminTaxonomyItem exact = items.stream()
                .filter(item -> item.getName() != null && normalized.contains(item.getName().toLowerCase()))
                .findFirst()
                .orElse(null);
        if (exact != null) return exact;
        // 降级：按词匹配（名称中的每个词只要有一个出现在文本中即可）
        return items.stream()
                .filter(item -> item.getName() != null && containsAnyWord(normalized, item.getName().toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    private List<AdminBlogClient.AdminTaxonomyItem> matchTaxonomies(
            List<AdminBlogClient.AdminTaxonomyItem> items,
            String text,
            int limit) {
        if (items == null || items.isEmpty() || text == null) {
            return List.of();
        }
        String normalized = text.toLowerCase();
        // 精确匹配优先，再按词匹配
        List<AdminBlogClient.AdminTaxonomyItem> exact = items.stream()
                .filter(item -> item.getName() != null && normalized.contains(item.getName().toLowerCase()))
                .limit(limit)
                .toList();
        if (!exact.isEmpty()) return exact;
        return items.stream()
                .filter(item -> item.getName() != null && containsAnyWord(normalized, item.getName().toLowerCase()))
                .limit(limit)
                .toList();
    }

    private AdminBlogClient.AdminTaxonomyItem findTaxonomyById(
            List<AdminBlogClient.AdminTaxonomyItem> items,
            Long id) {
        if (items == null || items.isEmpty() || id == null) {
            return null;
        }
        return items.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    private AdminBlogClient.AdminTaxonomyItem findTaxonomyByName(
            List<AdminBlogClient.AdminTaxonomyItem> items,
            String name) {
        if (items == null || items.isEmpty() || isBlank(name)) {
            return null;
        }
        String normalized = name.trim();
        return items.stream()
                .filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }

    private List<AdminBlogClient.AdminTaxonomyItem> findTaxonomiesByIds(
            List<AdminBlogClient.AdminTaxonomyItem> items,
            List<Long> ids) {
        if (items == null || items.isEmpty() || ids == null || ids.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> ids.contains(item.getId()))
                .limit(6)
                .toList();
    }

    private List<AdminBlogClient.AdminTaxonomyItem> findTaxonomiesByNames(
            List<AdminBlogClient.AdminTaxonomyItem> items,
            List<String> names,
            int limit) {
        if (items == null || items.isEmpty() || names == null || names.isEmpty()) {
            return List.of();
        }
        List<String> normalized = names.stream()
                .filter(name -> !isBlank(name))
                .map(name -> name.trim().toLowerCase())
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item.getName() != null && normalized.contains(item.getName().toLowerCase()))
                .limit(limit)
                .toList();
    }

    private String normalizeHtml(String value) {
        if (isBlank(value)) {
            return """
                    <h2>背景</h2>
                    <p>这里补充文章背景和目标读者。</p>
                    <h2>核心思路</h2>
                    <p>这里展开主要观点和技术路线。</p>
                    <h2>实践步骤</h2>
                    <ol><li>梳理目标。</li><li>设计实现路径。</li><li>验证结果。</li></ol>
                    <h2>总结</h2>
                    <p>这篇文章可以继续补充真实案例和代码细节。</p>
                    """;
        }
        String html = stripWritingMetadataTail(stripWritingPreamble(value))
                .replaceAll("(?is)```html", "")
                .replaceAll("(?is)```", "")
                .replaceAll("(?is)<script.*?>.*?</script>", "")
                .replaceAll("(?is)<iframe.*?>.*?</iframe>", "")
                .replaceAll("(?i)\\bon[a-z]+\\s*=\\s*\"[^\"]*\"", "")
                .replaceAll("(?i)\\bon[a-z]+\\s*=\\s*'[^']*'", "")
                .replaceAll("(?i)\\bon[a-z]+\\s*=\\s*[^\\s>]*", "")
                .replaceAll("(?i)\\sstyle\\s*=\\s*\"[^\"]*\"", "")
                .replaceAll("(?i)\\sstyle\\s*=\\s*'[^']*'", "")
                .trim();
        if (!html.matches("(?s).*<\\s*(h2|h3|p|ul|ol|blockquote|pre|div|section)\\b.*")) {
            StringBuilder builder = new StringBuilder();
            for (String paragraph : html.split("\\R{2,}")) {
                String text = paragraph.trim();
                if (!text.isBlank()) {
                    builder.append("<p>").append(escapeHtml(text)).append("</p>\n");
                }
            }
            html = builder.toString().trim();
        }
        return stripWritingMetadataTail(html);
    }

    private String stripWritingMetadataTail(String value) {
        String source = defaultString(value).trim();
        if (source.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = writingMetadataMatcher(source);
        if (matcher.find()) {
            return source.substring(0, matcher.start()).trim();
        }
        return source;
    }

    private boolean isHtmlSafe(String html) {
        if (html == null) {
            return false;
        }
        String lower = html.toLowerCase();
        return !lower.contains("<script")
                && !lower.contains("<iframe")
                && !lower.contains(" onclick=")
                && !lower.contains(" onload=")
                && !lower.contains("javascript:");
    }

    private String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String stripWritingPreamble(String value) {
        if (isBlank(value)) {
            return "";
        }
        String text = removeLeadingPreambleSentences(value.trim());
        java.util.regex.Matcher firstArticleTag = java.util.regex.Pattern
                .compile("(?is)<\\s*(h1|h2|h3|p|ul|ol|blockquote|pre|section|article)\\b")
                .matcher(text);
        if (firstArticleTag.find() && firstArticleTag.start() > 0) {
            String prefix = stripHtml(text.substring(0, firstArticleTag.start()));
            if (isAssistantPreamble(prefix)) {
                text = text.substring(firstArticleTag.start()).trim();
            }
        }
        StringBuilder builder = new StringBuilder();
        boolean dropping = true;
        for (String paragraph : text.split("\\R{2,}")) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (dropping && isAssistantPreamble(stripHtml(trimmed))) {
                continue;
            }
            dropping = false;
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(trimmed);
        }
        return builder.isEmpty() ? text : builder.toString();
    }

    private String removeLeadingPreambleSentences(String value) {
        String text = value == null ? "" : value.trim();
        boolean changed;
        do {
            changed = false;
            String plain = stripHtml(text).trim();
            if (!startsWithPreamblePhrase(plain)) {
                break;
            }
            java.util.regex.Matcher sentenceEnd = java.util.regex.Pattern
                    .compile("[。.!！]\\s*")
                    .matcher(text);
            if (sentenceEnd.find()) {
                text = text.substring(sentenceEnd.end()).trim();
                changed = true;
            }
        } while (changed && !text.isBlank());
        return text;
    }

    private boolean isAssistantPreamble(String text) {
        if (isBlank(text)) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "");
        if (normalized.length() > 160) {
            return false;
        }
        return startsWithPreamblePhrase(normalized)
                || containsAny(normalized, "获取一下可用的分类和标签", "获取一下现有的分类和标签", "获取分类和标签信息", "先获取分类标签");
    }

    private boolean startsWithPreamblePhrase(String text) {
        if (isBlank(text)) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "");
        return normalized.startsWith("我来帮你")
                || normalized.startsWith("我会帮你")
                || normalized.startsWith("让我")
                || normalized.startsWith("先让我")
                || normalized.startsWith("首先让我")
                || normalized.startsWith("下面")
                || normalized.startsWith("以下是")
                || normalized.startsWith("接下来");
    }

    private String extractHtmlHeading(String html) {
        if (html == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?is)<h[12][^>]*>(.*?)</h[12]>")
                .matcher(html);
        if (matcher.find()) {
            return stripHtml(matcher.group(1));
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "纳西妲生成草稿";
    }

    private String inferSuggestedCategory(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "部署", "docker", "nginx", "运维")) {
            return "部署运维";
        }
        if (containsAny(text, "spring", "java", "后端", "接口")) {
            return "后端开发";
        }
        if (containsAny(text, "vue", "前端", "typescript", "页面")) {
            return "前端开发";
        }
        if (containsAny(text, "ai", "agent", "模型", "提示词")) {
            return "AI 实践";
        }
        return "技术笔记";
    }

    private List<String> suggestedTags(String message, List<String> existingNames) {
        return suggestedTags(message, existingNames, List.of());
    }

    private List<String> suggestedTags(String message, List<String> existingNames, List<String> modelSuggestedNames) {
        List<String> result = new ArrayList<>();
        if (modelSuggestedNames != null) {
            for (String name : modelSuggestedNames) {
                if (!isBlank(name)
                        && (existingNames == null || existingNames.stream().noneMatch(existing -> existing.equalsIgnoreCase(name)))
                        && result.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                    result.add(name.trim());
                }
            }
        }
        String text = message == null ? "" : message.toLowerCase();
        addSuggestedTag(result, existingNames, text, "Spring Boot", "spring", "后端");
        addSuggestedTag(result, existingNames, text, "Vue", "vue", "前端");
        addSuggestedTag(result, existingNames, text, "Docker", "docker", "部署");
        addSuggestedTag(result, existingNames, text, "AI", "ai", "agent", "模型");
        addSuggestedTag(result, existingNames, text, "MySQL", "mysql", "数据库");
        if (result.isEmpty()) {
            result.add("技术实践");
            result.add("博客写作");
        }
        return result.stream().limit(6).toList();
    }

    private void addSuggestedTag(List<String> result, List<String> existingNames, String text, String tag, String... keywords) {
        if (existingNames != null && existingNames.stream().anyMatch(name -> name.equalsIgnoreCase(tag))) {
            return;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword) && !result.contains(tag)) {
                result.add(tag);
                return;
            }
        }
    }

    private String escapeHtml(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 完成任务。
     */
    private void finishTask(AgentTask task, AgentTaskStatus status, String summary, String error) {
        if (task == null || task.getId() == null) {
            return;
        }
        task.setStatus(status.name());
        task.setResultSummary(summary);
        task.setErrorMessage(error);
        task.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    /**
     * 获取任务 ID。
     */
    private Long taskId(AgentTask task) {
        return task == null ? null : task.getId();
    }

    /**
     * null 转空字符串。
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断 text 中是否包含 name 中的任意一个词。
     * 用于分类/标签的模糊匹配，例如 "后端开发" 拆分为 ["后端", "开发"]，
     * 只要文本中包含其中一个词即视为匹配。
     */
    private boolean containsAnyWord(String text, String name) {
        if (text == null || name == null) return false;
        // 按中文字符和英文单词拆分
        String[] words = name.split("[\\s,，、/]+");
        for (String word : words) {
            if (word.length() >= 2 && text.contains(word)) return true;
        }
        return false;
    }

    /**
     * 判断是否为空。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 提取标题。
     */
    private String extractTitle(String markdown) {
        if (markdown != null) {
            for (String line : markdown.split("\\R")) {
                String trimmed = line.replaceFirst("^#+\\s*", "").trim();
                if (!trimmed.isBlank()) {
                    return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
                }
            }
        }
        return "纳西妲生成草稿";
    }

    /**
     * 构建摘要。
     */
    private String buildSummary(String markdown) {
        String plain = stripWritingPreamble(markdown == null ? "" : markdown)
                .replaceAll("[#>*`\\-]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.isBlank()) {
            return "纳西妲生成的文章草稿";
        }
        return plain.length() > 180 ? plain.substring(0, 180) : plain;
    }
}
