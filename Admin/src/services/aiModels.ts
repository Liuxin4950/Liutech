import { aiApi } from './aiClient'

/**
 * 模型配置接口（后端返回的完整配置 + 只读生效值）
 */
export interface ModelConfig {
  id: number
  modelName: string
  displayName: string
  provider: string
  isEnabled: boolean
  isDefault: boolean
  sortOrder: number
  /** 单次回答的输出上限（token），即调用大模型时传的 max_tokens */
  maxTokens?: number
  /**
   * 模型上下文窗口（token）：一次请求里「输入 + 输出」的总上限。
   * 每个模型自己就有固定窗口大小，配大了请求会直接失败。
   */
  contextWindow?: number
  /** 采样温度，越大回答越发散 */
  temperature?: number
  description?: string
  /**
   * 只读生效值：服务端最终采用的输出上限。
   * 管理员配置的 maxTokens 会被全局安全上限夹小，这里给的是夹取之后的值。
   */
  effectiveMaxTokens?: number
  /**
   * 只读生效值：服务端最终采用的上下文窗口。
   * 未配置时是服务端默认值，所以它基本一定有值，而 contextWindow 可能为空。
   */
  effectiveContextWindow?: number
  /**
   * 只读生效值：输入预算（token）。
   * 计算方式：有效上下文 − 有效输出 − 安全余量。
   * 它才是真正决定「一次能塞多少条历史消息 / 多长的文章正文」的数字：
   * 上下文窗口配得大、输出上限也配得大时，留给输入的空间反而会变小。
   * 后端未提供该字段时可能为 undefined，前端需容错。
   */
  inputBudgetTokens?: number
  /**
   * 只读标记：输出上限是否被全局安全上限夹小。
   * true 表示配置值大于 spring.ai.security.model-policy-max-tokens-ceiling，
   * 服务端会按 effectiveMaxTokens 跑 —— 界面必须提示，不能静默。
   */
  outputClamped?: boolean
  /**
   * 只读标记：输入预算是否被全局成本护栏夹小。
   * true 表示模型上下文窗口大于 spring.ai.security.model-policy-max-input-tokens，
   * 即"模型能装 205K，但单次请求只允许用 96K 输入"。
   */
  inputCappedByPolicy?: boolean
}

/**
 * 模型配置请求接口（新增 / 编辑时提交的字段）
 */
export interface ModelConfigRequest {
  modelName: string
  displayName: string
  provider: string
  isEnabled: boolean
  sortOrder?: number
  /** 单次回答的输出上限（token）；不填由服务端给默认值，且仍受全局安全上限约束 */
  maxTokens?: number
  /** 模型上下文窗口（输入 + 输出总 token 上限）；不填由服务端给默认值 */
  contextWindow?: number
  /** 采样温度，越大回答越发散 */
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
