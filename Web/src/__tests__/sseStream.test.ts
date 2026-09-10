/**
 * SSE 流式链路回归测试
 *
 * 目的：把「后端真实帧格式 → 前端解析 → 回调分发」这条链路的行为固定下来。
 * 起因：Web 与 Admin 各自实现了一份 SSE 解析，协议理解已经分叉
 * （`start` 事件丢 conversationId、`error` 事件读错键名导致后端文案被吞）。
 * 解析层已收敛为 `services/sse.ts`（与 Admin 逐字节一致），任何改动都必须先让本文件通过。
 *
 * 关键事实（来自后端源码，不是猜测）：
 * - LiuTech-AI 用 Spring `SseEmitter` 下发，帧格式为
 *   `event:事件名\ndata:单行JSON\n\n`（SseEmitterHelper.sendSseEvent）。
 * - 后端**从不**下发 `contractVersion` envelope，前端保留 envelope 分支是为了后端将来升级。
 * - 错误事件负载是 `{ conversationId, error: "面向用户的文案" }`
 *   （SseEmitterHelper.safeSendError），键名是 `error` 而不是 `message`。
 *
 * 测试手法：不 mock 解析逻辑，只把 fetch 换成返回**真实的 `Response` + `ReadableStream`**
 * （undici 实现，与浏览器/线上同源），并按可控的分片边界推入字节，
 * 从而覆盖网络分片、粘包、坏帧、连接中断与 HTTP 错误码等真实形态。
 * 刻意不引入 `node:http` 等 Node 类型：前端 tsconfig 走 `@vue/tsconfig` 的 `"types": []`，
 * 保持"应用代码拿不到 Node 全局"的约束。
 *
 * @author 刘鑫
 */
import { afterAll, describe, expect, it, vi } from 'vitest'
import { isSseEnvelope, parseSseEventText, readSseStream } from '@/services/sse'

/** 拼一个后端风格的 SSE 帧（LF 换行，单行 JSON） */
const frame = (event: string, data: unknown): string =>
  `event:${event}\ndata:${JSON.stringify(data)}\n\n`

/**
 * 把一段文本按固定大小切成多个网络分片
 *
 * 用 `pull` 实现，让分片在消费方真正读取时才推入，贴近真实背压行为；
 * 分片边界故意落在 JSON 中间，用来验证跨分片的帧重组。
 *
 * @param text 完整响应文本
 * @param chunkSize 每个分片的字节数（按字符切，测试内容均为 ASCII/中文混合，够用）
 */
const chunkedStream = (text: string, chunkSize: number): ReadableStream<Uint8Array> => {
  const encoder = new TextEncoder()
  let offset = 0

  return new ReadableStream<Uint8Array>({
    pull(controller) {
      if (offset >= text.length) {
        controller.close()
        return
      }
      controller.enqueue(encoder.encode(text.slice(offset, offset + chunkSize)))
      offset += chunkSize
    }
  })
}

/** 一次被捕获的请求 */
interface CapturedRequest {
  url: string
  init: any
}

/**
 * 替换全局 fetch，返回可控响应并记录请求
 *
 * @param createResponse 依据请求生成响应
 * @returns captured：捕获到的请求列表；restore：恢复原 fetch
 */
const stubFetch = (createResponse: (request: CapturedRequest) => Response) => {
  const captured: CapturedRequest[] = []
  const originalFetch = globalThis.fetch

  vi.stubGlobal('fetch', async (input: any, init: any) => {
    const request: CapturedRequest = { url: String(input), init }
    captured.push(request)
    return createResponse(request)
  })

  return {
    captured,
    restore: () => { vi.stubGlobal('fetch', originalFetch) }
  }
}

/**
 * 构造一个 SSE 响应
 *
 * @param script 完整响应文本（多个帧拼起来）
 * @param chunkSize 网络分片大小，默认 17 字节（刻意切碎，制造粘包/半包）
 */
