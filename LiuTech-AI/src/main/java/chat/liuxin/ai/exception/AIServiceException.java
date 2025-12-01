package chat.liuxin.ai.exception;

/**
 * AI服务异常类 
 * 
 * 作者：刘鑫
 * 时间：2025-12-01
 */
public class AIServiceException extends RuntimeException {
    
    public AIServiceException(String message) {
        super(message);
    }
    
    public AIServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    // 连接异常 - AI服务连不上
    public static class ConnectionException extends AIServiceException {
        public ConnectionException(String message) {
            super(message);
        }
    }
    
    // 超时异常 - AI响应太慢
    public static class TimeoutException extends AIServiceException {
        public TimeoutException(String message) {
            super(message);
        }
    }
    
    // 模型异常 - AI模型出问题
    public static class ModelException extends AIServiceException {
        public ModelException(String message) {
            super(message);
        }
    }
    
    // 请求异常 - 用户输入有问题
    public static class RequestException extends AIServiceException {
        public RequestException(String message) {
            super(message);
        }
    }
}