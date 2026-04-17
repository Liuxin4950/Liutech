import { get, ServiceType } from './api'
import type { AiChatRequest } from './ai'
import { getServiceBaseURL } from '@/config/services'

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
      const streamUrl = `${aiBaseUrl}/chat/stream`

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
          ...request,
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
      const parsedData = JSON.parse(data)

      switch (eventType) {
        case 'start':
          break

        case 'data':
          if (parsedData.content) {
            onChunk(parsedData.content)
          }
          break

        case 'audio':
        case 'audio-skip':
        case 'audio-complete':
        case 'heartbeat':
          onEvent?.(eventType, parsedData)
          break

        case 'complete':
          onComplete?.(parsedData)
          break

        case 'error':
          console.error('流式响应错误:', parsedData)
          onError?.(new StreamError(
            parsedData.error || '流式响应发生错误',
            'STREAM_EVENT_ERROR'
          ))
          break

        default:
          // 如果没有事件类型，可能是直接的内容
          if (typeof parsedData === 'string') {
            onChunk(parsedData)
          } else if (parsedData.content) {
            onChunk(parsedData.content)
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
