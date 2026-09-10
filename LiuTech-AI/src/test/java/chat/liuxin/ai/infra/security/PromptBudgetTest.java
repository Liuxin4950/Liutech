package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PromptBudget 单元测试。
 *
 * 覆盖三件事：token 估算（中英混排）、历史裁剪（丢最旧、整条丢）、超限快速失败（文案里要有可行动的数字）。
 */
class PromptBudgetTest {

    private AiChatProperties props;
    private PromptBudget budget;

    @BeforeEach
    void setUp() {
        props = new AiChatProperties();
        budget = new PromptBudget(props);
    }

    // ==================== token 估算 ====================

    @Test
    void shouldEstimateChineseAsOneTokenPerChar() {
        // 10 个汉字 ≈ 10 token（不含包装开销）
        assertEquals(10, budget.estimateTokens("你好世界一二三四五六"));
    }

    @Test
    void shouldEstimateAsciiAsQuarterTokenPerChar() {
        // 40 个 ASCII 字符 ≈ 10 token
        assertEquals(10, budget.estimateTokens("a".repeat(40)));
    }

    @Test
    void shouldEstimateMixedTextBySummingBothParts() {
        // 5 汉字 + 40 ASCII ≈ 5 + 10 = 15
        assertEquals(15, budget.estimateTokens("你好世界啊" + "b".repeat(40)));
    }

    @Test
    void shouldTreatNullAndEmptyAsZero() {
        assertEquals(0, budget.estimateTokens((String) null));
        assertEquals(0, budget.estimateTokens(""));
    }

    @Test
    void shouldCountPerMessageOverheadWhenEstimatingMessages() {
        List<Message> messages = List.of(new UserMessage("你好"), new AssistantMessage("你好"));
        // 2 条消息各 2 token 内容 + 各 4 token 包装开销
        assertEquals(12, budget.estimateTokens(messages));
    }

    // ==================== 生效限制解析 ====================

    @Test
    void shouldDeriveInputBudgetFromContextMinusOutputMinusMargin() {
        PromptBudget.ModelLimits limits = budget.resolveLimits(4096, 32768);

        assertEquals(32768, limits.contextWindow());
        assertEquals(4096, limits.maxOutputTokens());
        assertEquals(32768 - 4096 - PromptBudget.SAFETY_MARGIN_TOKENS, limits.inputBudgetTokens());
    }

    @Test
    void shouldFallbackToDefaultContextWindowWhenNotConfigured() {
        PromptBudget.ModelLimits limits = budget.resolveLimits(2048, null);

        assertEquals(props.getSecurity().getModelPolicyDefaultContextWindow(), limits.contextWindow());
    }

    @Test
    void shouldClampOutputAboveGlobalCeilingAndFlagIt() {
        props.getSecurity().setModelPolicyMaxTokensCeiling(8192);

        PromptBudget.ModelLimits limits = budget.resolveLimits(65536, 200000);

        assertEquals(8192, limits.maxOutputTokens(), "超过全局安全上限应被夹小");
        assertTrue(limits.outputClamped(), "被夹小必须留下标记，不能再静默");
    }

    @Test
    void shouldCapInputBudgetByCostGuard() {
        props.getSecurity().setModelPolicyMaxInputTokens(1000);

        PromptBudget.ModelLimits limits = budget.resolveLimits(2048, 100000);

        assertEquals(1000, limits.inputBudgetTokens());
        assertTrue(limits.inputCappedByPolicy());
    }

    @Test
    void shouldNotCapInputBudgetWhenGuardDisabled() {
        props.getSecurity().setModelPolicyMaxInputTokens(0);

        PromptBudget.ModelLimits limits = budget.resolveLimits(2048, 32768);

        assertEquals(32768 - 2048 - PromptBudget.SAFETY_MARGIN_TOKENS, limits.inputBudgetTokens());
        assertTrue(!limits.inputCappedByPolicy());
    }

    @Test
    void shouldReportZeroInputBudgetWhenContextSmallerThanOutput() {
        PromptBudget.ModelLimits limits = budget.resolveLimits(8192, 8192);

        assertEquals(0, limits.inputBudgetTokens(), "上下文不足以容纳输出上限时输入预算为 0");
    }

    // ==================== 历史裁剪 ====================

    @Test
    void shouldDropOldestHistoryFirstAndKeepNewest() {
        List<Message> history = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            history.add(new UserMessage("第" + i + "条历史内容"));
        }

        // 每条 7 字 ≈ 7 token + 4 包装 = 11 token，给 25 token 只放得下最后 2 条
        List<Message> kept = budget.trimHistory(history, 25);

        assertEquals(2, kept.size());
        assertEquals("第4条历史内容", kept.get(0).getText());
        assertEquals("第5条历史内容", kept.get(1).getText());
    }

    @Test
    void shouldReturnEmptyWhenBudgetExhausted() {
        List<Message> history = List.of(new UserMessage("任意历史"));

        assertTrue(budget.trimHistory(history, 0).isEmpty());
        assertTrue(budget.trimHistory(history, -100).isEmpty());
        assertTrue(budget.trimHistory(List.of(), 100).isEmpty());
        assertTrue(budget.trimHistory(null, 100).isEmpty());
    }

    @Test
    void shouldKeepEverythingWhenHistoryFitsBudget() {
        List<Message> history = List.of(new UserMessage("短"), new AssistantMessage("也很短"));

        assertEquals(2, budget.trimHistory(history, 1000).size());
    }

    // ==================== 超限快速失败 ====================

    @Test
    void shouldThrowReadableErrorWhenMandatoryContentExceedsBudget() {
        AIServiceException.RequestException ex = assertThrows(AIServiceException.RequestException.class,
                () -> budget.assertMandatoryFits("zai-org/GLM-4.6", 50000, 30000, 32768, 4096));

        String message = ex.getMessage();
        assertTrue(message.contains("50000"), "提示里要给出本次实际占用: " + message);
        assertTrue(message.contains("30000"), "提示里要给出可用预算: " + message);
        assertTrue(message.contains("zai-org/GLM-4.6"), "提示里要给出模型名: " + message);
        assertTrue(message.contains("调大上下文窗口"), "提示里要给出可行动的解决办法: " + message);
    }

    @Test
    void shouldThrowWhenConfigurationItselfLeavesNoInputRoom() {
        AIServiceException.RequestException ex = assertThrows(AIServiceException.RequestException.class,
                () -> budget.assertMandatoryFits("bad-model", 10, 0, 8192, 8192));

        assertTrue(ex.getMessage().contains("配置有冲突"), ex.getMessage());
    }

    @Test
    void shouldPassWhenMandatoryContentFits() {
        budget.assertMandatoryFits("ok-model", 1000, 5000, 8192, 4096);
    }

    // ==================== 工具结果预算 ====================

    @Test
    void shouldTakeSmallerOfConfiguredLimitAndHalfOfInputBudget() {
        // 输入预算充足时，受配置上限约束
        assertEquals(12000, budget.toolResultCharBudget(96000, 12000));
        // 输入预算很小时，受预算一半约束（保护小上下文模型）
        assertEquals(2000, budget.toolResultCharBudget(4000, 12000));
    }

    @Test
    void shouldKeepToolResultBudgetAboveFloor() {
        assertEquals(500, budget.toolResultCharBudget(100, 12000));
    }

    @Test
    void shouldIgnoreNonPositiveConfiguredLimit() {
        assertEquals(5000, budget.toolResultCharBudget(10000, 0));
    }
}
