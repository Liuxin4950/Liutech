package chat.liuxin.ai.agent.application;

import chat.liuxin.ai.agent.response.AgentToolEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Agent 工具执行服务。
 *
 * 封装工具执行逻辑，统一处理：
 * - tool-start 事件发送
 * - 工具执行（通过 Supplier）
 * - tool-result 事件发送
 * - 审计日志记录
 *
 * 设计说明：
 * - 工具执行前后自动发送 SSE 事件，前端可展示"正在查询..."等状态
 * - 不记录 token、API key、系统提示词等敏感信息
 * - 工具执行失败时发送 failure 的 tool-result 事件
 *
 * @author liuxin
 * @see AgentStreamPublisher
 * @see AgentToolCallRecorder
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolExecutionService {

    private final AgentStreamPublisher streamPublisher;
    private final AgentToolCallRecorder toolCallRecorder;

    /**
     * 工具名称到展示名称的映射。
     * 用于前端展示友好的工具名称。
     */
    private static final Map<String, String> TOOL_DISPLAY_NAMES = Map.of(
            "public.searchArticles", "搜索文章",
            "public.recommendBySearch", "搜索推荐",
            "public.latestArticles", "最新文章",
            "public.getArticleDetail", "读取当前文章",
            "admin.listCategories", "读取分类列表",
            "admin.listTags", "读取标签列表",
            "admin.generateWritingHtml", "生成富文本 HTML",
            "admin.createDraft", "创建草稿",
            "admin.publishPost", "发布文章",
            "admin.offlinePost", "下架文章"
    );

    /**
     * 执行工具并发送 SSE 事件。
     *
     * 执行流程：
     * 1. 发送 tool-start 事件
     * 2. 记录审计日志（输入）
     * 3. 执行工具（通过 supplier）
     * 4. 记录审计日志（输出/错误）
     * 5. 发送 tool-result 事件
     *
     * @param context    SSE 上下文，非空
     * @param toolName  工具名称，非空
     * @param input     工具输入参数，用于摘要和审计
     * @param supplier  工具执行逻辑，内部捕获异常
     * @param <T>      工具返回类型
     * @return 工具执行结果，如果执行失败返回 null
     */
    public <T> T execute(AgentSseContext context, String toolName, Object input, Supplier<T> supplier) {
        String displayName = TOOL_DISPLAY_NAMES.getOrDefault(toolName, toolName);
        String inputSummary = summarizeInput(input);

        // 发送 tool-start 事件
        streamPublisher.sendToolStart(
                context.getEmitter(),
                context.getTaskId(),
                context.getConversationId(),
                toolName,
                displayName,
                inputSummary);

        long start = System.currentTimeMillis();

        try {
            // 执行工具
            T result = toolCallRecorder.record(context.getTaskId(), toolName, input, supplier);

            long durationMs = System.currentTimeMillis() - start;
            String resultSummary = summarizeOutput(toolName, result);

            // 发送 tool-result 成功事件
            streamPublisher.sendToolResultSuccess(
                    context.getEmitter(),
                    context.getTaskId(),
                    context.getConversationId(),
                    toolName,
                    displayName,
                    durationMs,
                    resultSummary);

            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";

            // 记录失败审计
            toolCallRecorder.recordFailure(context.getTaskId(), toolName, input, errorMessage, durationMs);

            // 发送 tool-result 失败事件
            streamPublisher.sendToolResultFailure(
                    context.getEmitter(),
                    context.getTaskId(),
                    context.getConversationId(),
                    toolName,
                    displayName,
                    durationMs,
                    errorMessage);

            return null;
        }
    }

    /**
     * 汇总输入参数为可读字符串。
     * 不记录敏感信息，只记录关键参数。
     * 例如："keyword=Spring, limit=6"
     */
    private String summarizeInput(Object input) {
        if (input == null) {
            return "";
        }
        if (input instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            map.forEach((key, value) -> {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                String keyStr = String.valueOf(key);
                if (isSensitiveKey(keyStr)) {
                    sb.append(keyStr).append("=***");
                } else {
                    sb.append(keyStr).append("=").append(String.valueOf(value));
                }
            });
            return sb.toString();
        }
        return String.valueOf(input);
    }

    /**
     * 汇总输出结果为可读字符串。
     * 例如："返回 6 条结果"、"执行成功"
     */
    private String summarizeOutput(String toolName, Object output) {
        if (output == null) {
            return "无结果";
        }
        if ("admin.generateWritingHtml".equals(toolName)) {
            if (output instanceof String s && !s.isBlank()) {
                return "已生成富文本 HTML";
            }
            return "未生成正文";
        }
        if (output instanceof java.util.Collection<?> collection) {
            return "返回 " + collection.size() + " 条结果";
        }
        if (output instanceof String s) {
            String text = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            return text.length() > 40 ? text.substring(0, 40) + "..." : text;
        }
        return "执行成功";
    }

    /**
     * 判断是否为敏感字段。
     * 敏感字段在摘要中会被替换为 ***。
     */
    private boolean isSensitiveKey(String key) {
        return "token".equalsIgnoreCase(key)
                || "apiKey".equalsIgnoreCase(key)
                || "api_key".equalsIgnoreCase(key)
                || "password".equalsIgnoreCase(key)
                || "secret".equalsIgnoreCase(key)
                || "bearerToken".equalsIgnoreCase(key);
    }
}
