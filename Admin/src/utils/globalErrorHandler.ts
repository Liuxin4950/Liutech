/**
 * 全局错误处理初始化
 *
 * 捕获三类"没人接"的错误：未处理的 JS 错误、未处理的 Promise 拒绝、Vue 组件内错误，
 * 统一用 antd message 提示，并对相同消息做 5 秒去重（避免连锁错误刷屏）。
 *
 * 说明：本文件原先依赖 utils/errorHandler.ts 的 handleUnknownError，而该文件其余封装
 * 长期无人调用（页面/store 统一用 antd message，网络层由 api.ts 拦截器统一提示）。
 * 为避免"两套错误处理入口"，这里把去重与提示逻辑收敛到本文件，errorHandler.ts 已删除。
 */
import { message } from 'ant-design-vue'

// 去重表：相同消息 5 秒内只提示一次
const recentErrors = new Map<string, number>()

function shouldDedupe(msg: string): boolean {
  const now = Date.now()
  const lastTime = recentErrors.get(msg)
  if (lastTime && now - lastTime < 5000) return true
  recentErrors.set(msg, now)
  setTimeout(() => recentErrors.delete(msg), 5000)
  return false
}

/**
 * 上报未知错误：忽略 null/undefined，取 error.message 兜底提示，并做去重
 */
function reportUnknownError(error: any) {
  if (error === null || error === undefined) {
    console.warn('捕获到 null/undefined 错误，已忽略')
    return
  }
  console.error('未知错误:', error)
  const msg = error?.message || error?.toString?.() || '发生未知错误，请稍后重试'
  if (!shouldDedupe(msg)) {
    message.error(msg)
  }
}

/**
 * 初始化全局错误处理
 */
export function initGlobalErrorHandler() {
  // 捕获未处理的 JavaScript 错误
  window.addEventListener('error', (event) => {
    // 过滤掉 ResizeObserver 错误，这是浏览器的已知问题
    if (event.error && event.error.message && event.error.message.includes('ResizeObserver')) {
      return
    }
    reportUnknownError(event.error)
  })

  // 捕获未处理的 Promise 拒绝
  window.addEventListener('unhandledrejection', (event) => {
    // 过滤掉 ResizeObserver 错误，这是浏览器的已知问题
    if (event.reason && event.reason.message && event.reason.message.includes('ResizeObserver')) {
      event.preventDefault()
      return
    }
    console.error('未处理的Promise拒绝:', event.reason)
    reportUnknownError(event.reason)
    // 阻止默认的控制台错误输出
    event.preventDefault()
  })
}

/**
 * 为 Vue 应用配置错误处理
 * @param app Vue应用实例
 */
export function configureVueErrorHandler(app: any) {
  app.config.errorHandler = (err: any, _instance: any, info: string) => {
    console.error('Vue错误:', err, info)
    reportUnknownError(err)
  }

  app.config.warnHandler = (msg: string, _instance: any, trace: string) => {
    console.warn('Vue警告:', msg, trace)
  }
}
