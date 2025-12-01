package chat.liuxin.ai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器 - 简化版
 * 
 * 作者：刘鑫
 * 时间：2025-12-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    // 处理连接异常 - AI服务连不上
    @ExceptionHandler(AIServiceException.ConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionException(Exception ex) {
        log.error("AI连接失败: {}", ex.getMessage());
        return createErrorResponse("AI服务连接失败，请检查网络", HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    // 处理超时异常 - AI响应太慢
    @ExceptionHandler(AIServiceException.TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeoutException(Exception ex) {
        log.error("AI响应超时: {}", ex.getMessage());
        return createErrorResponse("AI响应超时，请重试", HttpStatus.REQUEST_TIMEOUT);
    }
    
    // 处理模型异常 - AI模型出问题
    @ExceptionHandler(AIServiceException.ModelException.class)
    public ResponseEntity<Map<String, Object>> handleModelException(Exception ex) {
        log.error("AI模型错误: {}", ex.getMessage());
        return createErrorResponse("AI模型暂时不可用", HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    // 处理请求异常 - 用户输入有问题
    @ExceptionHandler(AIServiceException.RequestException.class)
    public ResponseEntity<Map<String, Object>> handleRequestException(Exception ex) {
        log.warn("请求参数错误: {}", ex.getMessage());
        return createErrorResponse("输入内容有误，请检查", HttpStatus.BAD_REQUEST);
    }
    
    // 处理其他AI异常
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAIServiceException(Exception ex) {
        log.error("AI服务异常: {}", ex.getMessage());
        return createErrorResponse("AI服务异常，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    // 处理其他系统异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("系统异常: {}", ex.getMessage(), ex);
        return createErrorResponse("系统错误，请联系管理员", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    // 创建简单的错误响应
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("code", status.value());
        
        return new ResponseEntity<>(response, status);
    }
}