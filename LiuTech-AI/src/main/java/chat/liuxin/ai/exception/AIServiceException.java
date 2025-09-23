package chat.liuxin.ai.exception;

/**
 * AI服务异常类
 * 
 * 功能：
 * 1. 提供AI服务相关的异常分类
 * 2. 支持错误码和详细错误信息
 * 3. 便于统一异常处理和错误追踪
 * 
 * 作者：刘鑫
 * 时间：2025-09-24
 */
public class AIServiceException extends RuntimeException {
    
    private final String errorCode;
    private final String errorType;
    
    public AIServiceException(String message) {
        super(message);
        this.errorCode = "AI_ERROR";
        this.errorType = "GENERAL";
    }
    
    public AIServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AI_ERROR";
        this.errorType = "GENERAL";
    }
    
    public AIServiceException(String errorCode, String errorType, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = errorType;
    }
    
    public AIServiceException(String errorCode, String errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorType = errorType;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    /**
     * AI连接异常
     */
    public static class ConnectionException extends AIServiceException {
        public ConnectionException(String message) {
            super("AI_CONNECTION_ERROR", "CONNECTION", message);
        }
        
        public ConnectionException(String message, Throwable cause) {
            super("AI_CONNECTION_ERROR", "CONNECTION", message, cause);
        }
    }
    
    /**
     * AI响应超时异常
     */
    public static class TimeoutException extends AIServiceException {
        public TimeoutException(String message) {
            super("AI_TIMEOUT_ERROR", "TIMEOUT", message);
        }
        
        public TimeoutException(String message, Throwable cause) {
            super("AI_TIMEOUT_ERROR", "TIMEOUT", message, cause);
        }
    }
    
    /**
     * AI模型不可用异常
     */
    public static class ModelUnavailableException extends AIServiceException {
        public ModelUnavailableException(String message) {
            super("AI_MODEL_UNAVAILABLE", "MODEL", message);
        }
        
        public ModelUnavailableException(String message, Throwable cause) {
            super("AI_MODEL_UNAVAILABLE", "MODEL", message, cause);
        }
    }
    
    /**
     * AI请求格式错误异常
     */
    public static class InvalidRequestException extends AIServiceException {
        public InvalidRequestException(String message) {
            super("AI_INVALID_REQUEST", "REQUEST", message);
        }
        
        public InvalidRequestException(String message, Throwable cause) {
            super("AI_INVALID_REQUEST", "REQUEST", message, cause);
        }
    }
    
    /**
     * AI服务限流异常
     */
    public static class RateLimitException extends AIServiceException {
        public RateLimitException(String message) {
            super("AI_RATE_LIMIT", "RATE_LIMIT", message);
        }
        
        public RateLimitException(String message, Throwable cause) {
            super("AI_RATE_LIMIT", "RATE_LIMIT", message, cause);
        }
    }
}