package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理端控制器基类
 * 提供简单的异常处理工具方法，避免代码重复
 * 
 * @author 刘鑫
 * @date 2024-01-30
 */
@Slf4j
public abstract class BaseAdminController {

    /**
     * 处理操作结果
     * 统一处理Service层返回的boolean结果
     * 
     * @param success 操作是否成功
     * @param successMessage 成功消息
     * @param operationName 操作名称（用于错误日志）
     * @return 统一响应结果
     */
    protected Result<String> handleOperationResult(boolean success, String successMessage, String operationName) {
        if (success) {
            return Result.success(successMessage);
        } else {
            log.warn("{}失败", operationName);
            return Result.fail(ErrorCode.OPERATION_ERROR, operationName + "失败");
        }
    }

    /**
     * 处理异常并返回错误响应
     * 统一的异常日志记录和错误响应
     * 
     * @param e 异常对象
     * @param operationName 操作名称
     * @param <T> 返回数据类型
     * @return 错误响应
     */
    protected <T> Result<T> handleException(Exception e, String operationName) {
        log.error("{}失败: {}", operationName, e.getMessage(), e);
        return Result.fail(ErrorCode.SYSTEM_ERROR, operationName + "失败: " + e.getMessage());
    }

    /**
     * 检查资源是否存在
     * 统一处理资源不存在的情况
     * 
     * @param resource 资源对象
     * @param notFoundErrorCode 资源不存在时的错误码
     * @param <T> 资源类型
     * @return 如果资源存在返回成功响应，否则返回错误响应
     */
    protected <T> Result<T> checkResourceExists(T resource, ErrorCode notFoundErrorCode) {
        if (resource == null) {
            return Result.fail(notFoundErrorCode);
        }
        return Result.success(resource);
    }
}