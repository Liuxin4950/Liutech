import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AiStream, StreamError } from '@/services/aiStream'
import { useChatStore } from '@/stores/chat'

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
