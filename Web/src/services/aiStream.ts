import { get, ServiceType } from './api'
import type { AiChatRequest } from './ai'
import { getServiceBaseURL } from '@/config/services'

/**
 * SSE Envelope 格式版本。
 * 用于前端判断如何解析 payload。
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

/**
 * data 事件 payload。
 */
interface DataPayload {
  content: string
}

/**
 * article-results 事件 payload。
 */
interface ArticleResultsPayload {
  source: string
  query: string
  reason: string
  items: ArticleResultItem[]
}

/**
 * 文章结果项。
 */
interface ArticleResultItem {
  id: number
  title: string
  summary: string
  status: string
  url: string
  adminUrl: string
  reason?: string
  source?: string
}

/**
 * confirmation-required 事件 payload。
 */
interface ConfirmationPayload {
  actionId: number
  actionType: string
  title: string
  description: string
  preview: any
  riskLevel: string
}

/**
 * error 事件 payload。
 */
interface ErrorPayload {
  code: string
  message: string
  stage: string
}

/**
 * complete 事件 payload。
 */
interface CompletePayload {
  taskId: number
  conversationId: number
}

/**
 * agent-start 事件 payload。
 */
interface AgentStartPayload {
  taskId: number
  conversationId: number
  intent: string
  role: string
  capabilities: string[]
}

/**
 * tool-start/tool-result 事件 payload。
 */
interface ToolEventPayload {
  toolName: string
  displayName: string
  inputSummary?: string
  success?: boolean
  durationMs?: number
  resultSummary?: string
  errorMessage?: string
}

/**
 * SSE Streaming Error
 */
export class StreamError extends Error {
  code?: string
  status?: number

  constructor(
    message: string,
    code?: string,
    status?: number
  ) {
    super(message)
    this.name = 'StreamError'
    this.code = code
    this.status = status
  }
}

/**
 * AI流式聊天服务
 *
 * 作者：刘鑫
 * 时间：2025-01-27
 * 功能：处理服务器发送事件(SSE)流式聊天
 *
 * 2026-05-01: 升级为统一 envelope 格式，支持 contractVersion。
 * 旧格式 payload 仍然兼容（用于显式关闭 Agent 的遗留 /chat/stream）。
 */
export class AiStream {
  // AbortController用于取消请求
  static abortController: AbortController | null = null

  /**
   * 发起流式聊天请求
   *
   * @param request 聊天请求
   * @param onChunk 接收到内容块时的回调
   * @param onComplete 流完成时的回调
   * @param onError 错误发生时的回调
   * @returns Promise<void>
   */
  static async streamChat(
    request: AiChatRequest,
    onChunk: (content: string) => void,
    onEvent?: (eventType: string, payload: any) => void,
    onComplete?: (response: any) => void,
    onError?: (error: StreamError) => void
  ): Promise<void> {
    try {
      // 清理之前的连接
      this.cleanup()

      // 创建新的AbortController
      this.abortController = new AbortController()

      const token = localStorage.getItem('token')

      // 构建请求URL
      const aiBaseUrl = getServiceBaseURL(ServiceType.AI)
      // agentEnabled === false 只作为内部故障/开发回退，不是用户可见的聊天模式。
      const useAgent = request.agentEnabled !== false
      const streamUrl = useAgent ? `${aiBaseUrl}/agent/stream` : `${aiBaseUrl}/chat/stream`
      const { agentEnabled, adminAgent, ...requestBody } = request

      // 由于EventSource不支持自定义请求头和POST方法，
      // 我们使用fetch流式读取作为替代方案
      const response = await fetch(
        useAgent && adminAgent ? `${aiBaseUrl}/admin/agent/stream` : streamUrl,
        {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'Cache-Control': 'no-cache',
          'Connection': 'keep-alive',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {})
        },
        body: JSON.stringify({
          ...requestBody,
          mode: 'stream'
        }),
        signal: this.abortController.signal
      })

