import type {
  AgentChatRequest,
  AgentCompletePayload,
  AgentErrorPayload,
  ArticleResultsPayload,
  ArticleResultItem,
  ToolEventPayload,
  DataPayload,
  FieldUpdatePayload,
} from '../types/agent'
import { getAiBaseUrl } from './aiClient'

const getToken = () => localStorage.getItem('token')

/**
 * SSE Envelope 格式版本。
 */
const CONTRACT_VERSION = 1

/**
 * SSE Envelope 根结构。
 */
interface SseEnvelope<T = unknown> {
  contractVersion: number
  event: string
  taskId: number
  conversationId: number
  timestamp: string
  payload: T
}

export interface AgentStreamHandlers {
  onStart?: () => void
  onData?: (content: string) => void
  onArticles?: (items: ArticleResultItem[], payload: ArticleResultsPayload) => void
  onToolStart?: (payload: ToolEventPayload) => void
  onToolResult?: (payload: ToolEventPayload) => void
  onFieldUpdate?: (payload: FieldUpdatePayload) => void
  onError?: (message: string, code?: string) => void
  onComplete?: (payload: AgentCompletePayload) => void
}

function isSseEnvelope(value: unknown): value is SseEnvelope {
  return typeof value === 'object' && value !== null
    && 'contractVersion' in value && (value as SseEnvelope).contractVersion === CONTRACT_VERSION
}

export class AgentService {
  static async stream(request: AgentChatRequest, handlers: AgentStreamHandlers) {
    const token = getToken()
    const response = await fetch(`${getAiBaseUrl()}/writing/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(request),
    })

    if (!response.ok || !response.body) {
      if (response.status === 429) {
        throw new Error('请求过于频繁，请稍后再试')
      }
      if (response.status === 403) {
        throw new Error('当前身份不能执行该操作')
      }
      // 读取后端返回的 JSON 错误信息（参数校验 400 等会带具体原因，如长度超限），
      // 读不到时退回状态码提示，避免用户只看到"连接中断"。
      let serverMessage = ''
      try {
        const errorBody = await response.json()
        serverMessage = errorBody?.message || ''
      } catch {
        // 响应体不是 JSON（如网关错误页），忽略走状态码兜底
      }
      throw new Error(serverMessage || `Agent 请求失败：${response.status}`)
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
        this.handleEvent(event, handlers, { complete: () => { receivedComplete = true }, error: () => { receivedError = true } })
      }
    }
    if (buffer.trim()) this.handleEvent(buffer, handlers, { complete: () => { receivedComplete = true }, error: () => { receivedError = true } })

    if (!receivedComplete && !receivedError) {
      handlers.onError?.('连接中断，请重试')
      handlers.onComplete?.({} as AgentCompletePayload)
    }
  }

  /**
   * 处理 SSE 事件。
   *
   * 支持两种格式：
   * 1. 新版 envelope 格式（contractVersion=1）
   * 2. 旧版裸 payload 格式
   *
   * 事件集合与后端 StreamingChatService 实际下发严格对齐：
   * start/data/heartbeat/avatar-cue/audio/audio-skip/field-update/tool-start/
   * tool-result/article-results/complete/audio-complete/error。
   * 本服务只消费写作助手需要的事件（data/article-results/tool-start/tool-result/
   * field-update/complete/error），heartbeat/avatar-cue/audio 由看板娘侧处理。
   */
  private static handleEvent(eventText: string, handlers: AgentStreamHandlers, flags?: { complete?: () => void; error?: () => void }) {
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

    // 检查是否为新版 envelope 格式
    if (isSseEnvelope(payload)) {
      eventType = payload.event
      payload = payload.payload
    }

    // 根据 eventType 分发到对应 handler
    switch (eventType) {
      case 'start':
        handlers.onStart?.()
        break

      case 'data': {
        const dataPayload = payload as DataPayload
        handlers.onData?.(dataPayload?.content || '')
        break
      }

      case 'article-results': {
        const articlePayload = payload as ArticleResultsPayload
        handlers.onArticles?.(articlePayload.items || [], articlePayload)
        break
      }

      case 'tool-start':
        handlers.onToolStart?.(payload as ToolEventPayload)
        break

      case 'tool-result':
        handlers.onToolResult?.(payload as ToolEventPayload)
        break

      case 'field-update':
        handlers.onFieldUpdate?.(payload as FieldUpdatePayload)
        break

      case 'error': {
        const errorPayload = payload as AgentErrorPayload
        handlers.onError?.(errorPayload?.message || 'Agent 执行失败', errorPayload?.code)
        flags?.error?.()
        break
      }

      case 'complete':
        handlers.onComplete?.(payload as AgentCompletePayload)
        flags?.complete?.()
        break
    }
  }
}

export default AgentService
