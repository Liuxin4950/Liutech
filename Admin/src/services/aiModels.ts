import { aiApi } from './aiClient'

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

// AiModelAdminController 端点(baseURL 已含 /ai 前缀)
// AI 服务 controller 直接返回原始对象/数组,不走 Result 包装
const BASE_URL = '/admin/models'

const aiModelsService = {
  getModelList: async (): Promise<ModelConfig[]> => {
    const resp = await aiApi.get<ModelConfig[]>(`${BASE_URL}/list`)
    return resp.data
  },

  addModel: async (data: ModelConfigRequest): Promise<ModelConfig> => {
    const resp = await aiApi.post<ModelConfig>(BASE_URL, data)
    return resp.data
  },

  updateModel: async (id: number, data: ModelConfigRequest): Promise<ModelConfig> => {
    const resp = await aiApi.put<ModelConfig>(`${BASE_URL}/${id}`, data)
    return resp.data
  },

  deleteModel: async (id: number): Promise<void> => {
    await aiApi.delete(`${BASE_URL}/${id}`)
  },

  setDefaultModel: async (id: number): Promise<void> => {
    await aiApi.put(`${BASE_URL}/${id}/default`)
  },

  toggleEnabled: async (id: number, enabled: boolean): Promise<void> => {
    await aiApi.put(`${BASE_URL}/${id}/toggle`, null, { params: { enabled } })
  },
}

export default aiModelsService
