package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.config.AiPromptConfig;
import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.exception.AIServiceException;
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.HealthCheckService;
import chat.liuxin.ai.service.ContextEnhancementService;
import chat.liuxin.ai.service.IntentRecognitionService;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

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

    private final OllamaChatModel chatModel;
    private final MemoryService memoryService;           // 记忆服务（数据库）
    private final HealthCheckService healthCheckService; // 健康检查服务
    private final RetryTemplate retryTemplate;          // 重试模板
    private final ContextEnhancementService contextEnhancementService;
    private final IntentRecognitionService intentRecognitionService;
    private final ObjectMapper objectMapper;             // 用于构造metadata的JSON
    private final AiPromptConfig aiPromptConfig;         // 系统提示词配置

    /**
     * 控制上下文窗口大小：最多拼接最近N条历史（不含本轮输入）
     * 你提出“暂时只保留20条”，这里按19条历史 + 1条本轮输入 组成本次Prompt
     */
    private static final int HISTORY_LIMIT = 9; // 历史条数（不含本轮输入）

    /**
     * 1) 普通聊天：一次性返回完整AI回复（带记忆）
     */
    @Override
    public ChatResponse processChat(ChatRequest request, Long userId) {
        // 记录开始时间,用于计算处理耗时
        long begin = System.currentTimeMillis();
        String userIdStr = userId != null ? userId.toString() : "0"; // 从JWT解析获得的用户ID
        String modelName = request.getModel() != null ? request.getModel() : "ollama";

        try {
            // 健康检查：确保AI服务可用
            healthCheckService.checkServiceAvailability();
            
            // 提取用户输入
            String input = request.getMessage();
            
            // 上下文增强和意图识别
            List<AiChatMessage> recentHistory = memoryService.listRecentMessages(userIdStr, 5);
            ContextEnhancementService.EnhancedContext enhancedContext = 
                contextEnhancementService.enhanceUserInput(input, request.getContext(), recentHistory);
            IntentRecognitionService.IntentResult intentResult = 
                intentRecognitionService.recognizeIntent(input);
            
            log.info("用户意图识别 - 主要意图: {}, 置信度: {:.2f}, 紧急程度: {}", 
                intentResult.getPrimaryIntent(), intentResult.getConfidence(), intentResult.getSecondaryIntent());
            log.debug("上下文增强 - 意图: {}, 情感: {}, 关键词: {}", 
                enhancedContext.getIntent(), enhancedContext.getEmotion(), enhancedContext.getKeywords());
            
            String enhancedInput = enhancedContext.getEnhancedInput();
            
            // 处理上下文信息，获取文章内容（如果在文章详情页）
             Map<String, Object> context = request.getContext();
             if (context != null && "post-detail".equals(context.get("page"))) {
                 // 同时支持postId和articleId两种字段名
                 Object articleIdObj = context.get("postId");
                 if (articleIdObj == null) {
                     articleIdObj = context.get("articleId"); // 兼容可能使用articleId的情况
                 }
                 
                 if (articleIdObj != null) {
                     Long articleId = null;
                     if (articleIdObj instanceof Number) {
                         articleId = ((Number) articleIdObj).longValue();
                     } else if (articleIdObj instanceof String) {
                         try {
                             articleId = Long.parseLong((String) articleIdObj);
                         } catch (NumberFormatException e) {
                             log.warn("文章ID格式错误: {}", articleIdObj);
                         }
                     }
                     
                     if (articleId != null) {
                         // 获取文章内容的逻辑
                         String articleContent = fetchArticleContent(articleId);
                         if (articleContent != null) {
                             // 将文章内容添加到用户输入中，增强AI的回答能力
                             enhancedInput = "用户在查看以下文章时提问：\n\n" + articleContent + "\n\n用户的问题是：" + enhancedInput;
                             log.info("已获取文章内容，文章ID: {}\n\n\n文章内容: {}\n\n", articleId, articleContent);
                         }
                     }
                 } else {
                     log.warn("文章详情页缺少文章ID: context={}", toJson(context));
                 }
             }

            // 读取最近历史（最多19条），用于作为上下文
            List<AiChatMessage> recent = memoryService.listRecentMessages(userIdStr, HISTORY_LIMIT);

            // 构造消息序列：系统提示词 -> 历史 -> （可选）RAG上下文 -> JSON输出约束 -> 本轮用户输入
            List<Message> messages = new ArrayList<>();

            messages.add(new SystemMessage(
                    aiPromptConfig.getFullSystemPrompt()
            ));
            // 重构历史列表
            messages.addAll(toPromptMessages(recent));

            // 注入前端上下文（若有）以及JSON输出规范
            if (request.getContext() != null && !request.getContext().isEmpty()) {
                // 使用已有的context变量,避免重复声明
                String contextPrompt = "前端用户当前路由位置（仅供决策active动作，不要放入message正常对话）：" + toJson(context);
                
                // 为文章详情页添加特殊提示
                if ("post-detail".equals(context.get("page"))) {
                    Object postId = context.get("postId");
                    if (postId == null) {
                        postId = context.get("articleId");
                    }
                    if (postId != null) {
                        contextPrompt += "\n\n注意：用户当前正在浏览文章详情页，文章ID为" + postId + 
                                        "。你可以针对当前文章内容回答用户问题，如果用户询问'当前文章'相关内容，请理解用户是在询问他正在浏览的这篇文章。";
                    }
                }
                
                messages.add(new SystemMessage(contextPrompt));
            }

            // 将用户当前输入的消息添加到消息列表末尾，作为最新一条用户消息
            messages.add(new UserMessage(enhancedInput));


            // 先落库"用户消息"（立即写入，便于审计/追踪）
            memoryService.saveUserMessage(userIdStr, input, modelName, null);

            // 使用重试模板调用模型（一次性返回完整答案）
            var response = retryTemplate.execute(retryContext -> {
                log.debug("调用AI模型，第{}次尝试", retryContext.getRetryCount() + 1);
                return chatModel.call(new Prompt(messages));
            });
            
            String aiOutput = response.getResult().getOutput().getContent();
            System.out.println("AI回复：\n" + aiOutput + '\n');

            // 解析模型输出：尝试按JSON提取 message/emotion/action/metadata
            ParsedResult parsed = parseModelOutput(aiOutput);

            // 成功后落库"AI消息"（status=1），并记录元数据（含情绪/动作/metadata）
            Map<String, Object> metaSave = new HashMap<>();
            if (parsed.emotion != null) metaSave.put("emotion", parsed.emotion);
            if (parsed.action != null) metaSave.put("action", parsed.action);
            if (parsed.metadata != null && !parsed.metadata.isEmpty()) metaSave.put("metadata", parsed.metadata);
            memoryService.saveAssistantMessage(userIdStr, parsed.message, modelName, 1, metaSave.isEmpty() ? null : toJson(metaSave));
             // 可选：轻量清理，保留每用户最近10条（或按需配置）
             memoryService.cleanupByRetainLastN(userIdStr, 10);

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
                memoryService.saveAssistantMessage(userIdStr, null, modelName, 9, toJson(meta));
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
        String userIdStr = userId != null ? userId.toString() : "0"; // 从JWT解析获得的用户ID
        String modelName = request.getModel() != null ? request.getModel() : "ollama";

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
                try {
                    emitter.send(SseEmitter.event().name("start").data(Map.of("message", "AI正在思考中...")));

                    // 上下文增强和意图识别
                    List<AiChatMessage> recentHistory = memoryService.listRecentMessages(userIdStr, 5);
                    ContextEnhancementService.EnhancedContext enhancedContext = 
                        contextEnhancementService.enhanceUserInput(request.getMessage(), request.getContext(), recentHistory);
                    IntentRecognitionService.IntentResult intentResult = 
                        intentRecognitionService.recognizeIntent(request.getMessage());
                    
                    log.info("流式聊天 - 用户意图: {}, 置信度: {:.2f}, 紧急程度: {}", 
                        intentResult.getPrimaryIntent(), intentResult.getConfidence(), intentResult.getSecondaryIntent());

                    // 读取最近历史（最多19条），末尾不会包含本轮输入
                    List<AiChatMessage> recent = memoryService.listRecentMessages(userIdStr, HISTORY_LIMIT);

                    // 构造消息序列：系统提示词 -> 历史 -> （可选）RAG上下文 -> 本轮用户输入
                    List<Message> messages = new ArrayList<>();
                    // 系统提示词
                    String systemPrompt = aiPromptConfig.getFullSystemPrompt();

                    messages.add(new SystemMessage(systemPrompt));
                    // 转换为信息列表格式
                    messages.addAll(toPromptMessages(recent));
                    // 前端上下文（如果有）
                    if (request.getContext() != null && !request.getContext().isEmpty()) {
                        messages.add(new SystemMessage("前端上下文（仅供决策，不要复述）：" + toJson(request.getContext())));
                    }
                    // 使用增强后的用户输入
                    messages.add(new UserMessage(enhancedContext.getEnhancedInput()));

                    // 先落库"用户消息"
                    memoryService.saveUserMessage(userIdStr, request.getMessage(), modelName, null);

                    // 调用模型流式接口
                    Flux<org.springframework.ai.chat.model.ChatResponse> stream = chatModel.stream(new Prompt(messages));

                    StringBuilder full = new StringBuilder();

                    stream.subscribe(
                            part -> {
                                try {
                                    String chunk = part.getResult().getOutput().getContent();
                                    if (chunk != null && !chunk.isEmpty()) {
                                        full.append(chunk);
                                        emitter.send(SseEmitter.event().name("data").data(Map.of("chunk", chunk)));
                                    }
                                } catch (IOException ioe) {
                                    log.error("SSE发送数据失败", ioe);
                                    emitter.completeWithError(ioe);
                                }
                            },
                            err -> {
                                log.error("AI流式响应异常", err);
                                try {
                                    // onError：发给前端错误，同时记录错误版AI消息（status=9）
                                    emitter.send(SseEmitter.event().name("error").data(Map.of("success", false, "message", "AI服务异常: " + err.getMessage())));
                                    Map<String, Object> meta = new HashMap<>();
                                    meta.put("error", err.getMessage());
                                    memoryService.saveAssistantMessage(userIdStr, full.length() > 0 ? full.toString() : null, modelName, 9, toJson(meta));
                                } catch (IOException ioe) {
                                    log.error("SSE发送错误事件失败", ioe);
                                } finally {
                                    emitter.completeWithError(err);
                                }
                            },
                            () -> {
                                try {
                                    // onComplete：解析并一次性写入完整AI消息（status=1）
                                    ParsedResult parsed = parseModelOutput(full.toString());
                                    Map<String, Object> metaSave = new HashMap<>();
                                    if (parsed.emotion != null) metaSave.put("emotion", parsed.emotion);
                                    if (parsed.action != null) metaSave.put("action", parsed.action);
                                    if (parsed.metadata != null && !parsed.metadata.isEmpty()) metaSave.put("metadata", parsed.metadata);
                                    memoryService.saveAssistantMessage(userIdStr, parsed.message, modelName, 1, metaSave.isEmpty() ? null : toJson(metaSave));
                                     memoryService.cleanupByRetainLastN(userIdStr, 10);

                                    // 完成事件可附带解析后的辅助信息，前端可按需消费
                                    emitter.send(SseEmitter.event().name("complete").data(Map.of(
                                            "success", true,
                                            "totalLength", full.length(),
                                            "emotion", parsed.emotion,
                                            "action", parsed.action
                                    )));
                                     emitter.complete();
                                } catch (IOException ioe) {
                                    log.error("SSE发送完成事件失败", ioe);
                                    emitter.completeWithError(ioe);
                                }
                            }
                    );
                } catch (Exception ex) {
                    log.error("处理SSE流异常", ex);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("success", false, "message", "处理请求异常: " + ex.getMessage())));
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("error", ex.getMessage());
                        memoryService.saveAssistantMessage(userIdStr, null, modelName, 9, toJson(meta));
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
            String apiUrl = "http://localhost:8080/posts/" + articleId;
            
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
            
            log.warn("获取文章内容失败，状态码: {}", response.getStatusCodeValue());
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
            if (root != null && root.isObject()) {
                JsonNode msgNode = root.get("message");
                if (msgNode == null) msgNode = root.get("text");
                r.message = msgNode != null && !msgNode.isNull() ? msgNode.asText("") : null;
                JsonNode emoNode = root.get("emotion");
                r.emotion = emoNode != null && !emoNode.isNull() ? emoNode.asText() : null;
                JsonNode actNode = root.get("action");
                r.action = actNode != null && !actNode.isNull() ? actNode.asText() : null;
                JsonNode metaNode = root.get("metadata");
                if (metaNode != null && metaNode.isObject()) {
                    r.metadata = objectMapper.convertValue(metaNode, new TypeReference<Map<String, Object>>() {});
                } else {
                    r.metadata = null;
                }
                // 若未提供message，则回退为原始文本
                if (r.message == null || r.message.isEmpty()) {
                    r.message = output;
                }
                return r;
            }
        } catch (Exception ignore) {
            // 非JSON，直接回退为文本
        }
        r.message = output;
        r.emotion = null;
        r.action = null;
        r.metadata = null;
        return r;
    }
}
