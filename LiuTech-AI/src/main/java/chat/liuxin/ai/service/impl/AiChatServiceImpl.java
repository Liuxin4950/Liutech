package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.config.AiPromptConfig;
import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.exception.AIServiceException;
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.HealthCheckService;

import chat.liuxin.ai.service.MemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI聊天核心服务实现（带“数据库记忆”版）
 * 学习顺序：
 * 1) 先读 processChat：最小可运行的一问一答 + 记忆写入/读取（窗口=20条）
 * 2) 再读 processChatStream：SSE按块推送 + 在完成/错误时再落库AI消息
 *
 * 设计要点（引导说明）：
 * - 记忆域：不分会话，一个 userId 对应一条时间线；当前用固定 "0"，未来接入JWT替换即可。
 * - 读取策略：每次调用前，取该用户最近19条历史（user/assistant/system均可）。末尾再追加本轮用户输入，组成Prompt。
 * - 写入策略：
 *   普通模式：先写入 user，再调用模型，拿到完整AI回复后写入 assistant（status=1）。异常时写入 assistant（status=9，metadata记录错误）。
 *   流式模式：先写入 user；订阅流，累计文本。onComplete 时一次性写入 assistant（status=1）；onError 时写入 assistant（status=9，metadata记录错误）。
 * - 清理策略（示例实现在 MemoryService）：按用户仅保留最近N条（可配，默认1000），多余的定期清理。这里在每次写入后“尝试触发一次轻量清理”。
 * 作者：刘鑫
 * 时间：2025-09-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;           // 记忆服务（数据库）
    private final HealthCheckService healthCheckService; // 健康检查服务
    private final RetryTemplate retryTemplate;          // 重试模板
    private final ObjectMapper objectMapper;             // 用于构造metadata的JSON
    private final AiPromptConfig aiPromptConfig;         // 系统提示词配置

    @Value("${server.base-url:http://localhost:8080}")
    private String serverBaseUrl;

    /**
     * 控制上下文窗口大小：最多拼接最近N条历史（不含本轮输入）
     * 你提出“暂时只保留20条”，这里按19条历史 + 1条本轮输入 组成本次Prompt
     */
    private static final int HISTORY_LIMIT = 19; // 历史条数（不含本轮输入）
    // 与 application.yml 示例一致的允许 action 列表
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "navigate:home",
            "navigate:create-post",
            "navigate:my-posts",
            "navigate:drafts",
            "navigate:favorites",
            "navigate:posts",
            "navigate:categories",
            "navigate:tags",
            "navigate:archive",
            "navigate:profile",
            "navigate:about",
            "navigate:chat-history",
            "interact:like",
            "interact:favorite",
            "interact:share",
            "interact:comment",
            "search:posts",
            "search:tags",
            "search:categories",
            "search:users",
            "show:capabilities",
            "none"
    );

    /**
     * 1) 普通聊天：一次性返回完整AI回复（带记忆）
     */
    @Override
    public ChatResponse processChat(ChatRequest request, Long userId) {
        // 记录开始时间,用于计算处理耗时
        long begin = System.currentTimeMillis();
        String userIdStr = userId != null ? userId.toString() : "0";
        String modelName = request.getModel() != null ? request.getModel() : "THUDM/glm-4-9b-chat";
        Long conversationId = request.getConversationId();
        String convType = request.getType() != null ? request.getType() : "general";

        try {
            // 健康检查：确保AI服务可用
            healthCheckService.checkServiceAvailability();
            
            // 提取用户输入
            String input = request.getMessage();
            
            // 处理前端上下文信息
            Map<String, Object> context = request.getContext();
            String contextPrompt = null;
            if (context != null && !context.isEmpty()) {
                // 构建基础上下文提示
                contextPrompt = "前端用户当前路由位置（仅供决策active动作，不参与输出）：" + toJson(context);
                
                // 如果在文章详情页，获取文章内容并增强用户输入
                if ("post-detail".equals(context.get("page"))) {
                    // 同时支持postId和articleId两种字段名
                    Object articleIdObj = context.get("postId");
                    if (articleIdObj == null) {
                        articleIdObj = context.get("articleId");
                    }
                    
                    if (articleIdObj != null) {
                        Long articleId = parseArticleId(articleIdObj);
                        if (articleId != null) {
                            // 获取文章内容并增强用户输入
                            String articleContent = fetchArticleContent(articleId);
                            if (articleContent != null) {
                                input = "用户在查看以下文章时提问：\n\n" + articleContent + "\n\n用户的问题是：" + input;
                                log.info("已获取文章内容，文章ID: {}", articleId);
                            }
                            // 添加文章ID到上下文提示
                            contextPrompt += "\n\n注意：用户当前正在浏览文章详情页，文章ID为" + articleId;
                        }
                    } else {
                        log.warn("文章详情页缺少文章ID: context={}", toJson(context));
                    }
                }
            }

            // 读取当前会话的最近历史（最多19条），用于作为上下文
            List<AiChatMessage> recent = conversationId != null ?
                    memoryService.listLastMessagesByConversation(conversationId, HISTORY_LIMIT) :
                    java.util.Collections.emptyList();

            // 构造消息序列：系统提示词 -> 历史 -> 上下文信息 -> 本轮用户输入
            List<Message> messages = new ArrayList<>();


            // 1.系统提示词
            messages.add(new SystemMessage(
                    aiPromptConfig.getFullSystemPrompt()
            ));
            // 2.历史信息列表
            messages.addAll(toPromptMessages(recent));
            
            // 3.前端上下文信息（如果有）
            if (contextPrompt != null) {
                messages.add(new SystemMessage(contextPrompt));
            }
            
            // 4.将用户当前输入的消息添加到消息列表末尾，作为最新一条用户消息
            messages.add(new UserMessage(input));


            if (conversationId == null) {
                String title = "新会话";
                conversationId = memoryService.createConversation(userIdStr, convType, title, null);
            }
            memoryService.saveUserMessage(userIdStr, conversationId, input, modelName, null);

            // 使用重试模板调用模型（一次性返回完整答案）
            String aiOutput = retryTemplate.execute(retryContext -> {
                log.debug("调用AI模型，第{}次尝试", retryContext.getRetryCount() + 1);
                return siliconFlowChatClient.chat(messages);
            });
            System.out.println("AI回复：\n" + (aiOutput == null ? "" : aiOutput) + '\n');

            // 解析并校验模型输出；必要时追加严格格式要求让AI重生成
            ParsedResult parsed = ensureValidStructuredOutput(messages, aiOutput);
            // 服务器侧动作门控：普通问答不触发动作，除非用户明确下达指令
            if (parsed.action != null && !"none".equals(parsed.action)) {
                if (!isExplicitActionRequest(input)) {
                    log.debug("非明确指令，强制置 action=none：原action={}", parsed.action);
                    parsed.action = "none";
                }
            }

            // 成功后落库"AI消息"（status=1），并记录元数据（含情绪/动作/metadata）
            Map<String, Object> metaSave = new HashMap<>();
            if (parsed.emotion != null) metaSave.put("emotion", parsed.emotion);
            if (parsed.action != null) metaSave.put("action", parsed.action);
            if (parsed.metadata != null && !parsed.metadata.isEmpty()) metaSave.put("metadata", parsed.metadata);
            memoryService.saveAssistantMessage(userIdStr, conversationId, parsed.message, modelName, 1, metaSave.isEmpty() ? null : toJson(metaSave));
            

             long cost = System.currentTimeMillis() - begin;
             log.debug("AI普通聊天成功，模型:{}，输入长度:{}，输出长度:{}，耗时:{}ms", modelName, input.length(), parsed.message != null ? parsed.message.length() : 0, cost);

             return ChatResponse.builder()
                     .success(true)
                     .message(parsed.message)
                     .emotion(parsed.emotion)
                     .action(parsed.action)
                     .metadata(parsed.metadata)
                     .userId(userIdStr) // 使用字符串类型的userId,与其他地方保持一致
                     .model(modelName)
                     .historyCount(recent.size() + 1) // 仅统计历史条数（不含AI本轮）
                     .timestamp(System.currentTimeMillis())
                     .processingTime(cost)
                     .responseLength(parsed.message != null ? parsed.message.length() : 0)
                     .conversationId(conversationId)
                     .build();

        } catch (AIServiceException e) {
            // AI服务特定异常，直接抛出让全局异常处理器处理
            log.error("AI服务异常: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("AI普通聊天失败", e);
            // 失败时也尝试记录一条"AI错误消息"，status=9，metadata带错误信息（帮助排查）
            try {
                Map<String, Object> meta = new HashMap<>();
                meta.put("error", e.getMessage());
                meta.put("errorType", e.getClass().getSimpleName());
                meta.put("timestamp", System.currentTimeMillis());
                memoryService.saveAssistantMessage(userIdStr, conversationId, null, modelName, 9, toJson(meta));
            } catch (Exception ignore) {
                log.warn("记录AI错误消息失败: {}", ignore.getMessage());
            }
            
            // 根据异常类型抛出相应的AI服务异常
            if (e.getCause() instanceof java.net.ConnectException || 
                e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new AIServiceException.ConnectionException("AI服务连接失败: " + e.getMessage(), e);
            } else if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new AIServiceException.TimeoutException("AI服务响应超时: " + e.getMessage(), e);
            } else {
                throw new AIServiceException("AI服务处理异常: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 2) 流式聊天：通过SSE按块推送AI回复（带记忆）
     * 关键点：
     * - 仅在 onComplete 或 onError 时落库 assistant，避免保存半截文本。
     * - onError 时写入 status=9，并将错误信息写入 metadata 便于排查。
     */
    @Override
    public SseEmitter processChatStream(ChatRequest request, Long userId) {
        String userIdStr = userId != null ? userId.toString() : "0";
        String modelName = request.getModel() != null ? request.getModel() : "THUDM/glm-4-9b-chat";
        Long conversationId = request.getConversationId();
        String convType = request.getType() != null ? request.getType() : "general";

        // 创建SSE发射器,用于服务器向客户端推送事件流
        SseEmitter emitter = new SseEmitter(30000L);
        try {
            // 健康检查：确保AI服务可用
            if (!healthCheckService.isServiceAvailable()) {
                emitter.completeWithError(new AIServiceException("AI服务当前不可用，请稍后重试"));
                return emitter;
            }
        
            emitter.send(SseEmitter.event().name("user").data(Map.of("userId", userId)));

            CompletableFuture.runAsync(() -> {
                Long convId = request.getConversationId();
                String convTypeLocal = request.getType() != null ? request.getType() : "general";
                try {
                    emitter.send(SseEmitter.event().name("start").data(Map.of("message", "AI正在思考中...")));

                    // 处理用户输入和前端上下文
                    String input = request.getMessage();
                    Map<String, Object> context = request.getContext();
                    String contextPrompt = null;
                    
                    if (context != null && !context.isEmpty()) {
                        // 构建基础上下文提示
                        contextPrompt = "前端用户当前路由位置（仅供决策active动作，不参与输出）：" + toJson(context);
                        
                        // 如果在文章详情页，获取文章内容并增强用户输入
                        if ("post-detail".equals(context.get("page"))) {
                            // 同时支持postId和articleId两种字段名
                            Object articleIdObj = context.get("postId");
                            if (articleIdObj == null) {
                                articleIdObj = context.get("articleId");
                            }
                            
                            if (articleIdObj != null) {
                                Long articleId = parseArticleId(articleIdObj);
                                if (articleId != null) {
                                    // 获取文章内容并增强用户输入
                                    String articleContent = fetchArticleContent(articleId);
                                    if (articleContent != null) {
                                        input = "用户在查看以下文章时提问：\n\n" + articleContent + "\n\n用户的问题是：" + input;
                                        log.info("已获取文章内容，文章ID: {}", articleId);
                                    }
                                    // 添加文章ID到上下文提示
                                    contextPrompt += "\n\n注意：用户当前正在浏览文章详情页，文章ID为" + articleId;
                                }
                            } else {
                                log.warn("文章详情页缺少文章ID: context={}", toJson(context));
                            }
                        }
                    }

                    // 读取当前会话最近历史（最多19条），末尾不会包含本轮输入
                    List<AiChatMessage> recent = convId != null ?
                            memoryService.listLastMessagesByConversation(convId, HISTORY_LIMIT) :
                            java.util.Collections.emptyList();

                    // 构造消息序列：系统提示词 -> 历史 -> 上下文信息 -> 本轮用户输入
                    List<Message> messages = new ArrayList<>();
                    // 1.系统提示词
                    messages.add(new SystemMessage(aiPromptConfig.getFullSystemPrompt()));
                    // 2.历史信息列表
                    messages.addAll(toPromptMessages(recent));
                    // 3.前端上下文信息（如果有）
                    if (contextPrompt != null) {
                        messages.add(new SystemMessage(contextPrompt));
                    }
                    // 4.用户输入
                    messages.add(new UserMessage(input));

                    if (convId == null) {
                        String title = "新会话";
                        convId = memoryService.createConversation(userIdStr, convTypeLocal, title, null);
                    }
                    memoryService.saveUserMessage(userIdStr, convId, input, modelName, null);

                    String full = siliconFlowChatClient.chat(messages);
                    if (full == null) full = "";
                    emitter.send(SseEmitter.event().name("data").data(Map.of("chunk", full)));
                    ParsedResult parsed = ensureValidStructuredOutput(messages, full);
                    if (parsed.action != null && !"none".equals(parsed.action)) {
                        if (!isExplicitActionRequest(input)) {
                            parsed.action = "none";
                        }
                    }
                    Map<String, Object> metaSave = new HashMap<>();
                    if (parsed.emotion != null) metaSave.put("emotion", parsed.emotion);
                    if (parsed.action != null) metaSave.put("action", parsed.action);
                    if (parsed.metadata != null && !parsed.metadata.isEmpty()) metaSave.put("metadata", parsed.metadata);
                    memoryService.saveAssistantMessage(userIdStr, convId, parsed.message, modelName, 1, metaSave.isEmpty() ? null : toJson(metaSave));
                    
                    emitter.send(SseEmitter.event().name("complete").data(Map.of(
                            "success", true,
                            "totalLength", full.length(),
                            "emotion", parsed.emotion,
                            "action", parsed.action
                    )));
                    emitter.complete();
                } catch (Exception ex) {
                    log.error("处理SSE流异常", ex);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("success", false, "message", "处理请求异常: " + ex.getMessage())));
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("error", ex.getMessage());
                        memoryService.saveAssistantMessage(userIdStr, convId, null, modelName, 9, toJson(meta));
                    } catch (IOException ioe) {
                        log.error("SSE发送异常事件失败", ioe);
                    }
                    emitter.completeWithError(ex);
                }
            });
            log.debug("AI流式聊天启动成功");
        } catch (Exception e) {
            log.error("创建SSE失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("success", false, "message", "创建流式连接失败: " + e.getMessage())));
            } catch (IOException ioe) {
                log.error("SSE发送初始化错误失败", ioe);
            }
            emitter.completeWithError(e);
        }
        return emitter;
    }

    // ==================== 辅助方法 ====================

    /**
     * 将数据库中的历史消息（升序）转换为 Spring AI 的 Message 列表
     * 说明：
     * - system/user/assistant 三种角色映射到对应Message类型；未知角色忽略
     * - 这里默认不注入“系统提示词”，如需可在最前面添加 new SystemMessage("你的系统设定...")
     */
    private List<Message> toPromptMessages(List<AiChatMessage> historyAsc) {
        if (historyAsc == null || historyAsc.isEmpty()) return new ArrayList<>();
        return historyAsc.stream().map(m -> {
            String role = Optional.ofNullable(m.getRole()).orElse("user");
            String content = Optional.ofNullable(m.getContent()).orElse("");
            switch (role) {
                case "system": return new SystemMessage(content);
                case "assistant": return new AssistantMessage(content);
                case "user":
                default: return new UserMessage(content);
            }
        }).collect(Collectors.toList());
    }

    /** 将对象转为JSON字符串（用于metadata存储） */
    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return null; }
    }
    
    /**
     * 从主服务获取文章内容
     * 
     * @param articleId 文章ID
     * @return 文章内容，包含标题和正文
     */
    private String fetchArticleContent(Long articleId) {
        try {
            // 构建请求URL
            String apiUrl = serverBaseUrl + "/posts/" + articleId;
            
            // 创建RestTemplate实例
            RestTemplate restTemplate = new RestTemplate();
            
            // 发送GET请求获取文章详情
            ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                // 解析响应JSON
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // 检查请求是否成功
                if (root.has("code") && root.get("code").asInt() == 200) {
                    JsonNode data = root.get("data");
                    
                    if (data != null) {
                        String title = data.has("title") ? data.get("title").asText() : "未知标题";
                        String content = data.has("content") ? data.get("content").asText() : "";
                        String summary = data.has("summary") ? data.get("summary").asText() : "";
                        
                        // 构建文章内容摘要
                        StringBuilder articleInfo = new StringBuilder();
                        articleInfo.append("文章标题: ").append(title).append("\n\n");
                        
                        if (!summary.isEmpty()) {
                            articleInfo.append("文章摘要: ").append(summary).append("\n\n");
                        }
                        
                        articleInfo.append("文章内容: ").append(content);
                        
                        return articleInfo.toString();
                    }
                }
            }
            
            log.warn("获取文章内容失败，状态码: {}", response.getStatusCode().value());
            return null;
        } catch (Exception e) {
            log.error("获取文章内容异常", e);
            return null;
        }
    }


    // ============== JSON解析辅助 ==============
    private static class ParsedResult {
        String message;
        String emotion;
        String action;
        Map<String, Object> metadata;
        // 校验辅助标记
        boolean isJsonObject;
        boolean hasMessage;
        boolean hasAction;
        boolean isActionAllowed;
    }

    /**
     * 解析模型输出字符串。如果是JSON对象，则提取 message/emotion/action/metadata 字段；
     * 如果不是合法JSON，则将全文作为 message 返回。
     */
    private ParsedResult parseModelOutput(String output) {
        ParsedResult r = new ParsedResult();
        if (output == null) {
            r.message = null;
            return r;
        }
        String s = output.trim();
        // 去除可能的代码块围栏 ```json ... ```
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int fence = s.lastIndexOf("```");
            if (fence > 0) s = s.substring(0, fence);
            s = s.trim();
        }
        try {
            JsonNode root = objectMapper.readTree(s);
            r.isJsonObject = root != null && root.isObject();
            if (r.isJsonObject) {
                JsonNode msgNode = root.get("message");
                if (msgNode == null) msgNode = root.get("text");
                r.message = msgNode != null && !msgNode.isNull() ? msgNode.asText("") : null;
                r.hasMessage = r.message != null && !r.message.isEmpty();
                JsonNode emoNode = root.get("emotion");
                r.emotion = emoNode != null && !emoNode.isNull() ? emoNode.asText() : null;
                JsonNode actNode = root.get("action");
                r.action = actNode != null && !actNode.isNull() ? actNode.asText() : null;
                r.hasAction = r.action != null && !r.action.isEmpty();
                r.isActionAllowed = r.action == null || ALLOWED_ACTIONS.contains(r.action);
                JsonNode metaNode = root.get("metadata");
                if (metaNode != null && metaNode.isObject()) {
                    r.metadata = objectMapper.convertValue(metaNode, new TypeReference<Map<String, Object>>() {});
                } else {
                    r.metadata = Collections.emptyMap();
                }
            } else {
                r.message = s; // 非JSON时，直接使用全文作为消息
                r.hasMessage = r.message != null && !r.message.isEmpty();
                r.metadata = Collections.emptyMap();
            }
        } catch (Exception e) {
            // 不是合法JSON：直接将原文作为message返回
            r.message = s;
            r.hasMessage = r.message != null && !r.message.isEmpty();
            r.metadata = Collections.emptyMap();
        }
        return r;
    }

    /**
     * 输出格式校验与修复：若不满足严格JSON结构或action不在允许集合中，则追加强制说明让模型重生成一次。
     */
    private ParsedResult ensureValidStructuredOutput(List<Message> baseMessages, String initialOutput) {
        ParsedResult first = parseModelOutput(initialOutput);
        if (first.isJsonObject && first.hasMessage && first.hasAction && first.isActionAllowed) {
            return first;
        }

        String allowed = String.join("|", ALLOWED_ACTIONS);
        String instruction = "严格格式要求：仅输出一个 JSON 对象，不包含任何解释或额外文本。" +
                "必须包含字段：message(string)、emotion(string)、action(string)、metadata(object)。" +
                "其中 action 的取值只能是以下之一：" + allowed + "。" +
                "请将你上一次的回答改写为上述格式，并仅输出 JSON 对象本身。";

        List<Message> fixMessages = new ArrayList<>(baseMessages);
        fixMessages.add(new SystemMessage(instruction));
        fixMessages.add(new UserMessage("原回答如下：\n" + initialOutput));

        try {
            String fixed = siliconFlowChatClient.chat(fixMessages);
            ParsedResult second = parseModelOutput(fixed);
            if (second.isJsonObject && second.hasMessage && second.hasAction && second.isActionAllowed) {
                return second;
            }
            // 仍不合规则兜底：保留message，action置为none
            if (second.message == null || second.message.isEmpty()) {
                second.message = first.message;
                second.hasMessage = second.message != null && !second.message.isEmpty();
            }
            if (!second.hasAction || !second.isActionAllowed) {
                second.action = "none";
                second.hasAction = true;
                second.isActionAllowed = true;
            }
            if (second.metadata == null) {
                second.metadata = Collections.emptyMap();
            }
            return second;
        } catch (Exception e) {
            log.warn("格式修复调用失败，使用初次解析结果兜底: {}", e.getMessage());
            if (!first.hasAction || !first.isActionAllowed) {
                first.action = "none";
                first.hasAction = true;
                first.isActionAllowed = true;
            }
            if (first.metadata == null) {
                first.metadata = Collections.emptyMap();
            }
            return first;
        }
    }

    /**
     * 解析文章ID
     * 
     * @param articleIdObj 文章ID对象
     * @return 解析后的文章ID，解析失败返回null
     */
    private Long parseArticleId(Object articleIdObj) {
        if (articleIdObj instanceof Number) {
            return ((Number) articleIdObj).longValue();
        } else if (articleIdObj instanceof String) {
            try {
                return Long.parseLong((String) articleIdObj);
            } catch (NumberFormatException e) {
                log.warn("文章ID格式错误: {}", articleIdObj);
                return null;
            }
        }
        return null;
    }

    /**
     * 判定用户是否明确请求触发动作（导航/交互/搜索/展示）。
     * 使用简单关键词匹配，避免在普通问答中误触发。
     */
    private boolean isExplicitActionRequest(String input) {
        if (input == null) return false;
        String s = input.trim();
        if (s.isEmpty()) return false;
        // 关键动词与常见表达（中文）
        String[] keywords = {
                "跳转", "打开", "进入", "去", "前往", "导航", "切换",
                "点赞", "收藏", "分享", "评论",
                "搜索", "查找",
                "展示", "显示"
        };
        String lower = s.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        // 简单指令格式，例如：navigate:home / interact:like 等
        for (String allowed : ALLOWED_ACTIONS) {
            if (lower.contains(allowed)) {
                return true;
            }
        }
        return false;
    }
}
