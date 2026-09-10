package chat.liuxin.ai.common.mcp;

import chat.liuxin.ai.dto.PostDetailDTO;
import chat.liuxin.ai.infra.config.AiChatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

/**
 * 工具结果体积控制：给"按 ID 读整篇文章"这类工具的结果加一道上限。
 *
 * <p>为什么必须有这一层：写作助手与看板娘都提供读全文的工具（{@link WritingTools#getArticleDetail}、
 * {@link BlogMcpTools#getPostDetail}），返回的是含完整 HTML 正文的 {@link PostDetailDTO}。
 * 一篇长文正文可能上万字，整篇塞进上下文会直接顶穿模型上下文窗口 ——
 * 线上表现就是"模型卡住、没有提示"（上游长时间不返回，最终只能等 SSE 超时）。
 *
 * <p>预算来源分两种：
 * <ul>
 *   <li>流式写作路径由 {@code StreamingChatService} 按模型输入预算算出，通过
 *       {@link ToolContext} 的 {@link #CONTEXT_KEY} 传入（不同模型上下文不同，预算也不同）；</li>
 *   <li>没有传（同步接口等）时用 {@code spring.ai.agent.max-tool-result-chars} 兜底。</li>
 * </ul>
 *
 * <p>截断是"显式"的：被截断的结果会带上明确的说明文字，让模型知道正文不完整、
 * 必要时改用其它手段（分段读取或让用户补充），而不是让它以为读到了全部内容。
 *
 * @author 刘鑫
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolResultBudget {

    /** ToolContext 中传递「本次请求单个工具结果的字符预算」的键 */
    public static final String CONTEXT_KEY = "toolResultCharBudget";

    /** 截断提示：告诉模型正文不完整，避免它基于半篇内容下结论 */
    private static final String TRUNCATED_NOTICE = "\n\n...(正文过长已截断，仅包含前 %d 字。如需后续内容请按段落另行读取)";

    private final AiChatProperties aiChatProperties;

    /**
     * 解析本次工具调用可用的字符预算。
     *
     * @param toolContext 工具上下文（可为 null）
     * @return 字符预算（正数）
     */
    public int resolveCharBudget(ToolContext toolContext) {
        Object fromContext = toolContext == null || toolContext.getContext() == null
                ? null
                : toolContext.getContext().get(CONTEXT_KEY);
        if (fromContext instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        int fallback = aiChatProperties.getAgent().getMaxToolResultChars();
        return fallback > 0 ? fallback : Integer.MAX_VALUE;
    }

    /**
     * 按预算截断一段文本。
     *
     * @param text        原始文本，可为 null
     * @param toolContext 工具上下文，用于取本次预算
     * @param what        内容名称（仅用于日志，例如"文章正文"）
     * @return 截断后的文本；未超限时原样返回
     */
    public String truncate(String text, ToolContext toolContext, String what) {
        if (text == null) {
            return null;
        }
        int budget = resolveCharBudget(toolContext);
        if (text.length() <= budget) {
            return text;
        }
        log.info("工具结果超预算已截断 - 内容: {}, 原长度: {} 字符, 预算: {} 字符", what, text.length(), budget);
        return text.substring(0, budget) + TRUNCATED_NOTICE.formatted(budget);
    }

    /**
     * 截断文章详情里的正文部分（标题/分类/标签等元信息保留，只压正文）。
     *
     * @param detail      文章详情，可为 null
     * @param toolContext 工具上下文
     * @return 处理后的文章详情（同一个对象，正文已被就地替换）
     */
    public PostDetailDTO truncateArticleContent(PostDetailDTO detail, ToolContext toolContext) {
        if (detail == null || detail.getContent() == null) {
            return detail;
        }
        detail.setContent(truncate(detail.getContent(), toolContext, "文章正文(id=" + detail.getId() + ")"));
        return detail;
    }
}
