/**
 * 简化的错误处理工具
 * 使用SweetAlert2提供友好的错误提示
 */
import Swal from 'sweetalert2'

// 获取当前主题背景色
const getBgColor = () => {
  return getComputedStyle(document.documentElement).getPropertyValue('--bg-soft').trim() || '#F8F9FA'
}
const getTextColor = () => {
  return getComputedStyle(document.documentElement).getPropertyValue('--text-main').trim() || '#3C4043'
}
const getPrimaryColor = () => {
  return getComputedStyle(document.documentElement).getPropertyValue('--color-primary').trim() || '#2d90cd'
}
const getSuccessColor = () => {
  return getComputedStyle(document.documentElement).getPropertyValue('--color-success').trim() || '#34A853'
}
const getErrorColor = () => {
  return getComputedStyle(document.documentElement).getPropertyValue('--color-error').trim() || '#EA4335'
}
const getWarningColor = () => {
  return getComputedStyle(document.documentElement).getPropertyValue('--color-warning').trim() || '#FBBC04'
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
    confirmButtonColor: getErrorColor(),
    background: getBgColor(),
    color: getTextColor()
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
    confirmButtonColor: getSuccessColor(),
    background: getBgColor(),
    color: getTextColor()
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
    background: getBgColor(),
    color: getTextColor(),
    iconColor: getSuccessColor()
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
    background: getBgColor(),
    color: getTextColor(),
    iconColor: getErrorColor()
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
    confirmButtonColor: getWarningColor(),
    background: getBgColor(),
    color: getTextColor()
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
    confirmButtonColor: getPrimaryColor(),
    cancelButtonColor: '#6c757d',
    background: getBgColor(),
    color: getTextColor()
  }).then((result) => result.isConfirmed)
}

/**
 * 显示输入对话框
 * @param message 提示消息
 * @param title 对话框标题
 * @param defaultValue 默认值
 * @returns Promise<string | null> 用户输入的内容，取消返回 null
 */
export function showPrompt(
  message: string,
  title: string = '输入',
  defaultValue: string = ''
): Promise<string | null> {
  return Swal.fire({
    icon: 'question',
    title: title,
    text: message,
    input: 'text',
    inputValue: defaultValue,
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    confirmButtonColor: getPrimaryColor(),
    cancelButtonColor: '#6c757d',
    background: getBgColor(),
    color: getTextColor(),
    inputValidator: (value) => {
      if (!value || !value.trim()) {
        return '请输入内容'
      }
    }
  }).then((result) => {
    if (result.isConfirmed) {
      return result.value || null
    }
    return null
  })
}

// 错误去重：相同消息在 5 秒内只显示一次
const recentErrors = new Map<string, number>()

/**
 * 错误去重检查
 * @param message 错误消息
 * @returns 是否应该跳过（重复消息）
 */
function isDuplicateError(message: string): boolean {
  const now = Date.now()
  const lastTime = recentErrors.get(message)
  if (lastTime && now - lastTime < 5000) return true
  recentErrors.set(message, now)
  setTimeout(() => recentErrors.delete(message), 5000)
  return false
}

/**
 * 处理API错误
 * 使用 Toast 提示（自动消失），带去重机制
 * @param error 错误对象
 */
export function handleApiError(error: any) {
  console.error('API错误:', error)

  // 如果是网络错误
  if (!error.response) {
    const msg = '网络连接失败，请检查网络设置'
    if (!isDuplicateError(msg)) showErrorToast(msg)
    return
  }

  const status = error.response.status
  const message = error.response.data?.message || '请求失败'

  // 401 错误由拦截器统一处理，不弹窗
  if (status === 401) return

  // 所有错误统一使用 Toast（自动消失），带去重
  if (!isDuplicateError(message)) {
    showErrorToast(message)
  }
}

/**
 * 处理表单验证错误
 * 使用 Toast 提示（自动消失）
 * @param errors 验证错误对象
 */
export function handleValidationError(errors: any) {
  let message: string

  if (typeof errors === 'string') {
    message = errors
  } else if (typeof errors === 'object' && errors !== null) {
    const firstError = Object.values(errors)[0]
    message = String(Array.isArray(firstError) ? firstError[0] : firstError)
  } else {
    message = '输入信息有误，请检查后重试'
  }

  if (!isDuplicateError(message)) {
    showErrorToast(message)
  }
}

/**
 * 处理未知错误
 * 使用 Toast 提示（自动消失），带去重机制
 * @param error 错误对象
 */
export function handleUnknownError(error: any) {
  // 过滤掉null错误和Prism相关的无害错误
  if (error === null || error?.message?.includes('Prism')) {
    return
  }
  console.error('未知错误:', error)
  const message = error?.message || '发生未知错误，请稍后重试'
  if (!isDuplicateError(message)) {
    showErrorToast(message)
  }
}