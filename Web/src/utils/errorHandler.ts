/**
 * 简化的错误处理工具
 * 使用SweetAlert2提供友好的错误提示
 */
import Swal from 'sweetalert2'

/**
 * 显示错误消息
 * @param message 错误消息
 * @param title 错误标题
 */
export function showError(message: string, title: string = '错误') {
  Swal.fire({
    icon: 'error',
    title: title,
    text: message,
    confirmButtonText: '确定',
    confirmButtonColor: '#d33'
  })
}

/**
 * 显示成功消息
 * @param message 成功消息
 * @param title 成功标题
 */
export function showSuccess(message: string, title: string = '成功') {
  Swal.fire({
    icon: 'success',
    title: title,
    text: message,
    confirmButtonText: '确定',
    confirmButtonColor: '#28a745'
  })
}

/**
 * 显示警告消息
 * @param message 警告消息
 * @param title 警告标题
 */
export function showWarning(message: string, title: string = '警告') {
  Swal.fire({
    icon: 'warning',
    title: title,
    text: message,
    confirmButtonText: '确定',
    confirmButtonColor: '#ffc107'
  })
}

/**
 * 显示确认对话框
 * @param message 确认消息
 * @param title 确认标题
 * @returns Promise<boolean> 用户是否确认
 */
export function showConfirm(message: string, title: string = '确认'): Promise<boolean> {
  return Swal.fire({
    icon: 'question',
    title: title,
    text: message,
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    confirmButtonColor: '#007bff',
    cancelButtonColor: '#6c757d'
  }).then((result) => result.isConfirmed)
}

/**
 * 处理API错误
 * 根据后端返回的错误信息显示相应提示
 * @param error 错误对象
 */
export function handleApiError(error: any) {
  console.error('API错误:', error)
  
  // 如果是网络错误
  if (!error.response) {
    showError('网络连接失败，请检查网络设置', '网络错误')
    return
  }
  
  const status = error.response.status
  const message = error.response.data?.message || '请求失败'
  
  switch (status) {
    case 401:
      showError('登录已过期，请重新登录', '认证失败')
      // 可以在这里添加跳转到登录页的逻辑
      break
    case 403:
      showError('权限不足，禁止访问', '权限错误')
      break
    case 404:
      showError('请求的资源不存在', '资源不存在')
      break
    case 500:
      showError('服务器内部错误，请稍后重试', '服务器错误')
      break
    default:
      showError(message, '请求失败')
  }
}

/**
 * 处理表单验证错误
 * @param errors 验证错误对象
 */
export function handleValidationError(errors: any) {
  if (typeof errors === 'string') {
    showError(errors, '输入错误')
    return
  }
  
  // 如果是对象形式的验证错误
  if (typeof errors === 'object' && errors !== null) {
    const firstError = Object.values(errors)[0]
    const message = Array.isArray(firstError) ? firstError[0] : firstError
    showError(String(message), '输入错误')
    return
  }
  
  showError('输入信息有误，请检查后重试', '输入错误')
}

/**
 * 处理未知错误
 * @param error 错误对象
 */
export function handleUnknownError(error: any) {
  console.error('未知错误:', error)
  const message = error?.message || '发生未知错误，请稍后重试'
  showError(message, '系统错误')
}