package chat.liuxin.liutech.common;

import lombok.Getter;

/**
 * 业务异常类
 * 用于处理业务逻辑中的异常情况
 * 
 * 使用场景：
 * 1. 用户输入不合法（用户名已存在、密码格式错误等）
 * 2. 业务规则违反（权限不足、状态不允许等）
 * 3. 数据不存在（用户不存在、文章不存在等）
 * 
 * 注意：BusinessException会被GlobalExceptionHandler捕获并统一处理
 * 
 * @author liuxin
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    /**
     * 使用ErrorCode枚举构造异常（推荐使用）
     * 这种方式可以保证错误码和错误信息的一致性
     * 
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 使用ErrorCode枚举和自定义消息构造异常
     * 当需要在标准错误信息基础上添加更多详细信息时使用
     * 
     * @param errorCode 错误码枚举
     * @param customMessage 自定义错误信息
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        // 如果提供了自定义消息，使用自定义消息；否则使用枚举中的默认消息
        super(customMessage != null && !customMessage.trim().isEmpty() ? customMessage : errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 直接使用错误码和消息构造异常
     * 一般不推荐使用，建议优先使用ErrorCode枚举
     * 
     * @param code 错误码
     * @param message 错误信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用ErrorCode枚举和原始异常构造异常
     * 当需要包装其他异常时使用
     * 
     * @param errorCode 错误码枚举
     * @param cause 原始异常
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }

    /**
     * 使用ErrorCode枚举、自定义消息和原始异常构造异常
     * 最完整的构造方式
     * 
     * @param errorCode 错误码枚举
     * @param customMessage 自定义错误信息
     * @param cause 原始异常
     */
    public BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage != null && !customMessage.trim().isEmpty() ? customMessage : errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }

}
