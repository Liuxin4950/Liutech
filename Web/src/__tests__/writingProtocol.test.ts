import { afterEach, describe, expect, it, vi } from 'vitest'
import { streamWritingAssistant } from '@/services/writingStream'

const frame = (payload: unknown, event = 'writing-event') => `event:${event}\ndata:${JSON.stringify(payload)}\n\n`
const event = (sequence: number, type: string, data: Record<string, unknown> = {}, extra = {}) => ({
  version: 1, requestId: 'request-one', sequence, timestamp: 12345, type, data, ...extra
})
const start = () => event(1, 'started', { model: 'DeepSeek-V3.2', mode: 'writing', baseRevision: 'revision-one' })
const response = (script: string) => new Response(new ReadableStream({ start(controller) {
  const bytes = new TextEncoder().encode(script)
  // 任意字节分片，包括中文多字节。
  for (let offset = 0; offset < bytes.length; offset += 17) controller.enqueue(bytes.slice(offset, offset + 17))
  controller.close()
} }), { headers: { 'Content-Type': 'text/event-stream' } })
async function run(script: string) {
  vi.stubGlobal('fetch', vi.fn(async () => response(script)))
  const handlers = { onStart: vi.fn(), onActivity: vi.fn(), onData: vi.fn(), onFieldUpdate: vi.fn(), onComplete: vi.fn(), onError: vi.fn() }
  await streamWritingAssistant({ baseUrl: '/ai', token: 'token', request: {}, handlers })
  return handlers
}
afterEach(() => vi.unstubAllGlobals())

describe('统一写作 SSE 协议', () => {
  it('按请求序号同步真实活动，正文补丁与会话版本一致，终态后不再分发', async () => {
    const activity = { activityId: 'read-categories:1', stage: 'reading_categories', message: '正在读取现有分类' }
    const patch = { baseRevision: 'revision-one', edits: [{ before: '<p>错字</p>', after: '<p>正字</p>' }] }
    const handlers = await run([
      start(), event(2, 'activity', { ...activity, status: 'running' }), event(3, 'heartbeat'),
      event(4, 'activity', { ...activity, status: 'completed', durationMs: 24 }),
      event(5, 'delta', { content: '只修改了一段。' }), event(6, 'proposal', { contentPatch: patch }),
      event(7, 'completed'), event(8, 'proposal', { title: '迟到字段' })
    ].map(value => frame(value)).join(''))
    expect(handlers.onStart).toHaveBeenCalledWith(expect.objectContaining({ requestId: 'request-one', baseRevision: 'revision-one' }))
    expect(handlers.onActivity).toHaveBeenCalledTimes(2)
    expect(handlers.onData).toHaveBeenCalledWith('只修改了一段。')
    expect(handlers.onFieldUpdate).toHaveBeenCalledExactlyOnceWith({ contentPatch: patch })
    expect(handlers.onComplete).toHaveBeenCalledTimes(1)
    expect(handlers.onError).not.toHaveBeenCalled()
  })

  it.each([
    ['不同版本', event(2, 'completed', {}, { version: 2 })],
    ['另一个请求', event(2, 'completed', {}, { requestId: 'other' })],
    ['序号缺失', event(3, 'completed')],
    ['重复序号', event(1, 'completed')],
    ['非数字时间', event(2, 'completed', {}, { timestamp: 'now' })],
    ['错误字段类型', event(2, 'proposal', { title: 123 })],
    ['过期补丁', event(2, 'proposal', { contentPatch: { baseRevision: 'old', edits: [{ before: '原稿', after: '改稿' }] } })],
    ['混合全文与补丁', event(2, 'proposal', { contentHtml: '<p>整文</p>', contentPatch: { baseRevision: 'revision-one', edits: [{ before: '原稿', after: '改稿' }] } })],
    ['非法活动状态', event(2, 'activity', { activityId: '1', stage: 'thinking', status: 'guess', message: '思考' })]
  ])('%s 会阻止后续完成与采纳', async (_name, invalid) => {
    const handlers = await run(frame(start()) + frame(invalid) + frame(event(3, 'completed')))
    expect(handlers.onError).toHaveBeenCalledExactlyOnceWith(expect.any(String), 'WRITING_PROTOCOL_ERROR')
    expect(handlers.onComplete).not.toHaveBeenCalled()
  })

  it('统一协议必须由 started 开始且不能混入旧事件', async () => {
    const noStart = await run(frame(event(1, 'completed')))
    expect(noStart.onError).toHaveBeenCalledTimes(1)
    const mixed = await run(frame(start()) + frame({ title: '新标题' }, 'field-update') + frame(event(2, 'completed')))
    expect(mixed.onFieldUpdate).not.toHaveBeenCalled()
    expect(mixed.onComplete).not.toHaveBeenCalled()
    expect(mixed.onError).toHaveBeenCalledTimes(1)
  })

  it('活动未结束或同ID重启不可伪造成完成', async () => {
    const activity = { activityId: 'read:1', stage: 'reading_draft', status: 'running', message: '正在读取正文' }
    const pending = await run(frame(start()) + frame(event(2, 'activity', activity)) + frame(event(3, 'completed')))
    expect(pending.onComplete).not.toHaveBeenCalled()
    expect(pending.onError).toHaveBeenCalledTimes(1)
    const reopened = await run(frame(start()) + frame(event(2, 'activity', { ...activity, status: 'completed' })) + frame(event(3, 'activity', activity)))
    expect(reopened.onError).toHaveBeenCalledTimes(1)
  })

  it('坏JSON帧之后即使收到正常completed也不可采纳', async () => {
    const handlers = await run(frame(start()) + 'event:writing-event\ndata:{坏帧}\n\n' + frame(event(2, 'completed')))
    expect(handlers.onError).toHaveBeenCalledTimes(1)
    expect(handlers.onComplete).not.toHaveBeenCalled()
  })

  it('failed是唯一终态，晚到proposal与completed被忽略', async () => {
    const handlers = await run(frame(start()) + frame(event(2, 'failed', { error: '模型输出截断', code: 'OUTPUT_TRUNCATED' }))
      + frame(event(3, 'proposal', { title: '不应采纳' })) + frame(event(4, 'completed')))
    expect(handlers.onError).toHaveBeenCalledExactlyOnceWith('模型输出截断', 'OUTPUT_TRUNCATED')
    expect(handlers.onFieldUpdate).not.toHaveBeenCalled()
    expect(handlers.onComplete).not.toHaveBeenCalled()
  })
})

