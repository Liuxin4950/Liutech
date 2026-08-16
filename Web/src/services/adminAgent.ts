import { getServiceBaseURL, ServiceType } from '@/services/serviceConfig'
import type { ArticleResultsPayload, PostSummaryDTO } from './ai'

export interface AdminArticleDraftSnapshot {
  postId?: number | null
  title?: string
  content?: string
  summary?: string
  categoryId?: number | string
  tagIds?: number[]
  status?: string
  coverImage?: string
  thumbnail?: string
}

export interface AgentPlanStep {
  key: string
  title: string
  status: string
}

export interface ToolEventPayload {
  toolName: string
  displayName: string
  inputSummary?: string
  success?: boolean
  durationMs?: number
  resultSummary?: string
  errorMessage?: string
}

export interface FieldUpdatePayload {
  title?: string
  summary?: string
  contentHtml?: string
  categoryId?: number
  categoryName?: string
  tagIds?: number[]
  tagNames?: string[]
  suggestedCategoryName?: string
  suggestedTagNames?: string[]
}

export interface TempMessage {
  role: string
  content: string
}

export interface AdminAgentRequest {
  message: string
  conversationId?: number
  context?: Record<string, unknown>
  draft?: AdminArticleDraftSnapshot
  tempMessages?: TempMessage[]
}

export interface AdminAgentHandlers {
  onStart?: () => void
  onData?: (content: string) => void
  onArticles?: (items: PostSummaryDTO[], payload: ArticleResultsPayload) => void
  onToolStart?: (payload: ToolEventPayload) => void
  onToolResult?: (payload: ToolEventPayload) => void
  onFieldUpdate?: (payload: FieldUpdatePayload) => void
  onComplete?: () => void
  onError?: (message: string) => void
}

interface SseEnvelope<T = unknown> {
  contractVersion: number
  event: string
  payload: T
}

const CONTRACT_VERSION = 1

/** 将 SSE payload 安全转换为目标类型（服务端 contract 保证结构一致） */
const asPayload = <T>(value: unknown): T => value as T

export class AdminAgentService {
  static async stream(request: AdminAgentRequest, handlers: AdminAgentHandlers) {
    const token = localStorage.getItem('token')
    const response = await fetch(`${getServiceBaseURL(ServiceType.AI)}/writing/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(request)
    })

    if (!response.ok || !response.body) {
      if (response.status === 403) throw new Error('当前身份不能使用管理员写作助手')
      throw new Error(`写作助手请求失败：${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let receivedComplete = false
    let receivedError = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const event of events) {
        if (event.trim()) this.handleEvent(event, handlers, { complete: () => { receivedComplete = true }, error: () => { receivedError = true } })
      }
    }
    if (buffer.trim()) this.handleEvent(buffer, handlers, { complete: () => { receivedComplete = true }, error: () => { receivedError = true } })

    if (!receivedComplete && !receivedError) {
      handlers.onError?.('连接中断，请重试')
      handlers.onComplete?.()
    }
  }

  private static handleEvent(eventText: string, handlers: AdminAgentHandlers, flags?: { complete?: () => void; error?: () => void }) {
    const lines = eventText.split('\n')
    let eventType = ''
    const dataLines: string[] = []
    for (const line of lines) {
      if (line.startsWith('event:')) eventType = line.substring(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.substring(5).trim())
    }
    if (!dataLines.length) return
    let payload: unknown
    try {
      payload = JSON.parse(dataLines.join('\n'))
    } catch {
      return
    }
    if ((payload as SseEnvelope)?.contractVersion === CONTRACT_VERSION) {
      const envelope = (payload as SseEnvelope).payload as Record<string, unknown>
      eventType = (payload as SseEnvelope).event
      payload = envelope
    }
    const p = payload as Record<string, unknown> | null
    switch (eventType) {
      case 'start':
        handlers.onStart?.()
        break
      case 'data':
        handlers.onData?.((p?.content as string) || '')
        break
      case 'article-results': {
        const payload = asPayload<ArticleResultsPayload>(p)
        handlers.onArticles?.(payload?.items || [], payload)
        break
      }
      case 'tool-start':
        handlers.onToolStart?.(asPayload<ToolEventPayload>(p))
        break
      case 'tool-result':
        handlers.onToolResult?.(asPayload<ToolEventPayload>(p))
        break
      case 'field-update':
        handlers.onFieldUpdate?.(asPayload<FieldUpdatePayload>(p))
        break
      case 'complete':
        handlers.onComplete?.()
        flags?.complete?.()
        break
      case 'error':
        handlers.onError?.((p?.message as string) || '写作助手执行失败')
        flags?.error?.()
        break
    }
  }
}
