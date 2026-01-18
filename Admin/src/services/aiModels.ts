import axios from 'axios'
import type { AxiosInstance } from 'axios'

type DataAxiosInstance = Omit<
  AxiosInstance,
  'request' | 'get' | 'delete' | 'head' | 'options' | 'post' | 'put' | 'patch'
> & {
  request<T = any>(config: unknown): Promise<T>
  get<T = any>(url: string, config?: unknown): Promise<T>
  delete<T = any>(url: string, config?: unknown): Promise<T>
  head<T = any>(url: string, config?: unknown): Promise<T>
  options<T = any>(url: string, config?: unknown): Promise<T>
  post<T = any>(url: string, data?: unknown, config?: unknown): Promise<T>
  put<T = any>(url: string, data?: unknown, config?: unknown): Promise<T>
  patch<T = any>(url: string, data?: unknown, config?: unknown): Promise<T>
}

// 创建 AI 服务专用的 axios 实例
const aiApi = axios.create({
  baseURL: import.meta.env.VITE_AI_BASE_URL || 'http://127.0.0.1:8081',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
}) as DataAxiosInstance

// 请求拦截器 - 添加 token
aiApi.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
aiApi.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
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
  /**
   * 获取所有模型配置列表
   */
  getModelList: (): Promise<ModelConfig[]> => {
    return aiApi.get<ModelConfig[]>('/admin/models/list')
  },

  /**
   * 获取所有启用的模型
   */
  getEnabledModels: (): Promise<ModelConfig[]> => {
    return aiApi.get<ModelConfig[]>('/admin/models/enabled')
  },

  /**
   * 获取默认模型
   */
  getDefaultModel: (): Promise<ModelConfig> => {
    return aiApi.get<ModelConfig>('/admin/models/default')
  },

  /**
   * 根据ID获取模型配置
   */
  getModelById: (id: number): Promise<ModelConfig> => {
    return aiApi.get<ModelConfig>(`/admin/models/${id}`)
  },

  /**
   * 添加新模型配置
   */
  addModel: (data: ModelConfigRequest): Promise<ModelConfig> => {
    return aiApi.post<ModelConfig>('/admin/ai/models', data)
  },

  /**
   * 更新模型配置
   */
  updateModel: (id: number, data: ModelConfigRequest): Promise<ModelConfig> => {
    return aiApi.put<ModelConfig>(`/admin/models/${id}`, data)
  },

  /**
   * 删除模型配置
   */
  deleteModel: (id: number): Promise<void> => {
    return aiApi.delete<void>(`/admin/models/${id}`)
  },

  /**
   * 设置默认模型
   */
  setDefaultModel: (id: number): Promise<void> => {
    return aiApi.put<void>(`/admin/models/${id}/default`)
  },

  /**
   * 切换模型启用状态
   */
  toggleEnabled: (id: number, enabled: boolean): Promise<void> => {
    return aiApi.put<void>(`/admin/models/${id}/toggle`, null, {
      params: { enabled }
    })
  },

  /**
   * 获取今天模型使用统计
   */
  getTodayModelUsage: (): Promise<ModelUsageStats[]> => {
    return aiApi.get<ModelUsageStats[]>('/admin/models/usage/today')
  }
}

export default aiModelsService
