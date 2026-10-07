import { afterEach, describe, expect, it, vi } from 'vitest'
vi.mock('@/services/serviceConfig', () => ({ ServiceType: { AI: 'ai' }, getServiceBaseURL: () => '/ai' }))
vi.mock('@/utils/auth', () => ({ getToken: () => null }))
import { AiStream } from '@/services/aiStream'

afterEach(() => { AiStream.cancel(); vi.unstubAllGlobals() })
const response = (text: string) => new Response(new ReadableStream({ start(controller) {
  controller.enqueue(new TextEncoder().encode(text)); controller.close()
} }), { headers: { 'Content-Type': 'text/event-stream' } })

describe('一次生成只提交一次', () => {
  it('断线保留 partial 并报告中断，不重复提交 POST', async () => {
    const fetcher = vi.fn().mockResolvedValue(response('event:data\ndata:{"content":"已收到的部分"}\n\n'))
    vi.stubGlobal('fetch', fetcher)
    const chunk = vi.fn(), error = vi.fn()
    await AiStream.streamChat({ message: 'hello' }, chunk, undefined, undefined, error)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(chunk).toHaveBeenCalledWith('已收到的部分')
    expect(error).toHaveBeenCalledTimes(1)
    expect(error.mock.calls[0]![0].code).toBe('STREAM_INCOMPLETE')
    expect(AiStream.isStreaming).toBe(false)
  })
  it('HTTP 拒绝不重试，正常 complete 不误报中断', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response('', { status: 403 }))
      .mockResolvedValueOnce(response('event:complete\ndata:{"success":true,"fullResponse":"done"}\n\n'))
    vi.stubGlobal('fetch', fetcher)
    const error = vi.fn(), complete = vi.fn()
    await AiStream.streamChat({ message: 'one' }, vi.fn(), undefined, complete, error)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(error).toHaveBeenCalledTimes(1)
    error.mockClear()
    await AiStream.streamChat({ message: 'two' }, vi.fn(), undefined, complete, error)
    expect(error).not.toHaveBeenCalled()
    expect(complete).toHaveBeenCalledTimes(1)
  })
})