const sseResponse = (script: string, chunkSize = 17): Response =>
  new Response(chunkedStream(script, chunkSize), {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream;charset=UTF-8' }
  })

/**
 * 构造一个 JSON 错误响应（后端业务异常形态：{ code, message, data }）
 *
 * @param status HTTP 状态码
 * @param message 面向用户的文案
 */
const errorResponse = (status: number, message: string): Response =>
  new Response(JSON.stringify({ code: status, message, data: null }), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })

/** 记录一次解析调用的结果，便于断言顺序与内容 */
interface RecordedEvent {
  event: string
  payload: any
}

/** 内存版 localStorage（Node 环境没有 DOM） */
const installLocalStorage = (token: string | null) => {
  const store = new Map<string, string>()
  if (token) store.set('token', token)
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
    removeItem: (key: string) => void store.delete(key),
    clear: () => store.clear()
  })
  return store
}

/**
 * 动态导入流式服务
 *
 * serviceConfig 在模块加载时就把 baseURL 算好，所以必须先 stub 环境变量再导入，
 * 否则会指向开发地址 8081 或生产相对路径 /ai。
 */
const loadWritingAssistant = async () => {
  vi.stubEnv('VITE_AI_BASE_URL', 'https://test.local/ai')
  vi.resetModules()
  const { AdminAgentService } = await import('@/services/adminAgent')
  return AdminAgentService
}

/** 动态导入看板娘流式服务（同样依赖 serviceConfig） */
const loadAiStream = async () => {
  vi.stubEnv('VITE_AI_BASE_URL', 'https://test.local/ai')
  vi.resetModules()
  const { AiStream } = await import('@/services/aiStream')
  return AiStream
}

afterAll(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
})

describe('SSE 解析层（services/sse.ts）', () => {
  it('解析后端真实帧：event + 单行 data', () => {
    const outcome = parseSseEventText('event:data\ndata:{"content":"你好","conversationId":3}')
    expect(outcome).toEqual({
      status: 'ok',
      event: { event: 'data', payload: { content: '你好', conversationId: 3 }, envelope: null }
    })
  })

  it('兼容 CRLF 换行与行内空格', () => {
    const outcome = parseSseEventText('event: data\r\ndata: {"content":"x"}\r\n')
    expect(outcome.status).toBe('ok')
    expect(outcome.status === 'ok' && outcome.event.event).toBe('data')
    expect(outcome.status === 'ok' && outcome.event.payload).toEqual({ content: 'x' })
  })

  it('多行 data 按规范用换行拼接', () => {
    const outcome = parseSseEventText('event:data\ndata:{"content":\ndata:"多行"}')
    expect(outcome.status === 'ok' && outcome.event.payload).toEqual({ content: '多行' })
  })

  it('忽略注释行与 id/retry 字段', () => {
    const outcome = parseSseEventText(': keep-alive\nid: 7\nretry: 3000\nevent:data\ndata:{"content":"ok"}')
    expect(outcome.status === 'ok' && outcome.event.payload).toEqual({ content: 'ok' })
  })

  it('空帧与坏 JSON 分别给出 empty / invalid，且不抛异常', () => {
    expect(parseSseEventText('event:data\n')).toEqual({ status: 'empty' })
    const broken = parseSseEventText('event:data\ndata:{不是 JSON}')
    expect(broken.status).toBe('invalid')
    expect(broken.status === 'invalid' && broken.rawData).toBe('{不是 JSON}')
  })

  it('envelope 帧会剥壳，并保留 envelope 原件', () => {
    const envelope = {
      contractVersion: 1,
      event: 'data',
      taskId: 1,
      conversationId: 5,
      timestamp: '2026-01-01T00:00:00Z',
      payload: { content: '包内内容' }
    }
    const outcome = parseSseEventText(`event:data\ndata:${JSON.stringify(envelope)}`)
    expect(outcome.status).toBe('ok')
    expect(outcome.status === 'ok' && outcome.event.event).toBe('data')
    expect(outcome.status === 'ok' && outcome.event.payload).toEqual({ content: '包内内容' })
    expect(outcome.status === 'ok' && outcome.event.envelope?.conversationId).toBe(5)
    expect(isSseEnvelope(envelope)).toBe(true)
    expect(isSseEnvelope({ contractVersion: 2, event: 'data' })).toBe(false)
  })

  it('readSseStream 能跨网络分片重组帧，并冲刷末尾帧', async () => {
    const text = frame('data', { content: '第一段' }) + frame('data', { content: '第二段' })
    const encoder = new TextEncoder()
    // 故意从 JSON 中间切开，模拟 TCP 分片
    const middle = Math.floor(text.length / 2)
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(text.slice(0, middle)))
        controller.enqueue(encoder.encode(text.slice(middle)))
        controller.close()
      }
    })

    const received: string[] = []
    await readSseStream(stream, {
      onEvent: event => received.push((event.payload as { content: string }).content)
    })
    expect(received).toEqual(['第一段', '第二段'])
  })

  it('readSseStream 把坏帧交给 onParseError，但不影响后续帧', async () => {
    const encoder = new TextEncoder()
    const text = 'event:data\ndata:{坏}\n\n' + frame('data', { content: '后续' })
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(text))
        controller.close()
      }
    })

    const received: string[] = []
    const failures: string[] = []
    await readSseStream(stream, {
      onEvent: event => received.push((event.payload as { content: string }).content),
      onParseError: rawData => failures.push(rawData)
    })
    expect(received).toEqual(['后续'])
    expect(failures).toEqual(['{坏}'])
  })
})

