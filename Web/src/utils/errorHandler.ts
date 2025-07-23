/**
 * 全局错误处理工具
 * 统一处理应用中的各种错误情况
 */

// 错误类型常量
export const ErrorType = {
  NETWORK: 'NETWORK',
  AUTH: 'AUTH',
  VALIDATION: 'VALIDATION',
  BUSINESS: 'BUSINESS',
  UNKNOWN: 'UNKNOWN'
} as const

// 错误类型联合类型
export type ErrorType = typeof ErrorType[keyof typeof ErrorType]

// 错误信息接口
export interface ErrorInfo {
  type: ErrorType
  message: string
  code?: string | number
  details?: any
}

// 错误处理器类
class ErrorHandler {
  /**
   * 显示错误消息
   * @param message 错误消息
   * @param type 错误类型
   */
  private showError(message: string, type: ErrorType = ErrorType.UNKNOWN) {
    // 这里可以集成消息提示组件，暂时使用console和alert
    console.error(`[${type}] ${message}`)
    
    // 根据错误类型显示不同的提示
    switch (type) {
      case ErrorType.NETWORK:
        alert(`网络错误：${message}`)
        break
      case ErrorType.AUTH:
        alert(`认证错误：${message}`)
        break
      case ErrorType.VALIDATION:
        alert(`输入错误：${message}`)
        break
      case ErrorType.BUSINESS:
        alert(`业务错误：${message}`)
        break
      default:
        alert(`系统错误：${message}`)
    }
  }

  /**
   * 处理API错误
   * @param error 错误对象
   */
  handleApiError(error: any): ErrorInfo {
    let errorInfo: ErrorInfo

    if (error.response) {
      // HTTP错误响应
      const status = error.response.status
      const message = error.response.data?.message || '请求失败'
      
      switch (status) {
        case 401:
          errorInfo = {
            type: ErrorType.AUTH,
            message: '登录已过期，请重新登录',
            code: status
          }
          break
        case 403:
          errorInfo = {
            type: ErrorType.AUTH,
            message: '权限不足，禁止访问',
            code: status
          }
          break
        case 404:
          errorInfo = {
            type: ErrorType.NETWORK,
            message: '请求的资源不存在',
            code: status
          }
          break
        case 422:
          errorInfo = {
            type: ErrorType.VALIDATION,
            message: message,
            code: status
          }
          break
        case 500:
          errorInfo = {
            type: ErrorType.BUSINESS,
            message: '服务器内部错误，请稍后重试',
            code: status
          }
          break
        default:
          errorInfo = {
            type: ErrorType.UNKNOWN,
            message: message,
            code: status
          }
      }
    } else if (error.request) {
      // 网络错误
      errorInfo = {
        type: ErrorType.NETWORK,
        message: '网络连接失败，请检查网络设置',
        details: error.request
      }
    } else {
      // 其他错误
      errorInfo = {
        type: ErrorType.UNKNOWN,
        message: error.message || '未知错误',
        details: error
      }
    }

    this.showError(errorInfo.message, errorInfo.type)
    return errorInfo
  }

  /**
   * 处理表单验证错误
   * @param errors 验证错误对象
   */
  handleValidationErrors(errors: Record<string, string[]>) {
    const messages = Object.entries(errors)
      .map(([field, msgs]) => `${field}: ${msgs.join(', ')}`)
      .join('\n')
    
    const errorInfo: ErrorInfo = {
      type: ErrorType.VALIDATION,
      message: `表单验证失败：\n${messages}`,
      details: errors
    }

    this.showError(errorInfo.message, errorInfo.type)
    return errorInfo
  }

  /**
   * 处理业务逻辑错误
   * @param message 错误消息
   * @param code 错误代码
   */
  handleBusinessError(message: string, code?: string | number) {
    const errorInfo: ErrorInfo = {
      type: ErrorType.BUSINESS,
      message,
      code
    }

    this.showError(errorInfo.message, errorInfo.type)
    return errorInfo
  }

  /**
   * 处理未捕获的错误
   * @param error 错误对象
   */
  handleUnknownError(error: any) {
    const errorInfo: ErrorInfo = {
      type: ErrorType.UNKNOWN,
      message: error?.message || '发生未知错误',
      details: error
    }

    this.showError(errorInfo.message, errorInfo.type)
    return errorInfo
  }
}

// 创建全局错误处理器实例
export const errorHandler = new ErrorHandler()

// 导出便捷方法
export const handleApiError = (error: any) => errorHandler.handleApiError(error)
export const handleValidationErrors = (errors: Record<string, string[]>) => errorHandler.handleValidationErrors(errors)
export const handleBusinessError = (message: string, code?: string | number) => errorHandler.handleBusinessError(message, code)
export const handleUnknownError = (error: any) => errorHandler.handleUnknownError(error)