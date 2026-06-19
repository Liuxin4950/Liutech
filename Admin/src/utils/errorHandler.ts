/**
 * 错误处理工具
 * 使用 ant-design-vue 的 message / Modal 提供提示
 */
import { message, Modal } from 'ant-design-vue'

// 去重机制：相同消息 5 秒内只显示一次
const recentErrors = new Map<string, number>()

function shouldDedupe(msg: string): boolean {
  const now = Date.now()
  const lastTime = recentErrors.get(msg)
  if (lastTime && now - lastTime < 5000) return true
  recentErrors.set(msg, now)
  setTimeout(() => recentErrors.delete(msg), 5000)
  return false
}

export function showError(msg: string) {
  message.error(msg)
}

export function showSuccess(msg: string) {
  message.success(msg)
}

export function showWarning(msg: string) {
  message.warning(msg)
}

/** 兼容旧签名，行为与 showSuccess 相同 */
export function showSuccessToast(msg: string) {
  message.success(msg)
}

/** 兼容旧签名，行为与 showError 相同 */
export function showErrorToast(msg: string) {
  message.error(msg)
}

/**
 * 显示确认对话框
 * @returns Promise<boolean> 用户是否确认
 */
export function showConfirm(msg: string, title: string = '确认'): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title,
      content: msg,
      okText: '确定',
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}

/**
 * 处理API错误（Toast 自动消失，带去重）
 */
export function handleApiError(error: any) {
  console.error('API错误:', error)

  let msg: string
  if (!error.response) {
    msg = '网络连接失败，请检查网络设置'
  } else {
    const status = error.response.status
    switch (status) {
      case 401: msg = '登录已过期，请重新登录'; break
      case 403: msg = '权限不足，禁止访问'; break
      case 404: msg = '请求的资源不存在'; break
      case 500: msg = '服务器内部错误，请稍后重试'; break
      default:  msg = error.response.data?.message || '请求失败'
    }
  }

  if (!shouldDedupe(msg)) {
    message.error(msg)
  }
}

/**
 * 处理表单验证错误
 */
export function handleValidationError(errors: any) {
  if (typeof errors === 'string') {
    message.error(errors)
    return
  }
  if (typeof errors === 'object' && errors !== null) {
    const firstError = Object.values(errors)[0]
    const msg = Array.isArray(firstError) ? firstError[0] : firstError
    message.error(String(msg))
    return
  }
  message.error('输入信息有误，请检查后重试')
}

/**
 * 处理未知错误（Toast 自动消失，带去重）
 */
export function handleUnknownError(error: any) {
  console.error('未知错误:', error)

  if (error === null || error === undefined) {
    console.warn('捕获到null/undefined错误，已忽略')
    return
  }

  const msg = error?.message || error?.toString?.() || '发生未知错误，请稍后重试'
  if (!shouldDedupe(msg)) {
    message.error(msg)
  }
}
