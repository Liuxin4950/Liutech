/**
 * 全局错误处理初始化
 * 捕获未处理的错误和Promise拒绝
 */
import { handleUnknownError } from './errorHandler'

/**
 * 初始化全局错误处理
 */
export function initGlobalErrorHandler() {
  // 捕获未处理的JavaScript错误
  window.addEventListener('error', (event) => {
    console.error('全局错误:', event.error)
    handleUnknownError(event.error)
  })

  // 捕获未处理的Promise拒绝
  window.addEventListener('unhandledrejection', (event) => {
    console.error('未处理的Promise拒绝:', event.reason)
    handleUnknownError(event.reason)
    // 阻止默认的控制台错误输出
    event.preventDefault()
  })

  console.log('✅ 全局错误处理已初始化')
}

/**
 * 为Vue应用配置错误处理
 * @param app Vue应用实例
 */
export function configureVueErrorHandler(app: any) {
  app.config.errorHandler = (err: any, _instance: any, info: string) => {
    console.error('Vue错误:', err, info)
    handleUnknownError(err)
  }

  app.config.warnHandler = (msg: string, _instance: any, trace: string) => {
    console.warn('Vue警告:', msg, trace)
  }
}