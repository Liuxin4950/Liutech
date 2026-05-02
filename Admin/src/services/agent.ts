import type {
  AgentActionResult,
  AgentChatRequest,
  AgentCompletePayload,
  AgentErrorPayload,
  AgentStartPayload,
  ArticleResultsPayload,
  ArticleResultItem,
  ConfirmationRequiredPayload,
  AgentPlanStep,
  ToolEventPayload,
  DataPayload,
} from '../types/agent'

const normalizeAiBaseUrl = () => {
  const envUrl = import.meta.env.VITE_AI_BASE_URL as string | undefined
  const raw = envUrl && envUrl.trim().length > 0 ? envUrl.trim() : 'http://127.0.0.1:8081'
  return raw.endsWith('/ai') ? raw : `${raw.replace(/\/$/, '')}/ai`
}

const getToken = () => localStorage.getItem('token')

/**
 * SSE Envelope 格式版本。
 */
const CONTRACT_VERSION = 1

/**
 * SSE Envelope 根结构。
 */
interface SseEnvelope<T = any> {
  contractVersion: number
  event: string
  taskId: number
  conversationId: number
  timestamp: string
  payload: T
}

export interface AgentStreamHandlers {
  onData?: (content: string) => void
  onPlan?: (steps: AgentPlanStep[]) => void
  onArticles?: (items: ArticleResultItem[], payload: ArticleResultsPayload) => void
  onConfirmation?: (payload: ConfirmationRequiredPayload) => void
  onActionResult?: (payload: AgentActionResult) => void
  onStart?: (payload: AgentStartPayload) => void
  onToolStart?: (payload: ToolEventPayload) => void
  onToolResult?: (payload: ToolEventPayload) => void
  onError?: (message: string, code?: string) => void
  onComplete?: (payload: AgentCompletePayload) => void
}

export class AgentService {
  static async stream(request: AgentChatRequest, handlers: AgentStreamHandlers) {
    const token = getToken()
    const response = await fetch(`${normalizeAiBaseUrl()}/agent/stream`, {
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
      throw new Error(`Agent 请求失败：${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const event of events) {
        this.handleEvent(event, handlers)
      }
    }
    if (buffer.trim()) this.handleEvent(buffer, handlers)
  }

  static async confirmAction(actionId: number): Promise<AgentActionResult> {
    const token = getToken()
    const response = await fetch(`${normalizeAiBaseUrl()}/agent/actions/${actionId}/confirm`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ confirmed: true }),
    })
    if (!response.ok) {
      throw new Error(`确认操作失败：${response.status}`)
    }
    return await response.json()
  }

  /**
   * 处理 SSE 事件。
   *
   * 支持两种格式：
   * 1. 新版 envelope 格式（contractVersion=1）
   * 2. 旧版裸 payload 格式
   */
  private static handleEvent(eventText: string, handlers: AgentStreamHandlers) {
    const lines = eventText.split('\n')
    let eventType = ''
    const dataLines: string[] = []

    for (const line of lines) {
      if (line.startsWith('event:')) eventType = line.substring(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.substring(5).trim())
    }

    if (!dataLines.length) return
    let payload = JSON.parse(dataLines.join('\n'))

    // 检查是否为新版 envelope 格式
    if (payload && payload.contractVersion === CONTRACT_VERSION) {
      const envelope = payload as SseEnvelope
      eventType = envelope.event
      payload = envelope.payload
    }

    // 根据 eventType 分发到对应 handler
    switch (eventType) {
      case 'data': {
        const dataPayload = payload as DataPayload
        handlers.onData?.(dataPayload?.content || '')
        break
      }

      case 'agent-start':
        handlers.onStart?.(payload as AgentStartPayload)
        break

      case 'agent-plan': {
        const planPayload = payload as { steps?: AgentPlanStep[] }
        handlers.onPlan?.(planPayload?.steps || [])
        break
      }

      case 'article-results': {
        const articlePayload = payload as ArticleResultsPayload
        handlers.onArticles?.(articlePayload.items || [], articlePayload)
        break
      }

      case 'confirmation-required':
        handlers.onConfirmation?.(payload as ConfirmationRequiredPayload)
        break

      case 'action-result':
        handlers.onActionResult?.(payload as AgentActionResult)
        break

      case 'tool-start':
        handlers.onToolStart?.(payload as ToolEventPayload)
        break

      case 'tool-result':
        handlers.onToolResult?.(payload as ToolEventPayload)
        break

      case 'error': {
        const errorPayload = payload as AgentErrorPayload
        handlers.onError?.(errorPayload?.message || 'Agent 执行失败', errorPayload?.code)
        break
      }

      case 'complete':
        handlers.onComplete?.(payload as AgentCompletePayload)
        break
    }
  }
}

export default AgentService
