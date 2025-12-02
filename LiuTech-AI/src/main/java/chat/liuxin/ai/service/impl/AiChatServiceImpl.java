package chat.liuxin.ai.service.impl;

/**
 * AI聊天服务实现
 * 作者：刘鑫
 * 时间：2025-12-01
 * 重构说明：适配新的数据库表结构，简化设计，优化性能
 * 流式过程说明：
 * - 初始化 SseEmitter 并发送事件：user -> start -> data -> complete|error。
 * - 入库策略：仅在 complete 或 error 时落库 assistant，避免保存半截文本；用户输入在生成前落库。
 * - 会话维护：若无 conversationId 则创建新会话。
 */

import chat.liuxin.ai.entity.AiChatMessage;
import chat.liuxin.ai.exception.AIServiceException;
import chat.liuxin.ai.req.ChatRequest;
import chat.liuxin.ai.resp.ChatResponse;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.MemoryService;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final RetryTemplate retryTemplate;
    private static final int HISTORY_LIMIT = 19; // 历史条数（不含本轮输入）

    /**
     * 1) 普通聊天：一次性返回完整AI回复
     */
    @Override
    public ChatResponse processChat(ChatRequest request, Long userId) {
        // 记录开始时间,用于计算处理耗时
        long begin = System.currentTimeMillis();
        String userIdStr = userId != null ? userId.toString() : "0";
        String modelName = request.getModel() != null ? request.getModel() : "THUDM/glm-4-9b-chat";
        Long conversationId = request.getConversationId();// 会话ID，若为空则创建新会话
        try {
            // 提取用户输入
            String input = request.getMessage();

            // 读取当前会话的最近历史（最多19条），用于作为上下文
            List<Message> historyMessages = conversationId != null ?
                    memoryService.listLastMessagesAsPromptMessages(conversationId, HISTORY_LIMIT) :
                    Collections.emptyList();

            // 构造消息序列：系统提示词 -> 历史 -> 上下文信息 -> 本轮用户输入
            List<Message> messages = new ArrayList<>();

            // 1.系统提示词由 ChatClient 的 defaultSystem 提供，这里不再重复注入
            // 2.历史信息列表
            messages.addAll(historyMessages);

            // 4.将用户当前输入的消息添加到消息列表末尾，作为最新一条用户消息
            messages.add(new UserMessage(input));

            //如果没有会话id就新建会话id，并返回前端
            if (conversationId == null) {
                String title = "新会话";
                conversationId = memoryService.createConversation(userIdStr, title);
            }

            // 异步保存用户消息
            saveUserMessageAsync(userIdStr, conversationId, input, modelName);

            // 使用重试模板调用模型（一次性返回完整答案）
            String aiOutput = retryTemplate.execute(retryContext -> {
                log.debug("调用AI模型，第{}次尝试", retryContext.getRetryCount() + 1);
                return siliconFlowChatClient.chat(messages, modelName);
            });
            System.out.println("AI回复：\n" + (aiOutput == null ? "" : aiOutput) + '\n');
            // 异步落库"AI消息"（status=1）
            // 重构说明：新表结构中移除了metadata字段，简化存储
            saveAssistantMessageAsync(userIdStr, conversationId, aiOutput, modelName, 1);


             long cost = System.currentTimeMillis() - begin;
             log.debug("AI普通聊天成功，模型:{}，输入长度:{}，输出长度:{}，耗时:{}ms", modelName, input.length(), aiOutput != null ? aiOutput.length() : 0, cost);

             return ChatResponse.builder()
                     .success(true)
                     .message(aiOutput)
                     .emotion(null)
                     .action(null)
                     .model(modelName)
                     .processingTime(cost)
                     .responseLength(aiOutput != null ? aiOutput.length() : 0)
                     .conversationId(conversationId)
                     .build();

        } catch (AIServiceException e) {
            // AI服务特定异常，直接抛出让全局异常处理器处理
            log.error("AI服务异常: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("AI普通聊天失败", e);
            try {
                saveAssistantMessageAsync(userIdStr, conversationId, null, modelName, 9);
            } catch (Exception ignore) {
                log.warn("记录AI错误消息失败: {}", ignore.getMessage());
            }

            // 根据异常类型抛出相应的AI服务异常
            if (e.getCause() instanceof java.net.ConnectException ||
                e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new AIServiceException.ConnectionException("AI服务连接失败: " + e.getMessage());
            } else if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new AIServiceException.TimeoutException("AI服务响应超时: " + e.getMessage());
            } else {
                throw new AIServiceException("AI服务处理异常: " + e.getMessage());
            }
        }
    }

    /**
     * 异步保存用户消息
     */
    @Async("optimizedAiTaskExecutor")
    public void saveUserMessageAsync(String userId, Long conversationId, String content, String model) {
        try {
            memoryService.saveUserMessage(userId, conversationId, content, model, null);
            log.debug("异步保存用户消息成功，会话ID:{}", conversationId);
        } catch (Exception e) {
            log.error("异步保存用户消息失败，会话ID:{}", conversationId, e);
        }
    }

    /**
     * 异步保存AI助手消息
     */
    @Async("optimizedAiTaskExecutor")
    public void saveAssistantMessageAsync(String userId, Long conversationId, String content, String model, int status) {
        try {
            memoryService.saveAssistantMessage(userId, conversationId, content, model, status, null);
            log.debug("异步保存AI消息成功，会话ID:{}, 状态:{}", conversationId, status);
        } catch (Exception e) {
            log.error("异步保存AI消息失败，会话ID:{}, 状态:{}", conversationId, status, e);
        }
    }
}
