package chat.liuxin.ai.infra.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/** 全局异常处理器 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AIServiceException.ConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionException(Exception ex) {
        log.error("AI连接失败: {}", ex.getMessage());
        return createErrorResponse("AI服务连接失败，请检查网络", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(AIServiceException.TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeoutException(Exception ex) {
        log.error("AI响应超时: {}", ex.getMessage());
        return createErrorResponse("AI响应超时，请重试", HttpStatus.REQUEST_TIMEOUT);
    }

    @ExceptionHandler(AIServiceException.ModelException.class)
    public ResponseEntity<Map<String, Object>> handleModelException(Exception ex) {
        log.error("AI模型错误: {}", ex.getMessage());
        return createErrorResponse("AI模型暂时不可用", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(AIServiceException.RequestException.class)
    public ResponseEntity<Map<String, Object>> handleRequestException(Exception ex) {
        log.warn("请求参数错误: {}", ex.getMessage());
        return createErrorResponse("输入内容有误，请检查", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAIServiceException(Exception ex) {
        log.error("AI服务异常: {}", ex.getMessage());
        return createErrorResponse("AI服务异常，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return createErrorResponse(ex.getReason() != null ? ex.getReason() : "请求失败", status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("系统异常: {}", ex.getMessage(), ex);
        return createErrorResponse("系统错误，请联系管理员", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("code", status.value());
        return new ResponseEntity<>(response, status);
    }
}
