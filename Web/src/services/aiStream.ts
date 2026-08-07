import { ServiceType } from './api'
import type { AiChatRequest } from './aiTypes'
import type { ArticleResultsPayload } from './ai'
import { getServiceBaseURL } from '@/services/serviceConfig'

/**
 * SSE Envelope 格式版本。
 * 用于前端判断如何解析 payload。
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

/**
 * data 事件 payload。
 */
interface DataPayload {
  content: string
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
  // 用户主动取消标志（区别于网络中断，避免触发重连）
  private static userCancelled = false
  // 重连等待定时器
  private static retryTimer: ReturnType<typeof setTimeout> | null = null
  // 重连等待 Promise 的 resolve（供 cancel 立即解除等待）
  private static retryResolve: (() => void) | null = null

  /**
   * 最大重连次数
   */
  private static readonly MAX_RETRIES = 4

  /**
   * 最大重连间隔（毫秒）
   */
  private static readonly MAX_DELAY_MS = 30000

  /**
   * 发起流式聊天请求
   *
   * 支持断线重连：当流异常中断（非用户取消、非服务端 error、非正常 complete）时，
   * 按指数退避（1s/2s/4s/8s，上限 30s）自动重连，最多 4 次。
   * 重连时携带原 conversationId 和最后收到的 seq，避免重复。
   *
   * @param request 聊天请求
   * @param onChunk 接收到内容块时的回调
   * @param onEvent SSE 事件回调
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
    // 重连状态
    this.userCancelled = false
    let isCompleted = false
    let serverError = false
    let currentConversationId = request.conversationId
    let lastSeq: number | undefined
    let retryCount = 0

    // 包装 onEvent：拦截 start 事件更新 conversationId，拦截带 seq 的事件记录 lastSeq
    const wrappedOnEvent = (eventType: string, payload: any) => {
      if (eventType === 'start' && payload?.conversationId) {
        currentConversationId = payload.conversationId
      }
      if (payload && typeof payload.seq === 'number' && payload.seq > (lastSeq ?? -1)) {
        lastSeq = payload.seq
      }
      onEvent?.(eventType, payload)
    }

    // 包装 onComplete：标记正常完成，阻止重连
    const wrappedOnComplete = (response: any) => {
      isCompleted = true
      onComplete?.(response)
    }

    // 包装 onError：标记服务端错误，阻止重连
    const wrappedOnError = (error: StreamError) => {
      serverError = true
      onError?.(error)
    }

    /**
     * 执行单次流式请求
     */
    const doStream = async (): Promise<void> => {
      // 清理之前的连接
      this.cleanup()

      // 创建新的AbortController
      this.abortController = new AbortController()

      const token = localStorage.getItem('token')

      // 构建请求URL
      const aiBaseUrl = getServiceBaseURL(ServiceType.AI)
      // 看板娘聊天走 /ai/chat/stream，写作助手走 /ai/writing/stream
      const { chatType, ...requestBody } = request
      const streamUrl = chatType === 'writing' ? `${aiBaseUrl}/writing/stream` : `${aiBaseUrl}/chat/stream`

      // 由于EventSource不支持自定义请求头和POST方法，
      // 我们使用fetch流式读取作为替代方案
      const response = await fetch(streamUrl, {
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
          // 重连时携带 conversationId 和 lastSeq，供后端去重/续传
          ...(currentConversationId ? { conversationId: currentConversationId } : {}),
          ...(lastSeq !== undefined ? { lastSeq } : {})
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
            this.handleSSEEvent(event, onChunk, wrappedOnEvent, wrappedOnComplete, wrappedOnError)
          }
        }
      }

      // 处理剩余的buffer
      if (buffer.trim()) {
        this.handleSSEEvent(buffer, onChunk, wrappedOnEvent, wrappedOnComplete, wrappedOnError)
      }
    }

    /**
     * 指数退避等待（可被 cancel 立即中断）
     */
    const retryDelay = (ms: number): Promise<void> => {
      return new Promise<void>(resolve => {
        this.retryResolve = resolve
        this.retryTimer = setTimeout(() => {
          this.retryTimer = null
          this.retryResolve = null
          resolve()
        }, ms)
      })
    }

    try {
      while (true) {
        try {
          await doStream()
        } catch (error: any) {
          // 用户主动取消 → 不重连
          if (this.userCancelled) return
          // 已正常完成或服务端报错 → 不重连
          if (isCompleted || serverError) return

          // 重连次数用尽 → 通知错误
          if (retryCount >= this.MAX_RETRIES) {
            const streamError = error instanceof StreamError
              ? error
              : new StreamError(error.message || '流式请求失败', 'STREAM_ERROR')
            onError?.(streamError)
            return
          }

          // 指数退避重连
          const delay = Math.min(1000 * Math.pow(2, retryCount), this.MAX_DELAY_MS)
          retryCount++
          console.warn(`[AiStream] 流异常中断，${delay}ms 后重连（第 ${retryCount}/${this.MAX_RETRIES} 次）`)
          await retryDelay(delay)
          if (this.userCancelled) return
          continue
        }

        // doStream 正常返回（reader.done）
        if (isCompleted || this.userCancelled || serverError) {
          return
        }

        // 流读完但未收到 complete → 异常中断，尝试重连
        if (retryCount >= this.MAX_RETRIES) {
          onError?.(new StreamError('流异常中断且重连次数用尽', 'STREAM_INCOMPLETE'))
          return
        }

        const delay = Math.min(1000 * Math.pow(2, retryCount), this.MAX_DELAY_MS)
        retryCount++
        console.warn(`[AiStream] 流未正常完成，${delay}ms 后重连（第 ${retryCount}/${this.MAX_RETRIES} 次）`)
        await retryDelay(delay)
        if (this.userCancelled) return
      }
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

      // 提取 envelope 级别的 conversationId（每个事件都携带）
      let envelopeConversationId: number | undefined

      // 检查是否为新版 envelope 格式
      if (parsedData && parsedData.contractVersion === CONTRACT_VERSION) {
        // 新版 envelope 格式
        const envelope = parsedData as SseEnvelope
        eventType = envelope.event
        parsedData = envelope.payload
        envelopeConversationId = envelope.conversationId
      }
      // else: 旧版裸 payload 格式，保持原样处理

      switch (eventType) {
        case 'start':
          // 首事件携带 conversationId，立即通知上层更新 store
          onEvent?.('start', { conversationId: envelopeConversationId })
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

        case 'article-results': {
          const payload = parsedData as ArticleResultsPayload
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
   * 取消当前流式请求（用户主动取消，不触发重连）
   */
  static cancel(): void {
    this.userCancelled = true
    // 清除重连等待定时器，立即解除阻塞
    if (this.retryTimer) {
      clearTimeout(this.retryTimer)
      this.retryTimer = null
    }
    if (this.retryResolve) {
      const resolve = this.retryResolve
      this.retryResolve = null
      resolve()
    }
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
