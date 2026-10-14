import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AiStream, StreamError } from '@/services/aiStream'
import { useChatStore } from '@/stores/chat'
import { getAiRuntime } from '@/services/aiRuntime'

vi.mock('@/services/aiRuntime', () => ({
  getAiRuntime: vi.fn().mockResolvedValue({ defaultModel: 'test-model', tts: { enabled: false, online: false } })
}))

beforeEach(() => {
  vi.useFakeTimers()
  localStorage.clear()
  sessionStorage.clear()
  setActivePinia(createPinia())
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.clearAllTimers()
  vi.useRealTimers()
})

it('聊天断流后保留部分回答，显示错误并清理加载和思考状态', async () => {
  vi.spyOn(AiStream, 'streamChat').mockImplementation(async (_request, onChunk, _onEvent, _onComplete, onError) => {
    onChunk('已经生成的正文')
    onError?.(new StreamError('连接中断，请重试', 'STREAM_INCOMPLETE'))
  })
  const store = useChatStore()
  await store.sendMessage('问题')
  expect(store.messages.map(message => message.content)).toEqual(['问题', '已经生成的正文', '❌ 连接中断，请重试'])
  expect(store.messages[1]).toMatchObject({ isStreaming: false, isThinking: false })
  expect(store.messages[2]?.isError).toBe(true)
  expect(store.isLoading).toBe(false)
  expect(store.isStreaming).toBe(false)
  expect(store.aiThinking).toBe(false)
  expect(store.ttsAwaitingAudio).toBe(false)
})

it('尚未输出正文的失败请求移除空占位，仅显示错误', async () => {
  vi.spyOn(AiStream, 'streamChat').mockImplementation(async (_request, _onChunk, _onEvent, _onComplete, onError) => {
    onError?.(new StreamError('权限不足', 'HTTP_ERROR', 403))
  })
  const store = useChatStore()
  await store.sendMessage('问题')
  expect(store.messages.map(message => message.content)).toEqual(['问题', '❌ 权限不足'])
  expect(store.isLoading).toBe(false)
  expect(store.isStreaming).toBe(false)
})

it('云TTS已配置但未验证允许首次生成，真实失败状态关闭语音入口', async () => {
  vi.mocked(getAiRuntime).mockResolvedValueOnce({
    aiOnline: true, defaultModel: 'configured-model',
    tts: { enabled: true, online: false, configured: true, onlineVerified: false, checkedAt: 0 }
  })
  const store = useChatStore()
  await Promise.resolve()
  expect(store.defaultModel).toBe('configured-model')
  expect(store.ttsAvailable).toBe(true)
  vi.mocked(getAiRuntime).mockResolvedValueOnce({
    aiOnline: true, defaultModel: 'configured-model',
    tts: { enabled: true, online: false, configured: true, onlineVerified: true, checkedAt: 1 }
  })
  await store.loadRuntime()
  expect(store.ttsAvailable).toBe(false)
})

it('接话取消整轮请求并保留部分正文，旧轮迟到内容和结束事件不能覆盖新状态', async () => {
  let callbacks!: Parameters<typeof AiStream.streamChat>
  let finish!: () => void
  vi.spyOn(AiStream, 'cancel').mockImplementation(() => {})
  vi.spyOn(AiStream, 'streamChat').mockImplementation((...args) => {
    callbacks = args
    args[1]('已显示正文')
    return new Promise<void>(resolve => { finish = resolve })
  })
  const store = useChatStore()
  const pending = store.sendMessage('旧问题')
  await Promise.resolve()
  store.interruptReply()
  callbacks[1]('迟到正文')
  callbacks[2]?.('audio', { seq: 1, audioUrl: 'late.mp3' })
  callbacks[3]?.({ conversationId: 999 })
  finish()
  await pending
  expect(store.messages.map(message => message.content)).toEqual(['旧问题', '已显示正文'])
  expect(store.messages[1]).toMatchObject({ isStreaming: false, isThinking: false })
  expect(store.isLoading).toBe(false)
  expect(store.conversationId).toBeNull()
  expect(store.ttsPendingCount).toBe(0)
  expect(AiStream.cancel).toHaveBeenCalled()
})

it('正文 complete 后仍等迟到 TTS，audio-complete 才补缺口并允许免提恢复', async () => {
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
  let callbacks!: Parameters<typeof AiStream.streamChat>
  let finish!: () => void
  vi.spyOn(AiStream, 'streamChat').mockImplementation((...args) => {
    callbacks = args
    return new Promise<void>(resolve => { finish = resolve })
  })
  const store = useChatStore()
  await Promise.resolve()
  store.setTtsAvailable(true)
  store.showModel = true
  const pending = store.sendMessage('问题')
  await Promise.resolve()
  callbacks[1]('答案')
  callbacks[3]?.({ ttsEnabled: true, segments: 2 })
  expect(store.isLoading).toBe(false)
  expect(store.ttsAwaitingAudio).toBe(true)
  callbacks[2]?.('avatar-cue', { seq: 1, expression: 'happy' })
  callbacks[2]?.('avatar-cue', { seq: 2, expression: 'sad' })
  callbacks[2]?.('audio', { seq: 2, audioUrl: 'second.wav' })
  callbacks[2]?.('audio-complete', { segments: 2 })
  expect(store.ttsAwaitingAudio).toBe(false)
  expect(store.shiftTtsAudioQueue()).toMatchObject({ seq: 1, status: 'skipped', cue: { expression: 'happy' } })
  expect(store.shiftTtsAudioQueue()).toMatchObject({ seq: 2, cue: { expression: 'sad' } })
  finish()
  await pending
})

it('上一轮正文已完成但音频连接未关时，新提问不会被旧轮 finally 清掉生成状态', async () => {
  const requests: { callbacks: Parameters<typeof AiStream.streamChat>; finish: () => void }[] = []
  vi.spyOn(AiStream, 'streamChat').mockImplementation((...callbacks) => new Promise<void>(resolve => {
    requests.push({ callbacks, finish: resolve })
  }))
  const store = useChatStore()
  const old = store.sendMessage('旧问题')
  await Promise.resolve()
  requests[0]!.callbacks[3]?.({ ttsEnabled: true, segments: 1 })
  const latest = store.sendMessage('新问题')
  await Promise.resolve()
  requests[0]!.finish()
  await old
  expect(store.isStreaming).toBe(true)
  expect(store.isLoading).toBe(true)
  requests[1]!.callbacks[1]('新回答')
  requests[1]!.callbacks[3]?.({ ttsEnabled: false })
  requests[1]!.finish()
  await latest
  expect(store.messages[store.messages.length - 1]?.content).toBe('新回答')
})


it('隐藏模型时不请求无法播放的 TTS，显示模型后才恢复语音请求', async () => {
  const stream = vi.spyOn(AiStream, 'streamChat').mockImplementation(async (request, _chunk, _event, complete) => {
    complete?.({ ttsEnabled: request.ttsEnabled, segments: 0 })
  })
  const store = useChatStore()
  await Promise.resolve()
  store.setTtsAvailable(true)
  await store.sendMessage('仅文字')
  expect(stream.mock.calls[0]?.[0].ttsEnabled).toBe(false)
  store.showModel = true
  await store.sendMessage('带声音')
  expect(stream.mock.calls[1]?.[0].ttsEnabled).toBe(true)
})
