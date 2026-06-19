package chat.liuxin.ai.service.impl;

import chat.liuxin.ai.common.monitor.AiMetrics;
import chat.liuxin.ai.dto.ChatRequest;
import chat.liuxin.ai.dto.ChatResponse;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.infra.security.AiModelPolicy;
import chat.liuxin.ai.service.AiChatService;
import chat.liuxin.ai.service.ChatServiceHelper;
import chat.liuxin.ai.service.MemoryService;
import chat.liuxin.ai.service.SiliconFlowChatClient;
import chat.liuxin.ai.service.StreamingChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * AI 聊天服务实现（同步 + 流式委托）。
 *
 * <p>同步方法（processChat / processWriting）直接在此实现；
 * 流式方法委托给 {@link StreamingChatService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final AiMetrics aiMetrics;
    private final ChatServiceHelper chatServiceHelper;
    private final AiModelPolicy aiModelPolicy;
    private final StreamingChatService streamingChatService;

    // ==================== 同步接口 ====================

    @Override
    public ChatResponse processChat(ChatRequest request, Long userId) {
        long begin = System.currentTimeMillis();
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = resolveModelName(request);
        Long conversationId = guestMode ? null : request.getConversationId();

        try {
            List<Message> messages = chatServiceHelper.prepareMessages(request, userIdStr, conversationId, guestMode);
            String input = request.getMessage();

            if (!guestMode && conversationId == null) {
                conversationId = memoryService.createConversation(userIdStr, chatServiceHelper.generateTitle(input));
            }
            if (!guestMode) {
                memoryService.saveUserMessage(userIdStr, conversationId, input, modelName, null);
            }

            AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
            logParameterApplication(modelName, params);
            String aiOutput = siliconFlowChatClient.chat(messages, modelName, params.temperature(), params.maxTokens());

            if (!guestMode) {
                memoryService.saveAssistantMessage(userIdStr, conversationId, aiOutput, modelName, 1, null);
            }

            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordSuccess(modelName, cost, estimateTokens(aiOutput));
            return buildSuccessResponse(aiOutput, modelName, cost, conversationId, guestMode);

        } catch (AIServiceException e) {
            aiMetrics.recordFailure(modelName, System.currentTimeMillis() - begin, e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordFailure(modelName, cost, e.getClass().getSimpleName());
            log.error("AI普通聊天失败", e);
            chatServiceHelper.saveErrorIfNeeded(guestMode, userIdStr, conversationId, modelName);
            throw classifyException(e);
        }
    }

    @Override
    public ChatResponse processWriting(ChatRequest request, Long userId) {
        long begin = System.currentTimeMillis();
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = resolveModelName(request);
        Long conversationId = guestMode ? null : request.getConversationId();

        try {
            List<Message> messages = chatServiceHelper.prepareMessages(request, userIdStr, conversationId, guestMode);
            AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
            logParameterApplication(modelName, params);
            String aiOutput = siliconFlowChatClient.chat(messages, modelName, params.temperature(), params.maxTokens(), SiliconFlowChatClient.ChatMode.WRITING);

            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordSuccess(modelName, cost, estimateTokens(aiOutput));
            return buildSuccessResponse(aiOutput, modelName, cost, conversationId, guestMode);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - begin;
            aiMetrics.recordFailure(modelName, cost, e.getClass().getSimpleName());
            log.error("AI写作助手失败", e);
            return ChatResponse.builder().success(false)
                    .message("写作助手处理失败: " + e.getMessage())
                    .model(modelName).processingTime(cost).build();
        }
    }

    // ==================== 流式接口（委托） ====================

    @Override
    public SseEmitter processStreamChat(ChatRequest request, Long userId) {
        String modelName = resolveModelName(request);
        AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
        logParameterApplication(modelName, params);
        return streamingChatService.processStreamChat(request, userId, modelName, params);
    }

    @Override
    public SseEmitter processWritingStream(ChatRequest request, Long userId) {
        String modelName = resolveModelName(request);
        AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
        logParameterApplication(modelName, params);
        return streamingChatService.processWritingStream(request, userId, modelName, params);
    }

    // ==================== 内部工具 ====================

    private ChatResponse buildSuccessResponse(String aiOutput, String modelName, long cost, Long conversationId, boolean guestMode) {
        return ChatResponse.builder()
                .success(true).message(aiOutput).model(modelName)
                .processingTime(cost).responseLength(aiOutput != null ? aiOutput.length() : 0)
                .conversationId(guestMode ? null : conversationId)
                .mode(guestMode ? "guest" : "user").build();
    }

    private String resolveModelName(ChatRequest request) {
        return aiModelPolicy.resolveModelName(request);
    }

    private AiModelPolicy.ModelParameters getModelParameters(ChatRequest request, String modelName) {
        return aiModelPolicy.resolveParameters(request, modelName);
    }

    private void logParameterApplication(String modelName, AiModelPolicy.ModelParameters params) {
        if (params.temperature() != null || params.maxTokens() != null) {
            log.info("AI模型参数 - 模型: {}, 来源: {}, temperature: {}, maxTokens: {}",
                    modelName, params.source(),
                    params.temperature() != null ? String.format("%.2f", params.temperature()) : "未设置",
                    params.maxTokens() != null ? params.maxTokens() : "未设置");
        } else {
            log.info("AI模型参数 - 模型: {}, 来源: {}, 使用默认参数", modelName, params.source());
        }
    }

    private int estimateTokens(String text) {
        return text != null ? text.length() / 4 : 0;
    }

    private AIServiceException classifyException(Exception e) {
        Throwable root = e.getCause() != null ? e.getCause() : e;
        if (root instanceof java.net.ConnectException || root instanceof java.net.SocketTimeoutException
                || root instanceof java.net.UnknownHostException) {
            return new AIServiceException.ConnectionException("AI服务连接失败: " + root.getMessage());
        }
        if (root instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            return new AIServiceException.RequestException("AI服务HTTP错误 " + httpEx.getStatusCode() + ": " + httpEx.getMessage());
        }
        if (root instanceof org.springframework.web.client.ResourceAccessException) {
            return new AIServiceException.ConnectionException("AI服务网络访问失败: " + root.getMessage());
        }
        if (root instanceof com.fasterxml.jackson.core.JsonParseException || root instanceof java.text.ParseException) {
            return new AIServiceException.ModelException("AI服务响应解析失败: " + root.getMessage());
        }
        if (root instanceof TimeoutException || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
            return new AIServiceException.TimeoutException("AI服务响应超时: " + root.getMessage());
        }
        return new AIServiceException("AI服务处理异常: " + (root.getMessage() != null ? root.getMessage() : "未知错误"));
    }
}
