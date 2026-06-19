import {del, get, post, ServiceType} from './api'
import type { AiChatRequest } from './ai-types'

// 重新导出共享类型，保持向后兼容
export type { AiChatRequest } from './ai-types'

/**
 * AI模型配置接口
 */
export interface AiModelConfig {
  id: number
  modelName: string
  displayName: string
  provider: string
  isEnabled: boolean
  isDefault: boolean
  maxTokens?: number
  temperature?: number
  description?: string
  sortOrder?: number
}

export interface AiChatResponse {
  success: boolean
  message: string
  code?: number
  /** AI识别的情绪标签，如 happy/angry/thinking/neutral */
  emotion?: string | null
  /** 动作指令，如 open_latest_articles/favorite_article/open_home */
  action?: string | null
  /** 模型名称 */
  model?: string
  /** 处理时间（毫秒） */
  processingTime?: number
  /** 响应长度 */
  responseLength?: number
  /** 对话ID */
  conversationId?: number
  /** 会话模式 */
  mode?: 'guest' | 'user'
  role?: 'guest' | 'user' | 'admin' | string
  authenticated?: boolean
  admin?: boolean
  capabilities?: string[]
  intent?: string
  plan?: AgentPlanStep[]
  articleResults?: ArticleResultsPayload
}

export interface AgentPlanStep {
    key: string
    title: string
    status: string
}

/**
 * 聊天消息实体
 */
export interface AiChatMessage {
    id: number
    userId: string
    role: 'user' | 'assistant' | 'system'
    content: string
    model?: string
    tokens?: number
    metadata?: string
    status: number
    createdAt: string
    updatedAt: string
}

/**
 * 聊天历史记录响应接口
 */
export interface ChatHistoryResponse {
    success: boolean
    message?: string
    data?: AiChatMessage[]
    page: number
    size: number
    total: number
    totalPages: number
    userId?: string
    timestamp: number
}

/**
 * 分类DTO
 */
export interface CategoryDTO {
    id: number
    name: string
    description?: string
    postCount: number
}

/**
 * 文章摘要DTO
 */
export interface PostSummaryDTO {
    id: number
    title: string
    summary?: string
    categoryName?: string
    authorName?: string
    tags?: string[]
    viewCount: number
    likeCount: number
    createdAt?: string
    /** 前端文章详情跳转地址，后端保证搜索/推荐结果携带 */
    url?: string
    /** 管理端跳转地址，管理员场景可用 */
    adminUrl?: string
    /** 文章状态，公开推荐通常为 published */
    status?: string
    /** Agent 推荐或搜索原因 */
    reason?: string
    /** Agent 结果来源：search/latest/hot/recommend 等 */
    source?: string
}

export interface ArticleResultsPayload {
    source?: string
    query?: string
    reason?: string
    items: PostSummaryDTO[]
}


/**
 * AI服务类
 * 使用AI服务专用端口8081
 *
 * 作者：刘鑫
 * 时间：2025-01-27
 */
export class Ai {
    /**
     * AI服务可用测试
     * 使用AI服务8081端口
     */
    static async chatStatus(): Promise<AiChatResponse> {
        // get 返回的已是服务端响应体，AI服务为 {success, message, ...}
        const response = await get<AiChatResponse>('/status', {}, {
            serviceType: ServiceType.AI
        })
        return response as unknown as AiChatResponse
    }
    /**
     * 普通聊天请求
     * 使用AI服务8081端口
     */
    static async chat(request: AiChatRequest): Promise<AiChatResponse> {
        const { chatType, ...requestBody } = request
        // 看板娘聊天走 /ai/chat，写作助手走 /ai/writing
        const endpoint = chatType === 'writing' ? '/writing' : '/chat'
        const response = await post<AiChatResponse>(endpoint, requestBody, {
            serviceType: ServiceType.AI
        })
        return response as unknown as AiChatResponse
    }
     /**
     * 聊天历史请求
     * 使用AI服务8081端口
     */
    static async chatHistory(page: number = 1, size: number = 20): Promise<ChatHistoryResponse> {
        // get 返回的已是服务端响应体，AI服务为 {success, message, ...}
         const response = await get<ChatHistoryResponse>(`/chat/history?page=${page}&size=${size}`, {}, {
            serviceType: ServiceType.AI
        })
        return response as unknown as ChatHistoryResponse
    }

    /**
     * 清空聊天记忆
     * 使用AI服务8081端口
     */
    static async clearChatMemory(): Promise<AiChatResponse> {
        // del 返回的已是服务端响应体，AI服务为 {success, message, ...}
        const response = await del<AiChatResponse>('/chat/memory', {
            serviceType: ServiceType.AI
        })
        return response as unknown as AiChatResponse
    }

    /**
     * 发送聊天消息（支持模式选择）
     * @param request 聊天请求
     * @param mode 聊天模式，默认为 normal
     * @returns Promise<AiChatResponse>
     */
    static async sendMessage(
        request: AiChatRequest,
        mode: 'normal' | 'stream' = 'normal'
    ): Promise<AiChatResponse> {
        // 添加模式参数到请求体
        const requestWithMode = {
            ...request,
            mode: mode
        }

        if (mode === 'stream') {
            // 实时响应应该使用 AiStream 服务
            throw new Error('实时响应请使用 AiStream.streamChat 方法')
        }

        const { chatType, ...requestBody } = requestWithMode
        // chatType='writing' 走写作助手接口，其他走看板娘接口
        const endpoint = chatType === 'writing' ? '/writing' : '/chat'
        const response = await post<AiChatResponse>(endpoint, requestBody, {
            serviceType: ServiceType.AI
        })
        return response as unknown as AiChatResponse
    }

    /**
     * 获取默认模型
     * 使用AI服务8081端口，无需认证
     */
    static async getDefaultModel(): Promise<string> {
        const response = await get<string>('/models/default', {}, {
            serviceType: ServiceType.AI
        })
        return response as unknown as string
    }

    /**
     * 获取所有启用的模型列表
     * 使用AI服务8081端口，无需认证
     */
    static async getEnabledModels(): Promise<AiModelConfig[]> {
        const response = await get<AiModelConfig[]>('/models/enabled', {}, {
            serviceType: ServiceType.AI
        })
        return response as unknown as AiModelConfig[]
    }
}

// 导出流式聊天服务
export { AiStream, StreamError } from './aiStream'
