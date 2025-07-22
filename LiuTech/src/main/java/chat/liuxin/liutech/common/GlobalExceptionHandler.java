package chat.liuxin.liutech.common;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器
 * 统一处理系统中的各种异常，保证API响应格式的一致性
 * 异常处理优先级（从高到低）：
 * 1. BusinessException - 业务逻辑异常
 * 2. MethodArgumentNotValidException - JSON请求参数校验异常
 * 3. ConstraintViolationException - URL参数校验异常
 * 4. HttpMessageNotReadableException - JSON格式错误
 * 5. MethodArgumentTypeMismatchException - 参数类型转换异常
 * 6. HttpRequestMethodNotSupportedException - HTTP方法不支持
 * 7. NoHandlerFoundException - 404异常
 * 8. DataAccessException - 数据库异常
 * 9. Exception - 其他未知异常
 *
 * @author liuxin
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ========== 业务异常处理 ==========

    /**
     * 处理业务逻辑异常
     * 这是最常见的异常类型，用于处理业务规则违反的情况
     *
     * @param e 业务异常
     * @return 统一的错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        // 业务异常通常不需要打印堆栈信息，只记录警告日志
        log.warn("业务异常: [{}] {}", e.getCode(), e.getMessage());

        // 直接使用异常中的错误码和消息构造响应
        Result<Void> result = new Result<>(e.getCode(), e.getMessage(), null);

        // 业务异常通常返回400状态码
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ========== 参数校验异常处理 ==========

    /**
     * 处理JSON请求体参数校验异常
     * 当使用@Valid注解校验@RequestBody参数时触发
     *
     * @param e 方法参数校验异常
     * @return 包含详细校验错误信息的响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 提取所有字段的校验错误信息
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        // 如果同一个字段有多个校验错误，用逗号分隔
                        (existing, replacement) -> existing + ", " + replacement
                ));

        log.warn("JSON参数校验失败: {}", errors);

        // 使用新的Result.fail方法，将错误详情放在data中
        Result<Map<String, String>> result = Result.fail(ErrorCode.PARAMS_ERROR, "参数校验失败", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理URL参数校验异常
     * 当使用@Validated注解校验方法参数时触发
     *
     * @param e 约束违反异常
     * @return 包含详细校验错误信息的响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Map<String, String>>> handleConstraintViolationException(ConstraintViolationException e) {
        Map<String, String> errors = new HashMap<>();

        // 提取约束违反的详细信息
        e.getConstraintViolations().forEach(violation -> {
            // 获取参数路径（如：getUserById.id）
            String fieldName = violation.getPropertyPath().toString();
            // 获取校验失败的消息
            String message = violation.getMessage();
            errors.put(fieldName, message);
        });

        log.warn("URL参数校验失败: {}", errors);

        Result<Map<String, String>> result = Result.fail(ErrorCode.PARAMS_ERROR, "参数校验失败", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ========== HTTP请求异常处理 ==========

    /**
     * 处理JSON格式错误异常
     * 当请求体不是有效的JSON格式时触发
     *
     * @param e HTTP消息不可读异常
     * @return 错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("JSON格式错误: {}", e.getMessage());

        Result<Void> result = Result.fail(ErrorCode.PARAMS_ERROR, "请求数据格式错误，请检查JSON格式");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理参数类型转换异常
     * 当URL参数无法转换为目标类型时触发（如：传入字符串但期望数字）
     *
     * @param e 方法参数类型不匹配异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型转换失败: 参数[{}]无法转换为类型[{}]", e.getName(), e.getRequiredType().getSimpleName());

        String message = String.format("参数[%s]类型错误，期望类型为%s", e.getName(), e.getRequiredType().getSimpleName());
        Result<Void> result = Result.fail(ErrorCode.PARAMS_ERROR, message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理HTTP方法不支持异常
     * 当请求方法不被支持时触发（如：POST请求访问只支持GET的接口）
     *
     * @param e HTTP请求方法不支持异常
     * @return 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("HTTP方法不支持: {}", e.getMessage());

        String message = String.format("不支持%s请求方法，支持的方法: %s", e.getMethod(), String.join(", ", e.getSupportedMethods()));
        Result<Void> result = Result.fail(ErrorCode.NOT_FOUND, message);

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(result);
    }

    /**
     * 处理404异常
     * 当请求的接口不存在时触发
     *
     * @param e 无处理器找到异常
     * @return 错误响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());

        Result<Void> result = Result.fail(ErrorCode.NOT_FOUND, "请求的接口不存在");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    // ========== 数据库异常处理 ==========

    /**
     * 处理数据库访问异常
     * 包括SQL异常、连接异常等数据库相关问题
     *
     * @param e 数据访问异常
     * @return 错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccessException(DataAccessException e) {
        // 数据库异常需要记录详细的错误信息用于排查问题
        log.error("数据库访问异常", e);

        Result<Void> result = Result.fail(ErrorCode.DATABASE_ERROR);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    // ========== 兜底异常处理 ==========

    /**
     * 处理所有未被上述方法捕获的异常
     * 这是最后的兜底处理，确保不会有异常直接暴露给用户
     *
     * @param e 未知异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        // 未知异常需要记录完整的堆栈信息用于排查问题
        log.error("系统未知异常", e);

        // 不要将具体的异常信息暴露给用户，统一返回系统异常
        Result<Void> result = Result.fail(ErrorCode.SYSTEM_ERROR);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
