import axios from 'axios'
import type { AxiosInstance } from 'axios'
import { message } from 'ant-design-vue'
import router from '../router'

/**
 * AI 服务 baseURL 解析。
 *
 * 返回值已包含 /ai 前缀,调用方写业务路径即可(如 /admin/models/list、/writing/stream)。
 *
 * 开发环境:VITE_AI_BASE_URL 未配置时兜底 http://127.0.0.1:8081/ai
 * 生产环境:VITE_AI_BASE_URL=/ai 由 Nginx 代理转发到 AI 服务容器
 *
 * 不能复用主 api 实例加 /ai/ 前缀 —— 那样只在生产 Nginx 代理下成立,
 * 开发环境会打到主后端 8080 导致 404/500。
 */
export const getAiBaseUrl = (): string => {
  const envUrl = import.meta.env.VITE_AI_BASE_URL as string | undefined
  const raw = envUrl && envUrl.trim().length > 0 ? envUrl.trim() : 'http://127.0.0.1:8081/ai'
  const trimmed = raw.replace(/\/$/, '')
  return trimmed.endsWith('/ai') ? trimmed : `${trimmed}/ai`
}

/**
 * AI 服务专用 axios 实例。
 *
 * 与主 api.ts 分离:baseURL 指向 AI 服务(8081),不经过 /api 主后端代理。
 * 拦截器行为对齐 api.ts:请求注入 JWT、401 跳登录、403 跳 403 页面,
 * 其他错误交给调用方 catch 处理。
 */
export const aiApi: AxiosInstance = axios.create({
  baseURL: getAiBaseUrl(),
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

aiApi.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

aiApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      message.destroy()
      localStorage.removeItem('token')
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    }
    if (error.response?.status === 403) {
      message.destroy()
      if (router.currentRoute.value.path !== '/403') {
        router.push('/403')
      }
    }
    return Promise.reject(error)
  }
)
