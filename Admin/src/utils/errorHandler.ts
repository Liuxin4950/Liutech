/**
 * 简化的错误处理工具
 * 使用SweetAlert2提供友好的错误提示
 */
import Swal from 'sweetalert2'

// 去重机制：相同消息 5 秒内只显示一次
const recentErrors = new Map<string, number>()

/**
 * 去重检查：相同消息 5 秒内只显示一次
 * @param message 错误消息
 * @returns 是否应该跳过（true = 跳过，false = 显示）
 */
function shouldDedupe(message: string): boolean {
  const now = Date.now()
  const lastTime = recentErrors.get(message)
  if (lastTime && now - lastTime < 5000) return true
  recentErrors.set(message, now)
  setTimeout(() => recentErrors.delete(message), 5000)
  return false
}

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
 * 显示成功Toast提示（自动消失）
 * @param message 成功消息
 */
export function showSuccessToast(message: string) {
  Swal.fire({
    icon: 'success',
    title: message,
    toast: true,
    position: 'top-end',
    showConfirmButton: false,
    timer: 2000,
    timerProgressBar: true,
    background: 'var(--bg-soft)',
    color: 'var(--text-main)',
    iconColor: 'var(--color-primary)'
  })
}

/**
 * 显示错误Toast提示（自动消失）
 * @param message 错误消息
 */
export function showErrorToast(message: string) {
  Swal.fire({
    icon: 'error',
    title: message,
    toast: true,
    position: 'top-end',
    showConfirmButton: false,
    timer: 3000,
    timerProgressBar: true,
    background: 'var(--bg-soft)',
    color: 'var(--text-main)',
    iconColor: '#d33'
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
 * 根据后端返回的错误信息显示相应提示（Toast 自动消失，带去重）
 * @param error 错误对象
 */
export function handleApiError(error: any) {
  console.error('API错误:', error)

  let message: string

  // 如果是网络错误（没有 response）
  if (!error.response) {
    message = '网络连接失败，请检查网络设置'
  } else {
    const status = error.response.status
    switch (status) {
      case 401:
        message = '登录已过期，请重新登录'
        break
      case 403:
        message = '权限不足，禁止访问'
        break
      case 404:
        message = '请求的资源不存在'
        break
      case 500:
        message = '服务器内部错误，请稍后重试'
        break
      default:
        message = error.response.data?.message || '请求失败'
    }
  }

  if (!shouldDedupe(message)) {
    showErrorToast(message)
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
 * 处理未知错误（Toast 自动消失，带去重）
 * @param error 错误对象
 */
export function handleUnknownError(error: any) {
  console.error('未知错误:', error)

  // 如果错误为null或undefined，直接返回，不显示错误提示
  if (error === null || error === undefined) {
    console.warn('捕获到null/undefined错误，已忽略')
    return
  }

  const message = error?.message || error?.toString?.() || '发生未知错误，请稍后重试'
  if (!shouldDedupe(message)) {
    showErrorToast(message)
  }
}