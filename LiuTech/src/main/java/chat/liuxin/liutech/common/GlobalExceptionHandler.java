package chat.liuxin.liutech.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 捕获业务异常
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        Result<Void> result = new Result<>(e.getCode(), e.getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // 捕获其他未知异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        e.printStackTrace(); // 建议用日志框架记录
        Result<Void> result = Result.fail(ErrorCode.SYSTEM_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
