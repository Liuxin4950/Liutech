import axios from 'axios'
import type { AxiosInstance } from 'axios'
import { message } from 'ant-design-vue'
import router from '../router'

// AI 服务 axios 实例（独立 baseURL，指向 AI 服务）
// 开发环境：http://127.0.0.1:8081，生产环境通过 Nginx 的 /ai/ 代理
const aiApi: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_AI_BASE_URL || 'http://127.0.0.1:8081',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器 —— 添加 token（与主 api.ts 一致）
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

// 响应拦截器 —— 路由跳转 + 统一解包
aiApi.interceptors.response.use(
  (response) => response.data,
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

/**
 * 模型配置接口
 */
export interface ModelConfig {
  id: number
  modelName: string
  displayName: string
  provider: string
  isEnabled: boolean
  isDefault: boolean
  sortOrder: number
  maxTokens?: number
  temperature?: number
  description?: string
}

/**
 * 模型配置请求接口
 */
export interface ModelConfigRequest {
  modelName: string
  displayName: string
  provider: string
  isEnabled: boolean
  sortOrder?: number
  maxTokens?: number
  temperature?: number
  description?: string
}

/**
 * 模型使用统计接口
 */
export interface ModelUsageStats {
  model: string
  usageCount: number
}

const aiModelsService = {
  getModelList: (): Promise<ModelConfig[]> =>
    aiApi.get('/admin/models/list'),

  addModel: (data: ModelConfigRequest): Promise<ModelConfig> =>
    aiApi.post('/admin/models', data),

  updateModel: (id: number, data: ModelConfigRequest): Promise<ModelConfig> =>
    aiApi.put(`/admin/models/${id}`, data),

  deleteModel: (id: number): Promise<void> =>
    aiApi.delete(`/admin/models/${id}`),

  setDefaultModel: (id: number): Promise<void> =>
    aiApi.put(`/admin/models/${id}/default`),

  toggleEnabled: (id: number, enabled: boolean): Promise<void> =>
    aiApi.put(`/admin/models/${id}/toggle`, null, { params: { enabled } }),
}

export default aiModelsService
