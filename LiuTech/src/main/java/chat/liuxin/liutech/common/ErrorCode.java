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
 * @author 刘鑫
 * @date 2024-01-15
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
    
    /**
     * 无权限操作此文章
     */
    ARTICLE_PERMISSION_DENIED(1103, "无权限操作此文章"),
    
    /**
     * 文章状态无效
     */
    ARTICLE_STATUS_INVALID(1104, "文章状态无效"),
    
    /**
     * 分类不存在
     */
    CATEGORY_NOT_FOUND(1105, "分类不存在"),
    
    /**
     * 标签不存在
     */
    TAG_NOT_FOUND(1106, "标签不存在"),

    // ========== 评论相关业务错误 1200-1299 ==========
    /**
     * 评论不存在
     */
    COMMENT_NOT_FOUND(1201, "评论不存在"),
    
    /**
     * 父评论不存在
     */
    PARENT_COMMENT_NOT_FOUND(1202, "父评论不存在"),
    
    /**
     * 父评论与文章不匹配
     */
    PARENT_COMMENT_MISMATCH(1203, "父评论与当前文章不匹配"),
    
    /**
     * 评论内容为空
     */
    COMMENT_CONTENT_EMPTY(1204, "评论内容不能为空"),
    
    /**
     * 评论内容超出长度限制
     */
    COMMENT_CONTENT_TOO_LONG(1205, "评论内容超出长度限制"),

    // ========== 点赞收藏相关业务错误 1300-1399 ==========
    /**
     * 重复点赞
     */
    ALREADY_LIKED(1301, "您已经点赞过了"),
    
    /**
     * 重复收藏
     */
    ALREADY_FAVORITED(1302, "您已经收藏过了"),
    
    /**
     * 取消点赞失败
     */
    UNLIKE_FAILED(1303, "取消点赞失败"),
    
    /**
     * 取消收藏失败
     */
    UNFAVORITE_FAILED(1304, "取消收藏失败"),

    // ========== 公告相关业务错误 1400-1499 ==========
    /**
     * 公告不存在
     */
    ANNOUNCEMENT_NOT_FOUND(1401, "公告不存在"),
    
    /**
     * 公告标题已存在
     */
    ANNOUNCEMENT_TITLE_EXISTS(1402, "公告标题已存在"),
    
    /**
     * 无权限操作此公告
     */
    ANNOUNCEMENT_PERMISSION_DENIED(1403, "无权限操作此公告"),

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
    NETWORK_ERROR(502, "网络异常，请稍后重试"),
    
    /**
     * 操作失败 - 通用操作错误
     */
    OPERATION_ERROR(503, "操作失败");

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
    
    /**
     * 根据错误码获取ErrorCode枚举
     * @param code 错误码
     * @return ErrorCode枚举，如果未找到则返回SYSTEM_ERROR
     */
    public static ErrorCode getByCode(int code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
