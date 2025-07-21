package chat.liuxin.liutech.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一响应结果类
 * 用于封装所有API接口的返回数据格式
 * 
 * 响应格式说明：
 * {
 *   "code": 200,           // 状态码：200成功，其他为失败
 *   "message": "操作成功",   // 响应消息
 *   "data": {...}          // 响应数据（可为null）
 * }
 * 
 * 使用示例：
 * 1. 成功响应：Result.success(data)
 * 2. 失败响应：Result.fail(ErrorCode.USER_NOT_FOUND)
 * 3. 带自定义消息的失败响应：Result.fail(ErrorCode.PARAMS_ERROR, "用户名格式错误")
 * 
 * @param <T> 响应数据的类型
 * @author liuxin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    
    /**
     * 状态码
     */
    private int code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;

    // ========== 成功响应的静态方法 ==========
    
    /**
     * 成功响应（带数据）
     * 最常用的成功响应方式
     * 
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }
    
    /**
     * 成功响应（无数据）
     * 用于只需要返回成功状态，不需要具体数据的场景
     * 
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }
    
    /**
     * 成功响应（带自定义消息和数据）
     * 用于需要自定义成功消息的场景
     * 
     * @param message 自定义成功消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    // ========== 失败响应的静态方法 ==========
    
    /**
     * 失败响应（使用ErrorCode枚举）
     * 推荐使用的失败响应方式，保证错误码和消息的一致性
     * 
     * @param errorCode 错误码枚举
     * @param <T> 数据类型
     * @return 失败的Result对象
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
    
    /**
     * 失败响应（使用ErrorCode枚举和自定义消息）
     * 当需要在标准错误信息基础上提供更详细信息时使用
     * 
     * @param errorCode 错误码枚举
     * @param customMessage 自定义错误消息
     * @param <T> 数据类型
     * @return 失败的Result对象
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String customMessage) {
        return new Result<>(errorCode.getCode(), customMessage, null);
    }
    
    /**
     * 失败响应（带错误详情数据）
     * 主要用于参数校验失败时返回具体的错误字段信息
     * 
     * @param errorCode 错误码枚举
     * @param customMessage 自定义错误消息
     * @param errorData 错误详情数据（如字段校验错误信息）
     * @param <T> 数据类型
     * @return 失败的Result对象
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String customMessage, T errorData) {
        return new Result<>(errorCode.getCode(), customMessage, errorData);
    }
    
    /**
     * 失败响应（直接指定错误码和消息）
     * 一般不推荐使用，建议优先使用ErrorCode枚举
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败的Result对象
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ========== 便捷判断方法 ==========
    
    /**
     * 判断是否成功
     * 
     * @return true表示成功，false表示失败
     */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
    
    /**
     * 判断是否失败
     * 
     * @return true表示失败，false表示成功
     */
    @JsonIgnore
    public boolean isFail() {
        return !isSuccess();
    }
}
