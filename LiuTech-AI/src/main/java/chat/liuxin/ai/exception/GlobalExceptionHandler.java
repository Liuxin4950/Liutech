package chat.liuxin.ai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 
 * 功能：
 * 1. 统一处理AI服务相关异常
 * 2. 返回标准化的错误响应格式
 * 3. 记录详细的错误日志便于问题排查
 * 4. 避免敏感信息泄露给客户端
 * 
 * 作者：刘鑫
 * 时间：2025-09-24
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理AI服务连接异常
     */
    @ExceptionHandler(AIServiceException.ConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionException(
            AIServiceException.ConnectionException ex, WebRequest request) {
        log.error("AI服务连接异常: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            ex.getErrorCode(),
            ex.getErrorType(),
            "AI服务暂时不可用，请稍后重试",
            HttpStatus.SERVICE_UNAVAILABLE
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    /**
     * 处理AI服务超时异常
     */
    @ExceptionHandler(AIServiceException.TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeoutException(
            AIServiceException.TimeoutException ex, WebRequest request) {
        log.error("AI服务响应超时: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            ex.getErrorCode(),
            ex.getErrorType(),
            "AI服务响应超时，请重新发送消息",
            HttpStatus.REQUEST_TIMEOUT
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.REQUEST_TIMEOUT);
    }
    
    /**
     * 处理AI模型不可用异常
     */
    @ExceptionHandler(AIServiceException.ModelUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleModelUnavailableException(
            AIServiceException.ModelUnavailableException ex, WebRequest request) {
        log.error("AI模型不可用: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            ex.getErrorCode(),
            ex.getErrorType(),
            "AI模型暂时不可用，请稍后重试",
            HttpStatus.SERVICE_UNAVAILABLE
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    /**
     * 处理AI请求格式错误异常
     */
    @ExceptionHandler(AIServiceException.InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequestException(
            AIServiceException.InvalidRequestException ex, WebRequest request) {
        log.warn("AI请求格式错误: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = createErrorResponse(
            ex.getErrorCode(),
            ex.getErrorType(),
            "请求格式不正确，请检查输入内容",
            HttpStatus.BAD_REQUEST
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理AI服务限流异常
     */
    @ExceptionHandler(AIServiceException.RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(
            AIServiceException.RateLimitException ex, WebRequest request) {
        log.warn("AI服务请求过于频繁: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = createErrorResponse(
            ex.getErrorCode(),
            ex.getErrorType(),
            "请求过于频繁，请稍后再试",
            HttpStatus.TOO_MANY_REQUESTS
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
    }
    
    /**
     * 处理通用AI服务异常
     */
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAIServiceException(
            AIServiceException ex, WebRequest request) {
        log.error("AI服务异常: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            ex.getErrorCode(),
            ex.getErrorType(),
            "AI服务处理异常，请稍后重试",
            HttpStatus.INTERNAL_SERVER_ERROR
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        log.error("系统异常: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorResponse = createErrorResponse(
            "SYSTEM_ERROR",
            "SYSTEM",
            "系统内部错误，请稍后重试",
            HttpStatus.INTERNAL_SERVER_ERROR
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 创建标准化的错误响应
     */
    private Map<String, Object> createErrorResponse(String errorCode, String errorType, 
                                                   String message, HttpStatus status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("errorCode", errorCode);
        errorResponse.put("errorType", errorType);
        errorResponse.put("message", message);
        errorResponse.put("status", status.value());
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return errorResponse;
    }
}