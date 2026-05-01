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
} from '../types/agent'

const normalizeAiBaseUrl = () => {
  const envUrl = import.meta.env.VITE_AI_BASE_URL as string | undefined
  const raw = envUrl && envUrl.trim().length > 0 ? envUrl.trim() : 'http://127.0.0.1:8081'
  return raw.endsWith('/ai') ? raw : `${raw.replace(/\/$/, '')}/ai`
}

const getToken = () => localStorage.getItem('token')

export interface AgentStreamHandlers {
  onData?: (content: string) => void
  onPlan?: (steps: AgentPlanStep[]) => void
  onArticles?: (items: ArticleResultItem[], payload: ArticleResultsPayload) => void
  onConfirmation?: (payload: ConfirmationRequiredPayload) => void
  onActionResult?: (payload: AgentActionResult) => void
  onStart?: (payload: AgentStartPayload) => void
  onError?: (message: string) => void
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

  private static handleEvent(eventText: string, handlers: AgentStreamHandlers) {
    const lines = eventText.split('\n')
    let eventType = ''
    const dataLines: string[] = []

    for (const line of lines) {
      if (line.startsWith('event:')) eventType = line.substring(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.substring(5).trim())
    }

    if (!dataLines.length) return
    const payload = JSON.parse(dataLines.join('\n')) as
      | { content?: string; steps?: AgentPlanStep[]; message?: string }
      | ArticleResultsPayload
      | ConfirmationRequiredPayload
      | AgentActionResult
      | AgentCompletePayload
      | AgentErrorPayload

    if (eventType === 'data') handlers.onData?.(('content' in payload && payload.content) || '')
    else if (eventType === 'agent-start') handlers.onStart?.(payload as AgentStartPayload)
    else if (eventType === 'agent-plan') handlers.onPlan?.(('steps' in payload && payload.steps) || [])
    else if (eventType === 'article-results') {
      const articlePayload = payload as ArticleResultsPayload
      handlers.onArticles?.(articlePayload.items || [], articlePayload)
    } else if (eventType === 'confirmation-required') handlers.onConfirmation?.(payload as ConfirmationRequiredPayload)
    else if (eventType === 'action-result') handlers.onActionResult?.(payload as AgentActionResult)
    else if (eventType === 'error') handlers.onError?.(('message' in payload && payload.message) || 'Agent 执行失败')
    else if (eventType === 'complete') handlers.onComplete?.(payload as AgentCompletePayload)
  }
}

export default AgentService
