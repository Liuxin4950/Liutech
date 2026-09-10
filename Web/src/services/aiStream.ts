import { ServiceType } from './api'
import type { AiChatRequest } from './aiTypes'
import type { ArticleResultsPayload } from './ai'
import { getServiceBaseURL } from '@/services/serviceConfig'
import { getToken } from '@/utils/auth'
import { parseSseEventText, readSseStream } from './sse'
import type { ParsedSseEvent } from './sse'

/**
 * data 事件 payload。
 */
interface DataPayload {
  content: string
  conversationId?: number
}

/**
 * error 事件 payload。
 *
 * 后端有两条下发路径，文案键名不同，两者都要认：
 * - `SseEmitterHelper.safeSendError` → `{ conversationId, error }`
 * - 其它业务异常路径 → `{ code, message, stage }`
 */
interface ErrorPayload {
  code?: string
  message?: string
  error?: string
  stage?: string
}

/**
 * complete 事件 payload。
 */
interface CompletePayload {
  taskId?: number
  conversationId?: number
  responseLength?: number
  mode?: string
  ttsEnabled?: boolean
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

      const token = getToken()

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

      if (!response.body) {
        throw new StreamError('无法读取响应流', 'STREAM_READ_ERROR')
      }

      // 读取流：切帧与 JSON 解析统一由 services/sse.ts 负责（与 Admin 同一实现）
      await readSseStream(response.body, {
        onEvent: (event: ParsedSseEvent) => {
          this.dispatchEvent(event, onChunk, wrappedOnEvent, wrappedOnComplete, wrappedOnError)
        },
        onParseError: (rawData: string, error: unknown) => {
          // 单帧坏掉只跳过该帧并留痕：不能因为一条脏数据就把整轮对话判死
          console.error('忽略无法解析的 SSE 事件:', error, rawData)
        }
      })
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
   * 处理单帧 SSE 文本（内部逐帧调用，也便于单测直接喂帧）
   *
   * 解析交给 services/sse.ts（与 Admin 同一实现），这里只负责把解析结果
   * 分发到看板娘链路的回调。支持两种负载形态：
   * 1. 裸 payload（当前线上形态）：data 行就是事件数据本身；
   * 2. envelope（contractVersion=1）：解析层会剥壳后再交过来。
   */
  static handleSSEEvent(
    eventText: string,
    onChunk: (content: string) => void,
    onEvent?: (eventType: string, payload: any) => void,
    onComplete?: (response: any) => void,
    onError?: (error: StreamError) => void
  ): void {
    const outcome = parseSseEventText(eventText)

    if (outcome.status === 'empty') return

    if (outcome.status === 'invalid') {
      // 坏帧只跳过并留痕：单条脏数据不应该让整轮对话失效
      console.error('忽略无法解析的 SSE 事件:', outcome.error, outcome.rawData)
      return
    }

    this.dispatchEvent(outcome.event, onChunk, onEvent, onComplete, onError)
  }

  /**
   * 分发看板娘链路的聊天事件
   *
   * 事件集合与后端 StreamingChatService 对齐：
   * start / data / heartbeat / avatar-cue / audio / audio-skip / audio-complete /
   * article-results / complete / error。
   * 写作助手相关事件（tool-start / field-update 等）由 services/writingStream.ts 处理。
   *
   * @param event 解析后的事件（payload 已剥掉 envelope）
   * @param onChunk 内容分片回调
   * @param onEvent 透传事件回调
   * @param onComplete 完成回调
   * @param onError 错误回调
   */
  private static dispatchEvent(
    event: ParsedSseEvent,
    onChunk: (content: string) => void,
    onEvent?: (eventType: string, payload: any) => void,
    onComplete?: (response: any) => void,
    onError?: (error: StreamError) => void
  ): void {
    const eventType = event.event
    const parsedData = event.payload

    switch (eventType) {
      case 'start': {
        // 首事件携带 conversationId，立即通知上层更新 store。
        // 后端当前下发裸 payload，conversationId 就在 payload 顶层；
        // envelope 形态下解析层已把 payload 剥出来，这里同样取得到。
        const payload = parsedData as { conversationId?: number } | null
        onEvent?.('start', { conversationId: payload?.conversationId })
        break
      }

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
        // 音频与表情事件，直接透传
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
        // 后端 SseEmitterHelper.safeSendError 把面向用户的文案放在 error 键，
        // 其它路径可能用 message；两个键都读，避免用户只看到兜底文案。
        onError?.(new StreamError(
          payload?.message || payload?.error || '流式响应发生错误',
          payload?.code || 'STREAM_EVENT_ERROR'
        ))
        break
      }

      default:
        // 如果没有事件类型，可能是直接的内容（旧格式兼容）
        if (typeof parsedData === 'string') {
          onChunk(parsedData)
        } else if (parsedData && (parsedData as DataPayload).content) {
          onChunk((parsedData as DataPayload).content)
        } else if (parsedData && (parsedData as ErrorPayload).error) {
          // 旧格式错误
          onError?.(new StreamError(
            (parsedData as ErrorPayload).error as string,
            'STREAM_EVENT_ERROR'
          ))
        }
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
