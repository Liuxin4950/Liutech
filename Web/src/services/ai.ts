
import { post, get, ServiceType } from './api'

/**
 * AI聊天请求接口
 */
export interface AiChatRequest {
    message: string
    model?: string
}

/**
 * AI聊天响应接口
 */
export interface AiChatResponse {
    success: boolean
    message: string
    userId?: number
    model?: string
    historyCount?: number
    timestamp?: number
    processingTime?: number
    responseLength?: number
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
        // post 返回的已是服务端响应体，AI服务为 {success, message, ...}
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



   
}
