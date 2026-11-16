/**
 * SSE 空闲看门狗专项测试
 *
 * 服务端心跳 15s 一次；45s 无任何网络数据视为连接静默死亡（如移动端网络切换），
 * 必须主动结束读流并向用户报告，而不是等 TCP 超时。
 */
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { SSE_IDLE_TIMEOUT_MS, readSseStream } from '@/services/sse'
import { AiStream } from '@/services/aiStream'
import { streamWritingAssistant } from '@/services/writingStream'

vi.mock('@/utils/auth', () => ({ getToken: () => null }))
vi.mock('@/services/serviceConfig', () => ({
  ServiceType: { AI: 'ai' },
  getServiceBaseURL: () => '/ai'
}))

/** 发出给定帧后保持连接打开、不再有任何数据的响应体 */
const silentStream = (frames: string[]) => {
  const encoder = new TextEncoder()
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const frame of frames) controller.enqueue(encoder.encode(frame))
    },
    pull() {
      return new Promise<void>(() => {})
    }
  })
}

const mockFetchStream = (frames: string[]) => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, body: silentStream(frames) } as unknown as Response)
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.clearAllTimers()
  vi.useRealTimers()
})

it('readSseStream：空闲超过阈值触发 onIdle 并结束读流', async () => {
  const events: string[] = []
  let idle = false
  const promise = readSseStream(silentStream(['event:start\ndata:{}\n\n']), {
    onEvent: event => events.push(event.event),
    idleTimeoutMs: SSE_IDLE_TIMEOUT_MS,
    onIdle: () => { idle = true }
  })
  await vi.advanceTimersByTimeAsync(SSE_IDLE_TIMEOUT_MS)
  await promise
  expect(idle).toBe(true)
  expect(events).toEqual(['start'])
})

it('readSseStream：持续到达的心跳重置计时，不会误杀活连接', async () => {
  let controller!: ReadableStreamDefaultController<Uint8Array>
  const encoder = new TextEncoder()
  const body = new ReadableStream<Uint8Array>({
    start(c) {
      controller = c
      c.enqueue(encoder.encode('event:start\ndata:{}\n\n'))
    }
  })
  let idle = false
  const promise = readSseStream(body, {
    onEvent: () => {},
    idleTimeoutMs: SSE_IDLE_TIMEOUT_MS,
    onIdle: () => { idle = true }
  })
  // 每 30s 一次心跳，共 90s：连接活着，看门狗不应触发
  for (let i = 0; i < 3; i++) {
    await vi.advanceTimersByTimeAsync(30_000)
    controller.enqueue(encoder.encode('event:heartbeat\ndata:{}\n\n'))
  }
  expect(idle).toBe(false)
  // 心跳停止后才触发
  await vi.advanceTimersByTimeAsync(SSE_IDLE_TIMEOUT_MS)
  await promise
  expect(idle).toBe(true)
})

it('readSseStream：不配置 idleTimeoutMs 时行为不变', async () => {
  const encoder = new TextEncoder()
  const body = new ReadableStream<Uint8Array>({
    start(c) {
      c.enqueue(encoder.encode('event:complete\ndata:{}\n\n'))
      c.close()
    }
  })
  const events: string[] = []
  await readSseStream(body, { onEvent: event => events.push(event.event) })
  await vi.advanceTimersByTimeAsync(SSE_IDLE_TIMEOUT_MS * 2)
  expect(events).toEqual(['complete'])
})

it('聊天流：静默断连上报 STREAM_IDLE_TIMEOUT 而不是无限等待', async () => {
  mockFetchStream(['event:start\ndata:{"conversationId":1}\n\n', 'event:data\ndata:{"content":"部分回答"}\n\n'])
  const onChunk = vi.fn()
  const onError = vi.fn()
  const promise = AiStream.streamChat({ message: '问题' } as never, onChunk, undefined, undefined, onError)
  await vi.advanceTimersByTimeAsync(SSE_IDLE_TIMEOUT_MS)
  await promise
  expect(onChunk).toHaveBeenCalledWith('部分回答')
  expect(onError).toHaveBeenCalledTimes(1)
  expect(onError.mock.calls[0]![0]).toMatchObject({ code: 'STREAM_IDLE_TIMEOUT' })
})

it('聊天流：正常完成后看门狗不再触发', async () => {
  const encoder = new TextEncoder()
  const body = new ReadableStream<Uint8Array>({
    start(c) {
      c.enqueue(encoder.encode('event:complete\ndata:{"conversationId":1}\n\n'))
      c.close()
    }
  })
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, body } as unknown as Response)
  const onComplete = vi.fn()
  const onError = vi.fn()
  await AiStream.streamChat({ message: '问题' } as never, vi.fn(), undefined, onComplete, onError)
  await vi.advanceTimersByTimeAsync(SSE_IDLE_TIMEOUT_MS * 2)
  expect(onComplete).toHaveBeenCalledTimes(1)
  expect(onError).not.toHaveBeenCalled()
})

it('写作流：静默断连上报 STREAM_IDLE_TIMEOUT，不伪造完成', async () => {
  mockFetchStream(['event:start\ndata:{"conversationId":1}\n\n'])
  const onComplete = vi.fn()
  const onError = vi.fn()
  const promise = streamWritingAssistant({
    baseUrl: '/ai', token: null, request: {},
    handlers: { onComplete, onError }
  })
  await vi.advanceTimersByTimeAsync(SSE_IDLE_TIMEOUT_MS)
  await promise
  expect(onError).toHaveBeenCalledWith('连接长时间无响应，请重试', 'STREAM_IDLE_TIMEOUT')
  expect(onComplete).not.toHaveBeenCalled()
})