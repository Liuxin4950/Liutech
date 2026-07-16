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
 * 同步方法（processChat / processWriting）直接在此实现；
 * 流式方法委托给 {@link StreamingChatService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {
    /** 写作模式专用参数：低温度保证稳定输出，高 maxTokens 避免正文被截断 */
    private static final double WRITING_TEMPERATURE = 0.3;
    private static final int WRITING_MAX_TOKENS = 8192; // 兜底值，管理端配置更高时优先用配置


    private final SiliconFlowChatClient siliconFlowChatClient;
    private final MemoryService memoryService;
    private final AiMetrics aiMetrics;
    private final ChatServiceHelper chatServiceHelper;
    private final AiModelPolicy aiModelPolicy;
    private final StreamingChatService streamingChatService;

    // ==================== 同步接口 ====================

    /**
     * 看板娘同步聊天完整流程。
     *
     * 组装消息(系统提示 + 博客上下文 + 历史)、若登录则先落库用户消息并按需新建会话,
     * 再调用 {@link SiliconFlowChatClient#chat} 拿到完整回复并落库 assistant 消息。
     * 全程通过 {@link AiMetrics} 记录成功/失败埋点;异常统一走 {@link #classifyException} 转成
     * {@link AIServiceException} 子类,同时落一条 status=3 的错误占位消息。
     */
    @Override
    public ChatResponse processChat(ChatRequest request, Long userId, String role) {
        long begin = System.currentTimeMillis();
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = resolveModelName(request);
        Long conversationId = guestMode ? null : request.getConversationId();

        try {
            List<Message> messages = chatServiceHelper.prepareMessages(request, userIdStr, conversationId, guestMode, false);
            String input = request.getMessage();

            if (!guestMode && conversationId == null) {
                conversationId = memoryService.createConversation(userIdStr, chatServiceHelper.generateTitle(input));
            }
            if (!guestMode) {
                memoryService.saveUserMessage(userIdStr, conversationId, input, modelName, null);
            }

            AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
            logParameterApplication(modelName, params);
            String aiOutput = siliconFlowChatClient.chat(messages, modelName, params.temperature(), params.maxTokens(), role);

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

    /**
     * 写作助手同步调用。
     *
     * 走 WRITING 模式,注册 WritingTools;不落库消息(草稿由前端管理)。
     * 底层客户端会以流式收集方式规避长响应下的 RestClient 超时。
     * 失败时不抛异常,直接返回 success=false 的响应,让前端展示错误文案。
     */
    @Override
    public ChatResponse processWriting(ChatRequest request, Long userId, String role) {
        long begin = System.currentTimeMillis();
        boolean guestMode = userId == null;
        String userIdStr = userId != null ? userId.toString() : null;
        String modelName = resolveModelName(request);
        Long conversationId = guestMode ? null : request.getConversationId();

        try {
            List<Message> messages = chatServiceHelper.prepareMessages(request, userIdStr, conversationId, guestMode, true);
            AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
            logParameterApplication(modelName, params);
            String aiOutput = siliconFlowChatClient.chat(messages, modelName, params.temperature(), params.maxTokens(), SiliconFlowChatClient.ChatMode.WRITING, role);

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

    /** 看板娘流式:解析模型/参数后委托 {@link StreamingChatService} 处理 SSE 生命周期。 */
    @Override
    public SseEmitter processStreamChat(ChatRequest request, Long userId, String role) {
        String modelName = resolveModelName(request);
        AiModelPolicy.ModelParameters params = getModelParameters(request, modelName);
        logParameterApplication(modelName, params);
        return streamingChatService.processStreamChat(request, userId, modelName, params, role);
    }

    /** 写作助手流式:同样委托,但走 WRITING 分支,不持久化消息。 */
    @Override
    public SseEmitter processWritingStream(ChatRequest request, Long userId, String role) {
        String modelName = resolveModelName(request);
        AiModelPolicy.ModelParameters params = writingParameters(getModelParameters(request, modelName));
        logParameterApplication(modelName, params);
        return streamingChatService.processWritingStream(request, userId, modelName, params, role);
    }

    // ==================== 内部工具 ====================

    /** 组装成功响应,访客模式屏蔽 conversationId 并把 mode 标记为 guest。 */
    private ChatResponse buildSuccessResponse(String aiOutput, String modelName, long cost, Long conversationId, boolean guestMode) {
        return ChatResponse.builder()
                .success(true).message(aiOutput).model(modelName)
                .processingTime(cost).responseLength(aiOutput != null ? aiOutput.length() : 0)
                .conversationId(guestMode ? null : conversationId)
                .mode(guestMode ? "guest" : "user").build();
    }

    /** 按请求 + 策略解析出实际使用的模型名(考虑默认模型、白名单等)。 */
    private String resolveModelName(ChatRequest request) {
        return aiModelPolicy.resolveModelName(request);
    }

    /**
     * 写作模式参数处理：尊重管理端配置的模型参数，仅做最低保障兜底。
     * - temperature: 用配置值，未配置时用0.3兜底（减少废话和空调用）
     * - maxTokens: 用配置值，配置低于8192时自动提升到8192兜底（避免长正文被截断）
     * 用户配置更高maxTokens（如16384/32768）时完全按配置生效，充分发挥模型能力。
     */
    private AiModelPolicy.ModelParameters writingParameters(AiModelPolicy.ModelParameters base) {
        Double temperature = base.temperature() != null ? base.temperature() : WRITING_TEMPERATURE;
        Integer maxTokens = base.maxTokens() != null ? Math.max(base.maxTokens(), 8192) : WRITING_MAX_TOKENS;
        return new AiModelPolicy.ModelParameters(temperature, maxTokens, base.source() + "+writing-fallback");
    }
    /** 解析 temperature / maxTokens,来源可能是请求参数、模型默认值或全局默认。 */
    private AiModelPolicy.ModelParameters getModelParameters(ChatRequest request, String modelName) {
        return aiModelPolicy.resolveParameters(request, modelName);
    }

    /** 记录本次实际生效的模型参数,便于排查前端传参和策略生效情况。 */
    private void logParameterApplication(String modelName, AiModelPolicy.ModelParameters params) {
        if (params.temperature() != null || params.maxTokens() != null) {
            log.debug("AI模型参数 - 模型: {}, 来源: {}, temperature: {}, maxTokens: {}",
                    modelName, params.source(),
                    params.temperature() != null ? String.format("%.2f", params.temperature()) : "未设置",
                    params.maxTokens() != null ? params.maxTokens() : "未设置");
        } else {
            log.debug("AI模型参数 - 模型: {}, 来源: {}, 使用默认参数", modelName, params.source());
        }
    }

    /** 粗略按字符数/4 估算 token 数,仅用于监控埋点,非计费用。 */
    private int estimateTokens(String text) {
        return text != null ? text.length() / 4 : 0;
    }

    /**
     * 把底层异常映射为 {@link AIServiceException} 的语义子类,便于上层统一处理和前端提示。
     *
     * 分类规则:
     * - 连接类(ConnectException / SocketTimeoutException / UnknownHostException / ResourceAccessException) → ConnectionException
     * - HTTP 状态错误 → RequestException
     * - JSON/文本解析失败 → ModelException
     * - 超时或消息含 "timeout" → TimeoutException
     * - 其余兜底为通用 AIServiceException
     */
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
        if (root instanceof tools.jackson.core.exc.StreamReadException || root instanceof java.text.ParseException) {
            return new AIServiceException.ModelException("AI服务响应解析失败: " + root.getMessage());
        }
        if (root instanceof TimeoutException || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
            return new AIServiceException.TimeoutException("AI服务响应超时: " + root.getMessage());
        }
        return new AIServiceException("AI服务处理异常: " + (root.getMessage() != null ? root.getMessage() : "未知错误"));
    }
}


