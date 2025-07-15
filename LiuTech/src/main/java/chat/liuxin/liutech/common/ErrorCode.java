package chat.liuxin.liutech.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 成功
    SUCCESS(200, "响应成功"),

    // 参数错误
    PARAMS_ERROR(1001, "请求参数错误"),
    USER_NOT_FOUND(1002, "用户不存在"),
    USERNAME_EMPTY(1003, "用户名不能为空"),

    // 系统错误
    SYSTEM_ERROR(500, "系统异常，请联系管理员");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