describe('写作助手流式链路（Web 侧）', () => {
  it('按后端真实帧顺序分发事件，并把 token 放进请求头', async () => {
    installLocalStorage('unit-test-token')

    const script = [
      frame('start', { conversationId: 12, model: 'qwen', mode: 'writing' }),
      frame('data', { content: '正在', conversationId: 12 }),
      frame('data', { content: '生成正文', conversationId: 12 }),
      frame('tool-start', { toolName: 'searchArticles', displayName: '检索文章' }),
      frame('tool-result', { toolName: 'searchArticles', displayName: '检索文章', success: true, durationMs: 12 }),
      frame('field-update', { title: '新标题', tagNames: ['Vue'] }),
      frame('article-results', {
        reason: '我找到这些文章，可以直接点开阅读。',
        items: [{ id: 1, title: '第一篇文章' }]
      }),
      frame('complete', { conversationId: 12, responseLength: 6 })
    ].join('')

    const recorder = stubFetch(() => sseResponse(script))

    const AdminAgentService = await loadWritingAssistant()

    const events: RecordedEvent[] = []
    let data = ''
    let completed = false
    let errorMessage = ''

    await AdminAgentService.stream(
      { message: '写一篇 Vue 文章', draft: { title: '草稿' }, tempMessages: [] },
      {
        onStart: () => events.push({ event: 'start', payload: null }),
        onData: (chunk: string) => { data += chunk },
        onToolStart: (payload: any) => events.push({ event: 'tool-start', payload }),
        onToolResult: (payload: any) => events.push({ event: 'tool-result', payload }),
        onFieldUpdate: (payload: any) => events.push({ event: 'field-update', payload }),
        onArticles: (items: any[], payload: any) => events.push({ event: 'article-results', payload: { items, reason: payload?.reason } }),
        onComplete: () => { completed = true },
        onError: (message: string) => { errorMessage = message }
      }
    )

    // 请求形态没被重构改掉：地址、鉴权头、请求体
    expect(recorder.captured[0].url).toBe('https://test.local/ai/writing/stream')
    expect(recorder.captured[0].init.method).toBe('POST')
    expect(recorder.captured[0].init.headers.Authorization).toBe('Bearer unit-test-token')
    expect(recorder.captured[0].init.headers.Accept).toBe('text/event-stream')
    expect(JSON.parse(recorder.captured[0].init.body).message).toBe('写一篇 Vue 文章')

    expect(data).toBe('正在生成正文')
    expect(completed).toBe(true)
    expect(errorMessage).toBe('')
    expect(events.map(item => item.event)).toEqual([
      'start', 'tool-start', 'tool-result', 'field-update', 'article-results'
    ])
    expect(events[4].payload.items).toEqual([{ id: 1, title: '第一篇文章' }])
    expect(events[4].payload.reason).toBe('我找到这些文章，可以直接点开阅读。')

    recorder.restore()
  })

  it('未登录时不带 Authorization 头', async () => {
    installLocalStorage(null)
    const recorder = stubFetch(() => sseResponse(frame('complete', { conversationId: 1 })))
    const AdminAgentService = await loadWritingAssistant()
    await AdminAgentService.stream({ message: '你好' } as any, {})
    expect(recorder.captured[0].init.headers.Authorization).toBeUndefined()
    recorder.restore()
  })

  it('error 事件透出后端友好文案与错误码', async () => {
    installLocalStorage('unit-test-token')
    const recorder = stubFetch(() => sseResponse(
      frame('start', { conversationId: 1 }) +
      frame('error', { conversationId: 1, code: 'MODEL_BUSY', error: '模型正忙，请稍后再试' })
    ))

    const AdminAgentService = await loadWritingAssistant()

    let errorMessage = ''
    let errorCode: string | undefined
    await AdminAgentService.stream({ message: '你好' } as any, {
      onError: (message: string, code?: string) => { errorMessage = message; errorCode = code }
    })

    expect(errorMessage).toBe('模型正忙，请稍后再试')
    expect(errorCode).toBe('MODEL_BUSY')
    recorder.restore()
  })

  it('流未收到 complete/error 就结束时报连接中断', async () => {
    installLocalStorage('unit-test-token')
    const recorder = stubFetch(() => sseResponse(
      frame('start', { conversationId: 1 }) + frame('data', { content: '半句话', conversationId: 1 })
    ))

    const AdminAgentService = await loadWritingAssistant()

    let errorMessage = ''
    let completed = false
    await AdminAgentService.stream({ message: '你好' } as any, {
      onError: (message: string) => { errorMessage = message },
      onComplete: () => { completed = true }
    })

    expect(errorMessage).toBe('连接中断，请重试')
    expect(completed).toBe(true)
    recorder.restore()
  })

  it('HTTP 403 / 400 / 429 分别给出身份、后端文案与限流提示', async () => {
    installLocalStorage('unit-test-token')

    const forbidden = stubFetch(() => errorResponse(403, '无权限'))
    const serviceFor403 = await loadWritingAssistant()
    await expect(serviceFor403.stream({ message: '你好' } as any, {}))
      .rejects.toThrow('当前身份不能使用管理员写作助手')
    forbidden.restore()

    const badRequest = stubFetch(() => errorResponse(400, '后端返回的业务提示'))
    const serviceFor400 = await loadWritingAssistant()
    await expect(serviceFor400.stream({ message: '你好' } as any, {}))
      .rejects.toThrow('后端返回的业务提示')
    badRequest.restore()

    const tooMany = stubFetch(() => errorResponse(429, '限流'))
    const serviceFor429 = await loadWritingAssistant()
    await expect(serviceFor429.stream({ message: '你好' } as any, {}))
      .rejects.toThrow('请求过于频繁，请稍后再试')
    tooMany.restore()
  })
})

