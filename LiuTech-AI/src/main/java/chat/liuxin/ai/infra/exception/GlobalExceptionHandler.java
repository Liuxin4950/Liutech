package chat.liuxin.ai.infra.exception;

import chat.liuxin.ai.common.utils.WebUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器：把各类异常统一映射成前端能识别的 JSON 响应。
 *
 * 映射规则：
 * - {@link AIServiceException} 的四个子类分别映射 503/408/503/400
 * - {@link ResponseStatusException} 保留原状态码（业务代码用它主动抛权限/存在性错误）
 * - 其他 {@link Exception} 兜底为 500，避免堆栈泄漏到前端
 *
 * 所有响应体格式统一为 {success:false, message, code}，前端按 code 分支处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** AI 服务网络连不上（DNS/连接拒绝/网络中断），映射 503 让前端提示"检查网络" */
    @ExceptionHandler(AIServiceException.ConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionException(Exception ex) {
        log.error("AI连接失败: {}", ex.getMessage());
        return createErrorResponse("AI服务连接失败，请检查网络", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** AI 响应超时（读超时/流式无响应），映射 408 让前端提示重试 */
    @ExceptionHandler(AIServiceException.TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeoutException(Exception ex) {
        log.error("AI响应超时: {}", ex.getMessage());
        return createErrorResponse("AI响应超时，请重试", HttpStatus.REQUEST_TIMEOUT);
    }

    /** AI 模型异常（返回空/格式错/上游模型不可用），映射 503 */
    @ExceptionHandler(AIServiceException.ModelException.class)
    public ResponseEntity<Map<String, Object>> handleModelException(Exception ex) {
        log.error("AI模型错误: {}", ex.getMessage());
        return createErrorResponse("AI模型暂时不可用", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** 用户请求参数不合法（触发上游 4xx），映射 400 */
    @ExceptionHandler(AIServiceException.RequestException.class)
    public ResponseEntity<Map<String, Object>> handleRequestException(Exception ex) {
        log.warn("请求参数错误: {}", ex.getMessage());
        return createErrorResponse("输入内容有误，请检查", HttpStatus.BAD_REQUEST);
    }

    /** 未细分类的 AIServiceException 兜底 500 */
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAIServiceException(Exception ex) {
        log.error("AI服务异常: {}", ex.getMessage());
        return createErrorResponse("AI服务异常，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 业务代码主动抛出的状态码异常（如 MemoryService 里的 404/403），保留原状态码和 reason */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return createErrorResponse(ex.getReason() != null ? ex.getReason() : "请求失败", status);
    }

    /** 兜底：任何未处理的异常都归为 500，堆栈只写日志不返回前端，避免泄漏内部实现 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        // SSE 请求异常由 StreamingChatService.onError 处理（发 error 事件），这里跳过避免破坏 event-stream 格式
        if (response.isCommitted() || WebUtils.isSseRequest(request)) {
            log.warn("SSE请求异常，已跳过JSON写入: {}", ex.getMessage());
            return null;
        }
        log.error("系统异常: {}", ex.getMessage(), ex);
        return createErrorResponse("系统错误，请联系管理员", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 统一构造错误响应体格式：{success:false, message, code} */
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("code", status.value());
        return new ResponseEntity<>(response, status);
    }
}
