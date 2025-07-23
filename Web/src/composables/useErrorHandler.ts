/**
 * 错误处理组合式函数
 * 在Vue组件中使用的错误处理逻辑
 */
import { ref } from 'vue'
import { errorHandler, type ErrorInfo } from '../utils/errorHandler'

export function useErrorHandler() {
  // 错误状态
  const error = ref<ErrorInfo | null>(null)
  const isError = ref(false)

  /**
   * 清除错误状态
   */
  const clearError = () => {
    error.value = null
    isError.value = false
  }

  /**
   * 设置错误状态
   * @param errorInfo 错误信息
   */
  const setError = (errorInfo: ErrorInfo) => {
    error.value = errorInfo
    isError.value = true
  }

  /**
   * 处理异步操作的错误
   * @param asyncFn 异步函数
   * @param showError 是否显示错误提示，默认true
   */
  const handleAsync = async <T>(
    asyncFn: () => Promise<T>,
    showError: boolean = true
  ): Promise<T | null> => {
    try {
      clearError()
      const result = await asyncFn()
      return result
    } catch (err: any) {
      const errorInfo = errorHandler.handleApiError(err)
      if (showError) {
        setError(errorInfo)
      }
      return null
    }
  }

  /**
   * 处理表单提交错误
   * @param submitFn 提交函数
   */
  const handleFormSubmit = async <T>(
    submitFn: () => Promise<T>
  ): Promise<T | null> => {
    try {
      clearError()
      const result = await submitFn()
      return result
    } catch (err: any) {
      // 特殊处理表单验证错误
      if (err.response?.status === 422 && err.response?.data?.errors) {
        const errorInfo = errorHandler.handleValidationErrors(err.response.data.errors)
        setError(errorInfo)
      } else {
        const errorInfo = errorHandler.handleApiError(err)
        setError(errorInfo)
      }
      return null
    }
  }

  /**
   * 处理业务逻辑错误
   * @param message 错误消息
   * @param code 错误代码
   */
  const showBusinessError = (message: string, code?: string | number) => {
    const errorInfo = errorHandler.handleBusinessError(message, code)
    setError(errorInfo)
  }

  /**
   * 显示成功消息
   * @param message 成功消息
   */
  const showSuccess = (message: string) => {
    // 这里可以集成成功提示组件
    console.log(`✅ ${message}`)
    alert(`操作成功：${message}`)
    clearError()
  }

  /**
   * 显示警告消息
   * @param message 警告消息
   */
  const showWarning = (message: string) => {
    // 这里可以集成警告提示组件
    console.warn(`⚠️ ${message}`)
    alert(`警告：${message}`)
  }

  return {
    // 状态
    error,
    isError,
    
    // 方法
    clearError,
    setError,
    handleAsync,
    handleFormSubmit,
    showBusinessError,
    showSuccess,
    showWarning
  }
}