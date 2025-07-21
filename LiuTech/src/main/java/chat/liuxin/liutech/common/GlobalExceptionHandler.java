package chat.liuxin.liutech.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 捕获业务异常
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        Result<Void> result = new Result<>(e.getCode(), e.getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // 处理请求参数验证异常(JSON请求)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage(),
                        (existing, replacement) -> existing + ", " + replacement
                ));
        log.warn("参数验证失败: {}", errors);
        Result<Map<String, String>> result = Result.fail(ErrorCode.PARAMS_ERROR, "参数验证失败");
        result.setData(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // 处理请求参数验证异常(非JSON请求)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Map<String, String>>> handleConstraintViolationException(ConstraintViolationException e) {
        Map<String, String> errors = new HashMap<>();
        e.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(fieldName, message);
        });
        log.warn("参数验证失败: {}", errors);
        Result<Map<String, String>> result = Result.fail(ErrorCode.PARAMS_ERROR, "参数验证失败", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // 捕获其他未知异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        Result<Void> result = Result.fail(ErrorCode.SYSTEM_ERROR, "参数验证失败");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
