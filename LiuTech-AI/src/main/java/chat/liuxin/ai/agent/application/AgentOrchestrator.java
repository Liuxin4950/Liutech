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
import chat.liuxin.ai.agent.tool.PublicArticleTool;
import chat.liuxin.ai.security.AiCapabilityResolver;
import chat.liuxin.ai.security.AiModelPolicy;
import chat.liuxin.ai.security.AiPromptSecurityPolicy;
import chat.liuxin.ai.security.AiToolAccessPolicy;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
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

    private final AgentIntentClassifier intentClassifier;
    private final AgentPlanService planService;
    private final PublicArticleTool publicArticleTool;
    private final AgentActionService agentActionService;
    private final AgentStreamPublisher streamPublisher;
    private final AgentToolExecutionService toolExecutionService;
    private final AgentTaskMapper agentTaskMapper;
    private final SiliconFlowChatClient siliconFlowChatClient;
    private final AgentToolCallRecorder toolCallRecorder;
    private final AiCapabilityResolver capabilityResolver;
    private final AiToolAccessPolicy toolAccessPolicy;
    private final AiPromptSecurityPolicy promptSecurityPolicy;
    private final AiModelPolicy aiModelPolicy;

    /**
     * 非流式执行。
     *
     * @param request 请求
     * @param user   用户上下文
     * @return 聊天响应
     */
    public AgentChatResponse execute(AgentChatRequest request, AgentUserContext user) {
        AgentIntent intent = intentClassifier.classify(request);
        AgentTask task = createTask(request, user, intent, AgentTaskStatus.RUNNING);
        var plan = planService.buildPlan(intent, user != null && user.isAdmin());
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
        AgentIntent intent = intentClassifier.classify(request);
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
                case WRITE_ARTICLE, CREATE_DRAFT -> handleDraft(request, user, task, plan);
                case PUBLISH_POST -> handlePublish(request, user, task, plan);
                case OFFLINE_POST -> handleOffline(request, user, task, plan);
                case SUMMARIZE -> handleTextGeneration(request, task, plan, "请结合上下文总结用户正在阅读的内容。");
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

        List<ArticleResultItem> items = topicRecommendation
                ? (context != null
                    ? toolExecutionService.execute(context, "public.recommendBySearch",
                        Map.of("keyword", keyword, "limit", 5),
                        () -> publicArticleTool.searchArticles(keyword, 5))
                    : toolCallRecorder.record(taskId(task), "public.recommendBySearch",
                        Map.of("keyword", keyword, "limit", 5),
                        () -> publicArticleTool.searchArticles(keyword, 5)))
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

        if (topicRecommendation) {
            items = rankTopicRecommendationItems(keyword, items);
        }

        String source = topicRecommendation ? "search" : "latest";
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

        AdminArticleDraftRequest draft = mergeGeneratedDraft(request);
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
            return AgentChatResponse.builder().success(true).taskId(taskId(task)).plan(plan).message("我还不能确定要发布哪篇文章，请先打开或选择具体草稿。").build();
        }
        ConfirmationRequiredPayload confirmation = agentActionService.createPublishAction(taskId(task), user, postId);
        finishTask(task, AgentTaskStatus.WAITING_CONFIRMATION, "等待管理员确认发布文章", null);
        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
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
            return AgentChatResponse.builder().success(true).taskId(taskId(task)).plan(plan).message("我还不能确定要下架哪篇文章，请先打开或选择具体文章。").build();
        }
        ConfirmationRequiredPayload confirmation = agentActionService.createOfflineAction(taskId(task), user, postId);
        finishTask(task, AgentTaskStatus.WAITING_CONFIRMATION, "等待管理员确认下架文章", null);
        return AgentChatResponse.builder()
                .success(true)
                .taskId(taskId(task))
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
    private AdminArticleDraftRequest mergeGeneratedDraft(AgentChatRequest request) {
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

        String prompt = promptSecurityPolicy.wrapUntrustedContent("DRAFT_GENERATION_INPUT", """
                请为 LiuTech 博客生成一篇适合作为草稿的中文技术文章。
                要求：
                - 使用 Markdown 正文。
                - 结构清晰，有标题、小节和结尾。
                - 不要声称已经发布。
                - 主题来自用户要求和当前编辑器内容。

                用户要求：
                %s

                当前标题：%s
                当前摘要：%s
                当前正文：
                %s
                """.formatted(
                request.getMessage(),
                nullToEmpty(draft.getTitle()),
                nullToEmpty(draft.getSummary()),
                nullToEmpty(draft.getContent())));

        String generated = generateText(prompt, 1400);
        if (isBlank(draft.getContent())) {
            draft.setContent(generated);
        } else {
            draft.setContent(draft.getContent() + "\n\n" + generated);
        }
        if (isBlank(draft.getTitle())) {
            draft.setTitle(extractTitle(generated));
        }
        if (isBlank(draft.getSummary())) {
            draft.setSummary(buildSummary(generated));
        }
        draft.setStatus("draft");
        return draft;
    }

    /**
     * 生成文本。
     */
    private String generateText(String prompt) {
        return generateText(prompt, 800);
    }

    /**
     * 生成文本。
     */
    private String generateText(String prompt, int maxTokens) {
        try {
            List<Message> messages = List.of(
                    new SystemMessage(promptSecurityPolicy.systemRules()),
                    new UserMessage(prompt));
            return siliconFlowChatClient.chat(messages, aiModelPolicy.resolveModelName((String) null), 0.6, maxTokens);
        } catch (Exception e) {
            log.warn("Agent 文本生成失败，使用降级草稿: {}", e.getMessage());
            return "我先给你搭一个草稿框架：\n\n## 背景\n\n这里补充问题背景。\n\n## 核心思路\n\n这里展开主要观点。\n\n## 实践步骤\n\n1. 梳理目标。\n2. 设计实现路径。\n3. 验证结果。\n\n## 总结\n\n这篇文章可以继续补充真实案例和代码细节。";
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
                String trimmed = line.replace("#", "").trim();
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
        String plain = markdown == null ? "" : markdown.replaceAll("[#>*`\\-]", "").replaceAll("\\s+", " ").trim();
        if (plain.isBlank()) {
            return "纳西妲生成的文章草稿";
        }
        return plain.length() > 180 ? plain.substring(0, 180) : plain;
    }
}
