/**
 * 错误处理组合式函数
 * 在Vue组件中使用的错误处理逻辑
 */
import { ref } from 'vue'
import { 
  handleApiError, 
  handleValidationError, 
  showError, 
  showSuccess, 
  showSuccessToast, 
  showErrorToast,
  showWarning, 
  showConfirm 
} from '../utils/errorHandler'

export function useErrorHandler() {
  // 错误状态
  const error = ref<string | null>(null)
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
   * @param message 错误信息
   */
  const setError = (message: string) => {
    error.value = message
    isError.value = true
  }

  /**
   * 处理异步操作的错误
   * @param asyncFn 异步函数
   * @param options 配置选项
   */
  const handleAsync = async <T>(
    asyncFn: () => Promise<T>,
    options?: {
      silent?: boolean
      onError?: (error: any) => void
      onFinally?: () => void
    }
  ): Promise<T | null> => {
    try {
      clearError()
      const result = await asyncFn()
      return result
    } catch (err: any) {
      if (options?.onError) {
        options.onError(err)
      } else if (!options?.silent && !err?.isBusiness) {
        // 业务错误已在拦截器里用Toast提示，这里避免再次弹模态框
        handleApiError(err)
      }
      setError(err?.response?.data?.message || err?.message || '操作失败')
      return null
    } finally {
      if (options?.onFinally) {
        options.onFinally()
      }
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
        handleValidationError(err.response.data.errors)
        setError('表单验证失败')
      } else {
        // 业务错误已在拦截器里用Toast提示，这里避免再次弹模态框
        if (!err?.isBusiness) {
          handleApiError(err)
        }
        setError(err?.response?.data?.message || err?.message || '提交失败')
      }
      return null
    }
  }

  /**
   * 显示业务错误
   * @param message 错误消息
   * @param title 错误标题
   */
  const showBusinessError = (message: string, title?: string) => {
    showError(message, title)
    setError(message)
  }

  /**
   * 显示确认对话框
   * @param message 确认消息
   * @param title 确认标题
   */
  const confirm = (message: string, title?: string) => {
    return showConfirm(message, title)
  }

  /**
   * 显示成功Toast提示（自动消失）
   * @param message 成功消息
   */
  const showToastSuccess = (message: string) => {
    showSuccessToast(message)
  }

  /**
   * 显示错误Toast提示（自动消失）
   * @param message 错误消息
   */
  const showToastError = (message: string) => {
    showErrorToast(message)
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
    showSuccessToast,
    showToastSuccess,
    showErrorToast,
    showToastError,
    showWarning,
    showError,
    confirm
  }
}