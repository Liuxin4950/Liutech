package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型输入预算。以 UTF-8 字节数作保守 Token 上界，不把该值当实际计费用量。
 * 包含正文、工具 schema、工具参数及返回值；输出与安全余量从模型上下文中预留。
 * 不具备对应模型 tokenizer 时会少用一部分窗口，以避免高熵/多字节文本低估。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptBudget {

    /**
     * 安全余量（token）
     *
     * 用途：吸收估算误差、工具定义（tools schema）以及消息包装带来的额外开销。
     * 这部分不参与业务内容分配，任何情况下都从输入预算里扣掉。
     */
    public static final int SAFETY_MARGIN_TOKENS = 512;

    /** 模型未配置 max_tokens 时的输出上限兜底值 */
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;

    private final AiChatProperties aiChatProperties;

    /** UTF-8 保守预算，未配对代理字符按 JSON 转义的6字节预算。 */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int bytes = 0;
        for (int i = 0; i < text.length();) {
            int point = text.codePointAt(i);
            i += Character.charCount(point);
            // 使用 UTF-8 字节上界，避免 emoji、罕见字和高熵文本绕过字符/4估算。
            bytes += point <= 0x7f ? 1 : point <= 0x7ff ? 2
                    : point >= 0xd800 && point <= 0xdfff ? 6 : point <= 0xffff ? 3 : 4;
        }
        return bytes;
    }

    /**
     * 估算一组消息的 token 数（正文 + 每条消息的包装开销）。
     *
     * @param messages 消息列表，可为 null
     * @return 估算 token 数
     */
    public int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message message : messages) {
            if (message == null) continue;
            total += estimateTokens(message.getText());
            if (message instanceof AssistantMessage assistant) {
                for (var call : assistant.getToolCalls()) {
                    total += estimateTokens(call.name()) + estimateTokens(call.arguments()) + 8;
                }
            } else if (message instanceof ToolResponseMessage tool) {
                for (var response : tool.getResponses()) {
                    total += estimateTokens(response.name()) + estimateTokens(response.responseData()) + 8;
                }
            }
            // 每条消息的角色标签、分隔符等固定开销，粗略按 4 token 计
            total += 4;
        }
        return total;
    }

    /**
     * 解析某模型实际生效的限制。
     *
     * @param configuredMaxTokens     管理端配置的单次输出上限，可为 null
     * @param configuredContextWindow 管理端配置的上下文窗口，可为 null（回退到全局默认）
     * @return 生效限制；其中 inputBudgetTokens 就是"这次能塞多少输入"的答案
     */
    public ModelLimits resolveLimits(Integer configuredMaxTokens, Integer configuredContextWindow) {
        AiChatProperties.Security security = aiChatProperties.getSecurity();

        // 1. 上下文窗口：未配置时用保守默认值，避免"没配就等于无限"
        int contextWindow = (configuredContextWindow != null && configuredContextWindow > 0)
                ? configuredContextWindow
                : security.getModelPolicyDefaultContextWindow();

        // 2. 输出上限：受全局安全上限约束（防止误填天文数字把额度一次打空）
        int ceiling = security.getModelPolicyMaxTokensCeiling();
        int declaredOutput = (configuredMaxTokens != null && configuredMaxTokens > 0)
                ? configuredMaxTokens
                : DEFAULT_MAX_OUTPUT_TOKENS;
        boolean outputClamped = ceiling > 0 && declaredOutput > ceiling;
        int maxOutputTokens = ceiling > 0 ? Math.min(declaredOutput, ceiling) : declaredOutput;
        if (outputClamped) {
            log.warn("模型输出上限 {} 超过全局安全上限 {}，实际按 {} 生效", declaredOutput, ceiling, maxOutputTokens);
        }

        // 3. 输入预算 = 上下文窗口 − 输出上限 − 安全余量，再受全局输入护栏约束
        long rawInputBudget = (long) contextWindow - maxOutputTokens - SAFETY_MARGIN_TOKENS;
        int maxInputTokens = security.getModelPolicyMaxInputTokens();
        boolean inputCappedByPolicy = maxInputTokens > 0 && rawInputBudget > maxInputTokens;
        int inputBudgetTokens = (int) Math.max(0L, Math.min(Integer.MAX_VALUE,
                inputCappedByPolicy ? maxInputTokens : rawInputBudget));

        if (rawInputBudget <= 0) {
            log.warn("上下文窗口 {} 小于输出上限 {} + 安全余量 {}，输入预算为 0，模型配置自相矛盾",
                    contextWindow, maxOutputTokens, SAFETY_MARGIN_TOKENS);
        }

        return new ModelLimits(contextWindow, maxOutputTokens, inputBudgetTokens,
                outputClamped, inputCappedByPolicy);
    }

    /**
     * 按可用预算裁剪历史消息（从最旧开始整条丢弃）。
     *
     * 整条丢而不是截断单条：半截的对话内容比没有更容易误导模型，且会破坏 user/assistant 配对。
     *
     * @param history         历史消息（不含系统提示与当前输入）
     * @param availableTokens 留给历史的 token 预算
     * @return 能装下的历史（保持原有顺序）；预算为 0 或历史为空时返回空列表
     */
    public List<Message> trimHistory(List<Message> history, int availableTokens) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }
        if (availableTokens <= 0) {
            log.info("输入预算已无剩余，丢弃全部历史消息（共 {} 条）", history.size());
            return new ArrayList<>();
        }

        // 从最新往回累加，直到超预算，剩下的就是要保留的后缀
        int used = 0;
        int keepFrom = history.size();
        for (int i = history.size() - 1; i >= 0; i--) {
            int cost = estimateTokens(history.get(i).getText()) + 4;
            if (used + cost > availableTokens) {
                break;
            }
            used += cost;
            keepFrom = i;
        }

        if (keepFrom > 0) {
            log.info("历史消息超出输入预算，丢弃最旧 {} 条（保留 {} 条，约 {} token）",
                    keepFrom, history.size() - keepFrom, used);
        }
        return new ArrayList<>(history.subList(keepFrom, history.size()));
    }

    /** 三类业务共用首次输入分配：必需内容不截断，参考资料节选，历史整条裁剪。 */
    public List<Message> prepareInitial(String model, AiModelPolicy.ModelParameters params,
            List<Message> mandatory, List<Message> references, List<Message> history,
            Message currentInput, List<ToolCallback> tools) {
        int required = estimateTokens(mandatory) + estimateTokens(List.of(currentInput)) + estimateToolTokens(tools);
        assertMandatoryFits(model, required, params.inputBudgetTokens(), params.contextWindow(), params.maxTokens());
        int remaining = params.inputBudgetTokens() - required;
        remaining -= Math.min(remaining / 2, Math.min(12000, params.inputBudgetTokens() / 4));
        int referenceBudget = remaining / 2;
        List<Message> messages = new ArrayList<>(mandatory);
        for (Message reference : references) {
            if (referenceBudget < 128) break;
            String text = reference.getText();
            int cost = estimateTokens(text) + 4;
            if (cost > referenceBudget) {
                text = truncateReference(text, referenceBudget - 4,
                    "\n[参考资料已按模型预算节选，不能声称已读取全文；请使用分段读取工具。]");
                cost = estimateTokens(text) + 4;
            }
            messages.add(new org.springframework.ai.chat.messages.UserMessage(text));
            remaining -= cost;
            referenceBudget -= cost;
        }
        messages.addAll(trimHistory(history, remaining));
        messages.add(currentInput);
        return messages;
    }

    /**
     * 校验「必需内容」是否放得下，放不下就快速失败。
     *
     * 必需内容 = 系统提示 + 站点/草稿上下文 + 当前用户输入。这些丢了就没法干活，
     * 与其把超长 prompt 发给上游干等（旧行为：等 5 分钟 SSE 超时，用户看不到原因），
     * 不如立刻返回一条能采取行动的错误。
     *
     * 参数刻意用基本类型而不是 {@link ModelLimits}：调用方（ChatServiceHelper）手上
     * 拿到的是 AiModelPolicy 解析出的生效参数，不希望为了校验再回查一次模型配置。
     *
     * @param modelName        模型名（用于提示文案）
     * @param mandatoryTokens  必需内容的估算 token
     * @param inputBudgetTokens 生效输入预算
     * @param contextWindow    生效上下文窗口
     * @param maxOutputTokens  生效输出上限
     * @throws AIServiceException.RequestException 超出预算时
     */
    public void assertMandatoryFits(String modelName, int mandatoryTokens, int inputBudgetTokens,
                                    int contextWindow, int maxOutputTokens) {
        if (inputBudgetTokens <= 0) {
            throw new AIServiceException.RequestException(
                    "模型「%s」的配置有冲突：上下文窗口 %d token 减去输出上限 %d token 后已无输入空间，请在管理端调大上下文窗口或调小最大 Token"
                            .formatted(modelName, contextWindow, maxOutputTokens));
        }
        if (mandatoryTokens > inputBudgetTokens) {
            throw new AIServiceException.RequestException(
                    "输入内容过长：本次请求必需内容约 %d token，而模型「%s」单次可用输入约 %d token（上下文 %d − 输出上限 %d − 安全余量 %d）。请精简当前输入，或在管理端为该模型调大上下文窗口"
                            .formatted(mandatoryTokens, modelName, inputBudgetTokens,
                                    contextWindow, maxOutputTokens, SAFETY_MARGIN_TOKENS));
        }
    }

    /**
     * 推导「单个工具结果」可用的字符预算。
     *
     * 取「配置上限」与「输入预算的一半」中的较小值：
     * - 配置上限（spring.ai.agent.max-tool-result-chars）防止单次读入一篇超长文；
     * - 输入预算的一半保证读完文章后仍留一半空间给系统提示、草稿、历史与模型输出。
     * 下限 500 字符，避免预算被算成 0 导致工具"什么都读不到"。
     *
     * 中文场景下 1 字符 ≈ 1 token，因此这里直接把 token 预算当字符预算用（偏保守）。
     *
     * @param inputBudgetTokens   本次请求的输入预算
     * @param configuredMaxChars  配置的单次工具结果上限
     * @return 字符预算
     */
    public int toolResultCharBudget(int inputBudgetTokens, int configuredMaxChars) {
        int byBudget = Math.max(0, inputBudgetTokens / 2);
        if (configuredMaxChars <= 0) {
            return byBudget;
        }
        return Math.min(configuredMaxChars, byBudget);
    }

    /** 工具 schema 同样属于模型输入，不能只数正文。 */
    public int estimateToolTokens(List<ToolCallback> tools) {
        int tokens = 0;
        for (ToolCallback tool : tools) {
            var definition = tool.getToolDefinition();
            tokens += estimateTokens(definition.name()) + estimateTokens(definition.description())
                    + estimateTokens(definition.inputSchema()) + 16;
        }
        return tokens;
    }

    /** 不切断 Unicode 码点；截断说明也占预算。原始写作草稿不使用此方法。 */
    public String truncateReference(String text, int maxTokens, String notice) {
        if (text == null || estimateTokens(text) <= maxTokens) return text;
        int remaining = Math.max(0, maxTokens - estimateTokens(notice));
        int end = 0;
        int used = 0;
        while (end < text.length()) {
            int point = text.codePointAt(end);
            int chars = Character.charCount(point);
            int cost = estimateTokens(text.substring(end, end + chars));
            if (used + cost > remaining) break;
            used += cost;
            end += chars;
        }
        return estimateTokens(notice) > maxTokens ? "" : text.substring(0, end) + notice;
    }

    public record ModelLimits(int contextWindow, int maxOutputTokens, int inputBudgetTokens,
                              boolean outputClamped, boolean inputCappedByPolicy) {
    }
}
