import { ServiceType, getServiceBaseURL } from './serviceConfig'
import type { AiChatRequest } from './aiTypes'
import type { ArticleResultsPayload } from './ai'
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

  /**
   * 发起流式聊天请求
   *
   * 每次调用只提交一次 POST。服务端尚无续传/幂等协议，断线后由用户手动重试，
   * 保留已收到的正文，避免重复落库、模型调用和内容拼接。
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
    this.cancel()
    const controller = new AbortController()
    this.abortController = controller
    let isCompleted = false
    let serverError = false

    try {
      const token = getToken()
      const aiBaseUrl = getServiceBaseURL(ServiceType.AI)
      const { chatType, ...requestBody } = request
      const streamUrl = chatType === 'writing' ? `${aiBaseUrl}/writing/stream` : `${aiBaseUrl}/chat/stream`

      const response = await fetch(streamUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(requestBody),
        signal: controller.signal
      })

      if (!response.ok) {
        let message = `请求失败（HTTP ${response.status}），请稍后重试`
        try {
          const body = await response.json()
          if (typeof body?.message === 'string' && body.message) message = body.message
        } catch {
          // 网关可能返回非 JSON 响应，保留状态码兜底文案。
        }
        throw new StreamError(message, 'HTTP_ERROR', response.status)
      }
      if (!response.body) {
        throw new StreamError('无法读取响应流，请重试', 'STREAM_READ_ERROR')
      }

      await readSseStream(response.body, {
        onEvent: (event: ParsedSseEvent) => {
          if (controller.signal.aborted || serverError) return
          this.dispatchEvent(event, onChunk, onEvent, (payload) => {
            isCompleted = true
            onComplete?.(payload)
          }, (error) => {
            serverError = true
            onError?.(error)
          })
        },
        onParseError: (rawData: string, error: unknown) => {
          console.error('忽略无法解析的 SSE 事件:', error, rawData)
        }
      })

      if (!isCompleted && !serverError && !controller.signal.aborted) {
        onError?.(new StreamError('连接中断，已保留收到的内容，请重试', 'STREAM_INCOMPLETE'))
      }
    } catch (error: unknown) {
      if (controller.signal.aborted || isCompleted || serverError) return
      onError?.(error instanceof StreamError
        ? error
        : new StreamError('连接中断，已保留收到的内容，请重试', 'STREAM_ERROR'))
    } finally {
      // 旧请求结束时不能清理后来建立的新请求。
      controller.abort()
      if (this.abortController === controller) this.abortController = null
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
        const payload = parsedData as { conversationId?: number; model?: string } | null
        onEvent?.('start', { conversationId: payload?.conversationId, ...(payload?.model ? { model: payload.model } : {}) })
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
   * 取消当前流式请求，不报告连接错误。
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
