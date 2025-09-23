
import { post, get, del, ServiceType } from './api'

/**
 * AI聊天请求接口
 */
export interface AiChatRequest {
  message: string
  /** 前端上下文，便于后端提示词决策，例如 { page: 'post-detail', articleId: 123 } */
  context?: Record<string, any>
}

export interface AiChatResponse {
  success: boolean
  message: string
  userId: string
  model: string
  historyCount: number
  timestamp: number
  processingTime?: number
  responseLength?: number
  /** AI识别的情绪标签，如 happy/angry/thinking/neutral */
  emotion?: string | null
  /** 动作指令，如 open_latest_articles/favorite_article/open_home */
  action?: string | null
  /** 元数据，例如 { articleId: 123 } */
  metadata?: Record<string, any> | null
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
        const response = await get<AiChatResponse>('/ai/status', {}, {
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
        const response = await post<AiChatResponse>('/ai/chat', request, {
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
        const response = await get<ChatHistoryResponse>(`/ai/chat/history?page=${page}&size=${size}`,{}, {
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
        const response = await del<AiChatResponse>('/ai/chat/memory', {
            serviceType: ServiceType.AI
        })
        return response as unknown as AiChatResponse
    }



   
}