describe('看板娘聊天流式链路', () => {
  /** 逐帧喂给 AiStream.handleSSEEvent，收集回调结果 */
  const parseFrames = async (frames: string[]) => {
    const AiStream = await loadAiStream()
    const chunks: string[] = []
    const events: RecordedEvent[] = []
    let completedPayload: any = null
    const errors: string[] = []

    for (const text of frames) {
      AiStream.handleSSEEvent(
        text,
        (content: string) => chunks.push(content),
        (event: string, payload: any) => events.push({ event, payload }),
        (payload: any) => { completedPayload = payload },
        (error: any) => errors.push(error.message)
      )
    }

    return { chunks, events, completedPayload, errors }
  }

  it('解析 data / start / error 帧', async () => {
    const result = await parseFrames([
      frame('data', { content: '你好', conversationId: 3 }),
      frame('start', { conversationId: 3, model: 'qwen', mode: 'guest' }),
      frame('error', { conversationId: 3, error: '模型正忙，请稍后再试' })
    ])

    expect(result.chunks).toEqual(['你好'])
    expect(result.events[0].event).toBe('start')
    // 后端下发的是裸 payload，conversationId 就在顶层
    expect(result.events[0].payload.conversationId).toBe(3)
    // 后端把面向用户的文案放在 error 键，不能被吞成兜底文案
    expect(result.errors).toEqual(['模型正忙，请稍后再试'])
  })

  it('透传 avatar-cue / audio / article-results / complete', async () => {
    const result = await parseFrames([
      frame('avatar-cue', { seq: 1, text: '你好', conversationId: 3 }),
      frame('audio', { seq: 1, audioUrl: '/tts/a.mp3', conversationId: 3 }),
      frame('article-results', { reason: '推荐', items: [{ id: 9, title: '文章' }] }),
      frame('complete', { conversationId: 3, responseLength: 2 })
    ])

    expect(result.events.map(item => item.event)).toEqual(['avatar-cue', 'audio', 'article-results'])
    expect(result.events[1].payload.audioUrl).toBe('/tts/a.mp3')
    expect(result.events[2].payload.items).toEqual([{ id: 9, title: '文章' }])
    expect(result.completedPayload.conversationId).toBe(3)
  })

  it('兼容 envelope 帧（后端将来启用时不需要改前端）', async () => {
    const envelope = {
      contractVersion: 1,
      event: 'data',
      taskId: 1,
      conversationId: 5,
      timestamp: '2026-01-01T00:00:00Z',
      payload: { content: '包内内容' }
    }
    const result = await parseFrames([frame('data', envelope)])
    expect(result.chunks).toEqual(['包内内容'])
  })

  it('坏帧只跳过并留痕，不中断整条流', async () => {
    const result = await parseFrames([
      'event:data\ndata:{不是合法 JSON}\n\n',
      frame('data', { content: '后续内容' })
    ])
    expect(result.chunks).toEqual(['后续内容'])
    expect(result.errors).toEqual([])
  })

  it('真实流上：分片 + 坏帧 + complete 全链路', async () => {
    installLocalStorage(null)

    const recorder = stubFetch(() => sseResponse([
      frame('start', { conversationId: 8, model: 'qwen', mode: 'guest' }),
      frame('data', { content: '第一段', conversationId: 8 }),
      'event:data\ndata:{坏帧}\n\n',
      frame('data', { content: '第二段', conversationId: 8 }),
      frame('complete', { conversationId: 8, responseLength: 6 })
    ].join('')))

    const AiStream = await loadAiStream()

    const chunks: string[] = []
    const events: RecordedEvent[] = []
    const errors: string[] = []
    let completedPayload: any = null

    await AiStream.streamChat(
      { message: '你好' } as any,
      (chunk: string) => chunks.push(chunk),
      (event: string, payload: any) => events.push({ event, payload }),
      (payload: any) => { completedPayload = payload },
      (error: any) => errors.push(error.message)
    )

    // 看板娘走 /chat/stream，写作助手走 /writing/stream，路由不能混
    expect(recorder.captured[0].url).toBe('https://test.local/ai/chat/stream')
    expect(chunks).toEqual(['第一段', '第二段'])
    expect(errors).toEqual([])
    expect(events[0]).toEqual({ event: 'start', payload: { conversationId: 8 } })
    expect(completedPayload.conversationId).toBe(8)
    expect(AiStream.isStreaming).toBe(false)

    recorder.restore()
  })
})