it('收到确定 completed 后的连接关闭异常不能抹掉成功终态', async () => {
  let pulled = false
  vi.stubGlobal('fetch', vi.fn(async () => new Response(new ReadableStream({ pull(controller) {
    if (!pulled) { pulled = true; controller.enqueue(new TextEncoder().encode(frame(start()) + frame(event(2, 'completed')))) }
    else controller.error(new Error('connection closed after terminal'))
  } }))))
  const onComplete = vi.fn()
  const onError = vi.fn()
  await streamWritingAssistant({ baseUrl: '/ai', token: null, request: {}, handlers: { onComplete, onError } })
  expect(onComplete).toHaveBeenCalledTimes(1)
  expect(onError).not.toHaveBeenCalled()
})

it('completed 后即使连接保持打开也立即结束请求并取消reader', async () => {
  const cancel = vi.fn()
  const body = new ReadableStream<Uint8Array>({
    start(controller) { controller.enqueue(new TextEncoder().encode(frame(start()) + frame(event(2, 'completed')))) },
    cancel
  })
  vi.stubGlobal('fetch', vi.fn(async () => new Response(body)))
  const onComplete = vi.fn()
  const onError = vi.fn()
  await streamWritingAssistant({ baseUrl: '/ai', token: null, request: {}, handlers: { onComplete, onError } })
  expect(onComplete).toHaveBeenCalledTimes(1)
  expect(onError).not.toHaveBeenCalled()
  expect(cancel).toHaveBeenCalledTimes(1)
  expect(body.locked).toBe(false)
})

it.each([
  ['坏JSON', 'event:writing-event\ndata:{坏帧}\n\n'],
  ['非法建议', frame(event(2, 'proposal', { title: 123 }))],
  ['后端失败', frame(event(2, 'failed', { error: '模型输出中断' }))]
])('%s 后无需EOF立即取消上游，错误只通知一次', async (_name, broken) => {
  const cancel = vi.fn()
  const body = new ReadableStream<Uint8Array>({
    start(controller) { controller.enqueue(new TextEncoder().encode(frame(start()) + broken + frame(event(3, 'completed')))) },
    cancel
  })
  vi.stubGlobal('fetch', vi.fn(async () => new Response(body)))
  const onComplete = vi.fn()
  const onError = vi.fn()
  await streamWritingAssistant({ baseUrl: '/ai', token: null, request: {}, handlers: { onComplete, onError } })
  expect(onError).toHaveBeenCalledTimes(1)
  expect(onComplete).not.toHaveBeenCalled()
  expect(cancel).toHaveBeenCalledTimes(1)
  expect(body.locked).toBe(false)
})
