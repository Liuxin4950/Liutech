package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 预算守卫：把「模型能装多少」变成一条硬约束。
 *
 * <p>背景（本类存在的原因）：此前只有 {@code max_tokens}（单次输出上限）这一个配置，
 * 输入侧完全没有预算概念。组装好的 prompt（系统提示 + 站点上下文 + 草稿 + 历史 + 当前输入 + 工具结果）
 * 不看大小直接发给模型，于是出现两类线上问题：
 * <ul>
 *   <li>AI 用 {@code getArticleDetail} 读了一篇长文，或历史累积了多轮长回复，prompt 直接超出模型
 *       上下文窗口，上游报错或长时间不返回（表现为「模型卡住」）；</li>
 *   <li>超限之后没有任何本地校验，用户拿到的只有一句兜底提示，不知道是"内容太长"。</li>
 * </ul>
 *
 * <p>本类提供三件事：
 * <ol>
 *   <li>{@link #estimateTokens} —— 不引入 tokenizer 依赖的轻量估算（中文按 1 字 1 token，其余按 4 字符 1 token）；</li>
 *   <li>{@link #resolveLimits} —— 把「模型配置 + 全局护栏」解析成实际生效的 上下文 / 输出上限 / 输入预算；</li>
 *   <li>{@link #trimHistory} 与 {@link #assertMandatoryFits} —— 超限时的分级处理：
 *       先丢最旧的历史，丢完还不够就带着明确数字快速失败，绝不把超长 prompt 丢给上游干等。</li>
 * </ol>
 *
 * <p>为什么估算而不是精确计数：引入 tiktoken 之类的依赖要为每个模型维护词表，收益有限；
 * 我们的目的是"不超上限"，而这里的估算对中英混排偏保守（宁可少塞一点），足够安全。
 *
 * @author 刘鑫
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

    /**
     * 估算一段文本的 token 数。
     *
     * 规则：CJK 字符（汉字/假名/韩文/全角标点）按 1 字符 ≈ 1 token；
     * 其余字符（英文、代码、HTML 标签、空白）按 4 字符 ≈ 1 token 向上取整。
     * 混排时两类分别计数再相加，比"统一除以 4"更接近真实值。
     *
     * @param text 待估算文本，可为 null
     * @return 估算 token 数（非负）
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                cjkChars++;
            } else {
                otherChars++;
            }
        }
        return cjkChars + (otherChars + 3) / 4;
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
        boolean outputClamped = declaredOutput > ceiling;
        int maxOutputTokens = Math.min(declaredOutput, ceiling);
        if (outputClamped) {
            log.warn("模型输出上限 {} 超过全局安全上限 {}，实际按 {} 生效", declaredOutput, ceiling, maxOutputTokens);
        }

        // 3. 输入预算 = 上下文窗口 − 输出上限 − 安全余量，再受全局输入护栏约束
        int rawInputBudget = contextWindow - maxOutputTokens - SAFETY_MARGIN_TOKENS;
        int maxInputTokens = security.getModelPolicyMaxInputTokens();
        boolean inputCappedByPolicy = maxInputTokens > 0 && rawInputBudget > maxInputTokens;
        int inputBudgetTokens = Math.max(0, inputCappedByPolicy ? maxInputTokens : rawInputBudget);

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
        int byBudget = Math.max(500, inputBudgetTokens / 2);
        if (configuredMaxChars <= 0) {
            return byBudget;
        }
        return Math.min(configuredMaxChars, byBudget);
    }

    /** 判断字符是否属于 CJK 区段（这些字符按 1 字 ≈ 1 token 计） */
    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // 汉字基本区
                || (c >= 0x3400 && c <= 0x4DBF)  // 汉字扩展 A
                || (c >= 0x3000 && c <= 0x303F)  // 中日韩标点
                || (c >= 0xFF00 && c <= 0xFFEF)  // 全角字符
                || (c >= 0x3040 && c <= 0x30FF)  // 日文假名
                || (c >= 0xAC00 && c <= 0xD7AF); // 韩文音节
    }

    /**
     * 模型生效限制三元组。
     *
     * @param contextWindow      上下文窗口（输入 + 输出总上限）
     * @param maxOutputTokens    单次输出上限（已受全局安全上限约束）
     * @param inputBudgetTokens  输入预算（上下文 − 输出 − 安全余量，再受全局输入护栏约束）
     * @param outputClamped      输出上限是否被全局安全上限夹小
     * @param inputCappedByPolicy 输入预算是否被全局输入护栏夹小
     */
    public record ModelLimits(int contextWindow, int maxOutputTokens, int inputBudgetTokens,
                              boolean outputClamped, boolean inputCappedByPolicy) {
    }
}
