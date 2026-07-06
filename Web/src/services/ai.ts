import {post, ServiceType} from './api'
import type { AiChatRequest } from './ai-types'

// 重新导出共享类型，保持向后兼容
export type { AiChatRequest } from './ai-types'

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
  articleResults?: ArticleResultsPayload
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
        return response.data
    }
}

// 导出流式聊天服务
export { AiStream, StreamError } from './aiStream'
