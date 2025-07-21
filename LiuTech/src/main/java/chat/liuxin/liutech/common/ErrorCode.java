package chat.liuxin.liutech.common;

import lombok.Getter;

/**
 * 错误码枚举类
 * 统一管理系统中所有的错误码和错误信息
 * 
 * 错误码设计规范：
 * - 200: 成功
 * - 400-499: 客户端错误（请求参数、权限等）
 * - 1000-1999: 业务逻辑错误（用户相关、数据相关等）
 * - 500-599: 服务器内部错误
 * 
 * @author liuxin
 */
@Getter
public enum ErrorCode {

    // ========== 成功状态 ==========
    SUCCESS(200, "操作成功"),

    // ========== 客户端错误 4xx ==========
    /**
     * 请求参数错误 - 通用参数校验失败
     */
    PARAMS_ERROR(400, "请求参数错误"),
    
    /**
     * 未授权访问 - 用户未登录或token无效
     */
    UNAUTHORIZED(401, "未授权访问，请先登录"),
    
    /**
     * 禁止访问 - 用户权限不足
     */
    FORBIDDEN(403, "权限不足，禁止访问"),
    
    /**
     * 资源不存在 - 请求的资源未找到
     */
    NOT_FOUND(404, "请求的资源不存在"),

    // ========== 用户相关业务错误 1000-1099 ==========
    /**
     * 用户不存在 - 根据用户名或ID查询用户失败
     */
    USER_NOT_FOUND(1001, "用户不存在"),
    
    /**
     * 用户名已存在 - 注册时用户名重复
     */
    USERNAME_EXISTS(1002, "用户名已存在"),
    
    /**
     * 邮箱已存在 - 注册时邮箱重复
     */
    EMAIL_EXISTS(1003, "邮箱已被注册"),
    
    /**
     * 登录失败 - 用户名或密码错误（安全考虑，不具体说明是哪个错误）
     */
    LOGIN_FAILED(1004, "用户名或密码错误"),
    
    /**
     * 账户被禁用 - 用户状态异常
     */
    ACCOUNT_DISABLED(1005, "账户已被禁用，请联系管理员"),
    
    /**
     * 用户名不能为空 - 特定的参数校验错误
     */
    USERNAME_EMPTY(1006, "用户名不能为空"),
    
    /**
     * 密码格式错误 - 密码不符合安全要求
     */
    PASSWORD_FORMAT_ERROR(1007, "密码格式不正确"),

    // ========== 博客相关业务错误 1100-1199 ==========
    /**
     * 文章不存在
     */
    ARTICLE_NOT_FOUND(1101, "文章不存在"),
    
    /**
     * 文章标题已存在
     */
    ARTICLE_TITLE_EXISTS(1102, "文章标题已存在"),

    // ========== 系统错误 5xx ==========
    /**
     * 系统内部错误 - 未知的系统异常
     */
    SYSTEM_ERROR(500, "系统异常，请联系管理员"),
    
    /**
     * 数据库操作异常 - 数据库连接、SQL执行等错误
     */
    DATABASE_ERROR(501, "数据库操作异常"),
    
    /**
     * 网络异常 - 外部服务调用失败
     */
    NETWORK_ERROR(502, "网络异常，请稍后重试");

    /**
     * 错误码
     */
    private final int code;
    
    /**
     * 错误信息
     */
    private final String message;

    /**
     * 构造函数
     * @param code 错误码
     * @param message 错误信息
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
