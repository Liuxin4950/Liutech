import {del, get, post, ServiceType} from './api'

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

/**
 * AI聊天请求接口
 */
export interface AiChatRequest {
  message: string
  /** 前端上下文，便于后端提示词决策，例如 { page: 'post-detail', articleId: 123 } */
  context?: Record<string, any>
  conversationId?: number
  tempMessages?: Array<{
    role: 'user' | 'assistant' | 'system'
    content: string
  }>
  /** 聊天模式：normal 普通模式，stream 流式模式 */
  mode?: 'normal' | 'stream'
  /** 使用的模型，默认为空 */
  model?: string
  /** 温度参数，控制回复的随机性 */
  temperature?: number
  /** 最大token数 */
  maxTokens?: number
  /**
   * 是否启用语音推理（由前端开关决定）
   * - true：后端会尝试把流式文本分段并触发 TTS，额外推送 audio 事件
   * - false：只返回文本，不做 TTS 推理
   */
  ttsEnabled?: boolean
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
 * 推荐请求接口
 */
export interface RecommendRequest {
    /** 推荐类型: search, category, latest, hot */
    type: string
    /** 搜索关键词 (type=search时使用) */
    keyword?: string
    /** 分类ID (type=category时使用) */
    categoryId?: number
    /** 返回数量限制 */
    limit?: number
}

/**
 * 推荐响应接口
 */
export interface RecommendResponse {
    /** 推荐类型 */
    type: string
    /** 搜索关键词 */
    keyword?: string
    /** 分类信息 */
    category?: CategoryDTO
    /** 推荐的文章列表 */
    posts: PostSummaryDTO[]
    /** 推荐理由 */
    reason: string
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
        // post 返回的已是服务端响应体，AI服务为 {success, message, ...}
        const response = await post<AiChatResponse>('/chat', request, {
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
        // 根据模式选择不同的端点
        const endpoint = mode === 'stream' ? '/chat/stream' : '/chat'

        // 添加模式参数到请求体
        const requestWithMode = {
            ...request,
            mode: mode
        }

        if (mode === 'stream') {
            // 流式模式应该使用 AiStream 服务
            throw new Error('流式模式请使用 AiStream.streamChat 方法')
        }

        const response = await post<AiChatResponse>(endpoint, requestWithMode, {
            serviceType: ServiceType.AI
        })
        return response as unknown as AiChatResponse
    }

    /**
     * 获取推荐内容
     * 使用AI服务8081端口
     */
    static async recommend(request: RecommendRequest): Promise<RecommendResponse> {
        const response = await post<RecommendResponse>('/recommend', request, {
            serviceType: ServiceType.AI
        })
        return response as unknown as RecommendResponse
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
