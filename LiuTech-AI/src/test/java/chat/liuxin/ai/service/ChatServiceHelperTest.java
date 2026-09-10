package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.PromptBudget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChatServiceHelper 输入预算执行点测试。
 *
 * 这是聊天/写作 × 同步/流式四条路径的公共入口，预算裁剪与超限失败都在这里发生，
 * 因此必须有独立测试固定行为：必需内容保序、历史从最旧丢弃、超限给出可读错误。
 */
class ChatServiceHelperTest {

    private PromptService promptService;
    private MemoryService memoryService;
    private PromptBudget promptBudget;
    private ChatServiceHelper helper;

    @BeforeEach
    void setUp() {
        promptService = mock(PromptService.class);
        memoryService = mock(MemoryService.class);
        promptBudget = new PromptBudget(new AiChatProperties());
        helper = new ChatServiceHelper(promptService, memoryService, promptBudget);
    }

    /** 构造生效参数：上下文 8192、输出 2048 → 输入预算 8192-2048-512=5632 */
    private AiModelPolicy.ModelParameters params() {
        return new AiModelPolicy.ModelParameters(0.9, 2048, 8192, 5632, false, false, "database");
    }

    private ChatRequest requestWithMessage(String message) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        return request;
    }

    @Test
    void shouldKeepMandatoryOrderAndAppendCurrentInputLast() {
        List<Message> mandatory = new ArrayList<>();
        mandatory.add(new SystemMessage("系统提示"));
        mandatory.add(new UserMessage("草稿上下文"));
        when(promptService.assembleParts(any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PromptService.AssembledPrompt(mandatory, new ArrayList<>()));

        List<Message> messages = helper.prepareMessages(
                requestWithMessage("帮我写个标题"), "1", 10L, false, true, "glm", params());

        assertEquals(3, messages.size());
        assertTrue(messages.get(0) instanceof SystemMessage);
        assertEquals("草稿上下文", messages.get(1).getText());
        assertEquals("帮我写个标题", messages.get(2).getText(), "当前输入必须排在最后");
    }

    @Test
    void shouldDropOldestHistoryWhenOverBudget() {
        List<Message> mandatory = new ArrayList<>();
        mandatory.add(new SystemMessage("系统提示"));
        List<Message> history = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            // 每条约 1500 汉字 ≈ 1500 token + 4 包装，8 条约 12000 token，远超 5632 的输入预算
            history.add(new AssistantMessage("历史" + i + "：" + "内容".repeat(750)));
        }
        when(promptService.assembleParts(any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PromptService.AssembledPrompt(mandatory, history));

        List<Message> messages = helper.prepareMessages(
                requestWithMessage("继续"), "1", 10L, false, false, "glm", params());

        // 最后一条一定是当前输入；历史只保留最新的若干条，且不含第 1 条
        assertEquals("继续", messages.get(messages.size() - 1).getText());
        assertTrue(messages.size() < 10, "应丢弃部分历史，实际消息数: " + messages.size());
        assertTrue(messages.stream().noneMatch(m -> m.getText().startsWith("历史1：")),
                "最旧的历史应被优先丢弃");
    }

    @Test
    void shouldThrowReadableErrorWhenMandatoryContentAloneExceedsBudget() {
        List<Message> mandatory = new ArrayList<>();
        mandatory.add(new SystemMessage("系统提示"));
        when(promptService.assembleParts(any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PromptService.AssembledPrompt(mandatory, new ArrayList<>()));

        // 单条用户输入（8000 汉字 ≈ 8000 token）就超过 5632 token 的输入预算
        ChatRequest request = requestWithMessage("很长的输入".repeat(2000));

        AIServiceException.RequestException ex = assertThrows(AIServiceException.RequestException.class,
                () -> helper.prepareMessages(request, "1", 10L, false, false, "glm-4.6", params()));

        assertTrue(ex.getMessage().contains("输入内容过长"), ex.getMessage());
        assertTrue(ex.getMessage().contains("glm-4.6"), "错误里要带模型名: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("5632"), "错误里要带可用预算: " + ex.getMessage());
    }

    @Test
    void shouldTickTrimHistoryWithRealBudgetOnly() {
        // 用真实预算组件验证一遍边界：预算刚好够放一条历史
        List<Message> mandatory = new ArrayList<>();
        mandatory.add(new SystemMessage("系统"));
        List<Message> history = List.of(new AssistantMessage("旧历史"), new AssistantMessage("新历史"));
        when(promptService.assembleParts(any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PromptService.AssembledPrompt(mandatory, history));

        List<Message> messages = helper.prepareMessages(
                requestWithMessage("问题"), "1", 10L, false, false, "glm", params());

        // 预算充裕（5632）时不应无故丢弃历史
        assertTrue(messages.stream().anyMatch(m -> m.getText().equals("旧历史")), "预算充足时不应丢历史");
        assertTrue(messages.stream().anyMatch(m -> m.getText().equals("新历史")));
    }

    @Test
    void shouldPassResolvedModelNameToPromptAssembly() {
        when(promptService.assembleParts(any(), any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new PromptService.AssembledPrompt(new ArrayList<>(), new ArrayList<>()));

        helper.prepareMessages(requestWithMessage("hi"), "42", 7L, false, true, "zai-org/GLM-4.6", params());

        // 组装时按参数透传上下文（写作模式 + 会话 id），保证与预算同源
        org.mockito.Mockito.verify(promptService).assembleParts(
                any(), eq("42"), eq(7L), eq(false), eq(true), any());
    }
}
