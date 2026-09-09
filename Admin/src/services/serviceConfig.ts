/**
 * 服务基础地址配置（Admin 端）
 *
 * 统一主后端与 AI 服务的 baseURL 解析，避免各 service 文件各自拼地址。
 * 解析策略与 Web 端 `Web/src/services/serviceConfig.ts` 保持一致：
 * 环境变量优先 → 开发环境本地地址 → 生产环境同源相对路径。
 *
 * 作者：刘鑫
 */

// 开发环境判断（由 Vite 在构建时注入）
const isDevelopment = import.meta.env.DEV

/**
 * 主后端 baseURL
 *
 * 优先级：VITE_API_BASE_URL → 开发环境 http://127.0.0.1:8080 → 生产环境 /api。
 *
 * 生产环境必须走同源 /api（根 Nginx 反向代理到 backend 容器）：
 * 不要写 `backend:8080`，该主机名只在 Docker 内网可解析，浏览器侧拿不到。
 */
export const getBackendURL = (): string => {
  const envUrl = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (envUrl && envUrl.trim().length > 0) {
    return envUrl.trim()
  }
  return isDevelopment ? 'http://127.0.0.1:8080' : '/api'
}

/**
 * AI 服务 baseURL（返回值保证以 /ai 结尾，调用方只写业务路径）
 *
 * 优先级：VITE_AI_BASE_URL → 开发环境 http://127.0.0.1:8081/ai → 生产环境 /ai。
 *
 * AI 服务是独立容器（8081），与主后端分开配置：生产由根 Nginx 的 /ai 代理转发，
 * 开发环境直连 8081，因此不能复用主实例再拼 /ai 前缀（那样开发环境会打到 8080）。
 */
export const getAiBaseUrl = (): string => {
  const envUrl = import.meta.env.VITE_AI_BASE_URL as string | undefined
  const raw = envUrl && envUrl.trim().length > 0
    ? envUrl.trim()
    : (isDevelopment ? 'http://127.0.0.1:8081/ai' : '/ai')

  // 去掉结尾斜杠，再补齐 /ai 后缀，保证调用方拼接业务路径时不会出现双斜杠或漏前缀
  const trimmed = raw.replace(/\/$/, '')
  return trimmed.endsWith('/ai') ? trimmed : `${trimmed}/ai`
}
