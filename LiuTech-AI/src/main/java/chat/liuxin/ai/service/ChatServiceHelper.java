package chat.liuxin.ai.service;

import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.infra.security.PromptBudget;
import chat.liuxin.ai.common.mcp.RoleBasedToolRegistry;
import org.springframework.ai.support.ToolCallbacks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import chat.liuxin.ai.infra.exception.AIServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天服务公共工具方法。
 *
 * 从 AiChatServiceImpl 和 StreamingChatService 中提取的重复逻辑。
 * 同时是**输入预算的唯一执行点**：聊天/写作 × 同步/流式四条路径都经过
 * {@link #prepareMessages}，预算裁剪与超限失败只在这里做一次，避免各处口径不一。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatServiceHelper {

    private final PromptService promptService;
    private final MemoryService memoryService;
    private final PromptBudget promptBudget;
    private final RoleBasedToolRegistry toolRegistry;

    /**
     * 组装本次调用要发送给模型的完整消息序列，并保证不超出模型的输入预算。
     *
     * 处理顺序（分级处理：能裁就裁，裁不动就明确报错）：
     * 1. 由 {@link PromptService#assembleParts} 产出「必需消息」与「可裁历史」；
     * 2. 必需消息 = 系统提示 + 站点/草稿上下文 + 当前用户输入，超预算时直接抛出可读错误；
     * 3. 历史从最旧开始整条丢弃，直到塞进剩余预算。
     *
     * @param request        原始请求
     * @param userId         用户 id（访客为 null）
     * @param conversationId 会话 id（访客/写作模式为 null）
     * @param guestMode      是否访客模式
     * @param writingMode    是否写作模式
     * @param modelName      生效模型名（用于错误文案与日志）
     * @param params         生效参数（含上下文窗口与输入预算）
     * @return 可直接发给模型的消息列表
     */
    public List<Message> prepareMessages(ChatRequest request, String userId, Long conversationId,
                                         boolean guestMode, boolean writingMode,
                                         String modelName, AiModelPolicy.ModelParameters params) {
        validateContext(request);
        PromptService.AssembledPrompt parts = promptService.assembleParts(
                request, userId, conversationId, guestMode, writingMode, memoryService);

        // 当前输入属于必需内容：即使为空也补一条空 UserMessage，防止 Spring AI 报错
        Message currentInput = new UserMessage(request.getMessage() != null ? request.getMessage() : "");

        int mandatoryTokens = promptBudget.estimateTokens(parts.mandatory())
                + promptBudget.estimateTokens(currentInput.getText()) + 4;
        int schemaTokens = 0;
        if (toolRegistry != null) {
            var tools = java.util.Arrays.asList(ToolCallbacks.from(toolRegistry.getToolsForRoleAndMode(
                    writingMode ? "ADMIN" : "GUEST", writingMode ? "WRITING" : "CHAT").toArray()));
            schemaTokens = promptBudget.estimateToolTokens(tools);
            mandatoryTokens += schemaTokens;
        }

        // 必需内容都放不下 → 立刻失败，并告诉用户超了多少、可以怎么做
        promptBudget.assertMandatoryFits(modelName, mandatoryTokens,
                params.inputBudgetTokens(), params.contextWindow(), params.maxTokens());

        int remaining = params.inputBudgetTokens() - mandatoryTokens;
        List<Message> references = new ArrayList<>();
        int toolReserve = Math.min(remaining / 2, Math.min(12000, params.inputBudgetTokens() / 4));
        remaining -= toolReserve;
        int referenceBudget = remaining / 2;
        for (Message reference : parts.references()) {
            if (referenceBudget < 128) break;
            String text = reference.getText();
            int cost = promptBudget.estimateTokens(text) + 4;
            if (cost > referenceBudget) {
                String notice = "\n[当前文章参考资料已按模型预算节选，不能声称已读取全文；需要后续内容时调用分段读取工具。]";
                text = promptBudget.truncateReference(text, referenceBudget - 4, notice);
                cost = promptBudget.estimateTokens(text) + 4;
            }
            references.add(new UserMessage(text));
            referenceBudget -= cost;
            remaining -= cost;
        }
        int historyBudget = remaining;
        List<Message> history = promptBudget.trimHistory(parts.history(), historyBudget);

        List<Message> messages = new ArrayList<>(parts.mandatory());
        messages.addAll(references);
        messages.addAll(history);
        messages.add(currentInput);

        int historyTokens = promptBudget.estimateTokens(history);
        log.info("输入预算 - 模型: {}, 上下文: {}, 输出上限: {}, 输入预算: {}, 本次实际: {} token（必需 {} + 历史 {} 条 {}）, 消息数: {}",
                modelName, params.contextWindow(), params.maxTokens(), params.inputBudgetTokens(),
                promptBudget.estimateTokens(messages) + schemaTokens, mandatoryTokens, history.size(), historyTokens, messages.size());
        return messages;
    }

    private void validateContext(ChatRequest request) {
        var context = request.getContext();
        if (context == null) return;
        var allowed = java.util.Set.of("page", "postId", "recommendations", "requestedFields", "source", "appendTags");
        if (context.size() > allowed.size() || context.keySet().stream().anyMatch(key -> !allowed.contains(key))) {
            throw new AIServiceException.RequestException("页面上下文包含不支持的字段");
        }
        if (String.valueOf(context).length() > 20000) {
            throw new AIServiceException.RequestException("页面上下文过长，请减少推荐记录后重试");
        }
        Object fields = context.get("requestedFields");
        if (fields != null && (!(fields instanceof List<?> values) || values.size() > 8
                || values.stream().anyMatch(value -> !(value instanceof String)
                || !java.util.Set.of("title", "summary", "content", "category", "tags", "tag", "check").contains(value)))) {
            throw new AIServiceException.RequestException("写作字段范围无效");
        }
    }

    /**
     * 新建会话时用首条用户消息裁出会话标题:超过 10 字截断加省略号,空输入回退为"新会话"。
     */
    public String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) return "新会话";
        String trimmed = firstMessage.trim();
        return trimmed.length() > 10 ? trimmed.substring(0, 10) + "..." : trimmed;
    }

    /**
     * 登录态下若发生异常,补一条 status=3 的错误占位 assistant 消息,便于历史列表看到失败痕迹。
     * 访客模式或无会话则跳过;记录本身失败只 warn 不再抛。
     */
    public void saveErrorIfNeeded(boolean guestMode, String userId, Long conversationId, String modelName) {
        if (!guestMode && conversationId != null) {
            try {
                memoryService.saveAssistantMessage(userId, conversationId, null, modelName, MemoryService.MESSAGE_STATUS_ERROR, null);
            } catch (Exception e) {
                log.warn("记录错误消息失败: {}", e.getMessage());
            }
        }
    }
}