      if (!response.ok) {
        throw new StreamError(
          `HTTP ${response.status}: ${response.statusText}`,
          'HTTP_ERROR',
          response.status
        )
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()

      if (!reader) {
        throw new StreamError('无法读取响应流', 'STREAM_READ_ERROR')
      }

      let buffer = ''

      // 读取流
      while (true) {
        const { done, value } = await reader.read()

        if (done) {
          break
        }

        // 解码数据块
        const chunk = decoder.decode(value, { stream: true })
        buffer += chunk

        // 处理SSE事件
        const events = buffer.split('\n\n')
        buffer = events.pop() || '' // 保留最后一个可能不完整的事件

        for (const event of events) {
          if (event.trim()) {
            this.handleSSEEvent(event, onChunk, onEvent, onComplete, onError)
          }
        }
      }

      // 处理剩余的buffer
      if (buffer.trim()) {
        this.handleSSEEvent(buffer, onChunk, onEvent, onComplete, onError)
      }

    } catch (error: any) {
      if (error.name === 'AbortError') {
        return
      }

      const streamError = error instanceof StreamError
        ? error
        : new StreamError(error.message || '流式请求失败', 'STREAM_ERROR')

      onError?.(streamError)
    } finally {
      this.cleanup()
    }
  }

  /**
   * 处理SSE事件
   *
   * 支持两种格式：
   * 1. 新版 envelope 格式（contractVersion=1）：
   *    { contractVersion, event, taskId, conversationId, timestamp, payload }
   * 2. 旧版裸 payload 格式（用于显式关闭 Agent 的遗留 /chat/stream）
   */
  static handleSSEEvent(
    eventText: string,
    onChunk: (content: string) => void,
    onEvent?: (eventType: string, payload: any) => void,
    onComplete?: (response: any) => void,
    onError?: (error: StreamError) => void
  ): void {
    try {
      const lines = eventText.split('\n')
      let eventType = ''
      const dataLines: string[] = []

      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.substring(5).trim())
        }
      }

      const data = dataLines.join('\n')

      if (!data) return

      // 解析数据
      let parsedData = JSON.parse(data)

      // 检查是否为新版 envelope 格式
      if (parsedData && parsedData.contractVersion === CONTRACT_VERSION) {
        // 新版 envelope 格式
        const envelope = parsedData as SseEnvelope
        eventType = envelope.event
        parsedData = envelope.payload
      }
      // else: 旧版裸 payload 格式，保持原样处理

      switch (eventType) {
        case 'start':
          break

        case 'data': {
          // data 事件：提取 content 字段
          const payload = parsedData as DataPayload
          if (payload && payload.content) {
            onChunk(payload.content)
          } else if (typeof parsedData === 'string') {
            // 兼容旧格式
            onChunk(parsedData)
          }
          break
        }

        case 'audio':
        case 'audio-skip':
        case 'audio-complete':
        case 'avatar-cue':
        case 'heartbeat':
          // 音频事件，直接透传
          onEvent?.(eventType, parsedData)
          break

        case 'agent-start': {
          const payload = parsedData as AgentStartPayload
          onEvent?.(eventType, payload)
          break
        }

        case 'agent-plan':
        case 'article-results': {
          const payload = parsedData as ArticleResultsPayload
          onEvent?.(eventType, payload)
          break
        }

        case 'confirmation-required': {
          const payload = parsedData as ConfirmationPayload
          onEvent?.(eventType, payload)
          break
        }

        case 'action-result':
          onEvent?.(eventType, parsedData)
          break

        case 'tool-start':
        case 'tool-result': {
          const payload = parsedData as ToolEventPayload
          onEvent?.(eventType, payload)
          break
        }

        case 'complete': {
          const payload = parsedData as CompletePayload
          onComplete?.(payload)
          break
        }

        case 'error': {
          const payload = parsedData as ErrorPayload
          console.error('流式响应错误:', payload)
          onError?.(new StreamError(
            payload?.message || '流式响应发生错误',
            payload?.code || 'STREAM_EVENT_ERROR'
          ))
          break
        }

        default:
          // 如果没有事件类型，可能是直接的内容（旧格式兼容）
          if (typeof parsedData === 'string') {
            onChunk(parsedData)
          } else if (parsedData && parsedData.content) {
            onChunk(parsedData.content)
          } else if (parsedData && parsedData.error) {
            // 旧格式错误
            onError?.(new StreamError(
              parsedData.error,
              'STREAM_EVENT_ERROR'
            ))
          }
      }
    } catch (error: any) {
      console.error('处理SSE事件失败:', error)
      onError?.(new StreamError(
        `SSE事件解析失败: ${error.message}`,
        'SSE_PARSE_ERROR'
      ))
    }
  }

  /**
   * 取消当前流式请求
   */
  static cancel(): void {
    this.cleanup()
  }

  /**
   * 清理资源
   */
  static cleanup(): void {
    if (this.abortController) {
      this.abortController.abort()
      this.abortController = null
    }
  }

  /**
   * 检查是否正在连接
   */
  static get isStreaming(): boolean {
    return this.abortController !== null
  }
}
