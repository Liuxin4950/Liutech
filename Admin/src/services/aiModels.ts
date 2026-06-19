import { get, post, put } from './api'

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

const BASE_URL = '/ai/admin/models'

const aiModelsService = {
  getModelList: async (): Promise<ModelConfig[]> => {
    const resp = await get<ModelConfig[]>(`${BASE_URL}/list`)
    return resp.data
  },

  addModel: async (data: ModelConfigRequest): Promise<ModelConfig> => {
    const resp = await post<ModelConfig>(BASE_URL, data)
    return resp.data
  },

  updateModel: async (id: number, data: ModelConfigRequest): Promise<ModelConfig> => {
    const resp = await put<ModelConfig>(`${BASE_URL}/${id}`, data)
    return resp.data
  },

  deleteModel: async (id: number): Promise<void> => {
    await put<void>(`${BASE_URL}/${id}`)
  },

  setDefaultModel: async (id: number): Promise<void> => {
    await put<void>(`${BASE_URL}/${id}/default`)
  },

  toggleEnabled: async (id: number, enabled: boolean): Promise<void> => {
    await put<void>(`${BASE_URL}/${id}/toggle`, null, { params: { enabled } })
  },
}

export default aiModelsService
