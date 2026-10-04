import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { UserService } from '@/services/user'
import { AiStream } from '@/services/aiStream'
import { Ai } from '@/services/ai'
import { getToken, removeToken, setToken } from '@/utils/auth'

vi.mock('@/services/aiRuntime', () => ({ getAiRuntime: vi.fn().mockResolvedValue({ defaultModel: 'test', tts: { enabled: false, online: false } }) }))
vi.mock('@/services/user', () => ({ UserService: {
  isLoggedIn: () => !!getToken(),
  logout: () => removeToken(),
  getCurrentUser: vi.fn(async () => ({ id: getToken() === 'A' ? 1 : 2, username: getToken(), email: '', points: 0 }))
} }))

let pinia: ReturnType<typeof createPinia>
beforeEach(() => {
  vi.useFakeTimers()
  localStorage.clear(); sessionStorage.clear()
  pinia = createPinia(); setActivePinia(pinia)
})
afterEach(() => {
  for (const store of (pinia as any)._s.values()) store.$dispose()
  vi.clearAllTimers(); vi.useRealTimers(); vi.restoreAllMocks()
})

it('真实身份确认前不恢复旧记录；A退出后的游客上下文与B历史完全隔离', async () => {
  localStorage.setItem('liutech-chat-history', JSON.stringify({ messages: [{ content: '旧无归属私密消息' }] }))
  setToken('A')
  const user = useUserStore()
  const chat = useChatStore()
  expect(chat.messages).toEqual([])
  await user.fetchUserInfo()
  const stream = vi.spyOn(AiStream, 'streamChat').mockImplementation(async (_request, chunk, _event, complete) => { chunk('回答'); complete?.({ conversationId: 77 }) })
  await chat.sendMessage('A的私密问题')
  await nextTick(); await vi.advanceTimersByTimeAsync(600)
  user.logout()
  expect(chat.messages).toEqual([])
  expect(chat.conversationId).toBeNull()
  await chat.sendMessage('游客问题')
  expect(stream.mock.calls[stream.mock.calls.length - 1]?.[0].tempMessages).toEqual([])
  setToken('B'); await user.fetchUserInfo(true)
  expect(chat.messages).toEqual([])
  expect(chat.conversationId).toBeNull()
  await chat.sendMessage('B问题')
  expect(stream.mock.calls[stream.mock.calls.length - 1]?.[0]).not.toHaveProperty('conversationId')
  user.logout(); setToken('A'); await user.fetchUserInfo(true)
  expect(chat.messages.map(message => message.content)).toEqual(['A的私密问题', '回答'])
  expect(chat.conversationId).toBe(77)
  chat.$dispose(); user.$dispose()
  pinia = createPinia(); setActivePinia(pinia)
  const refreshedUser = useUserStore(); await refreshedUser.fetchUserInfo(true)
  const refreshed = useChatStore()
  await refreshed.sendMessage('A的新问题')
  expect(refreshed.messages.map(message => message.content)).toEqual(['A的私密问题', '回答', 'A的新问题', '回答'])
  expect(new Set(refreshed.messages.map(message => message.id)).size).toBe(4)
})

it('身份切换取消旧流，晚到正文、推荐与终态不能污染新用户', async () => {
  setToken('A')
  const user = useUserStore(); await user.fetchUserInfo()
  const chat = useChatStore()
  let finish!: () => void
  let callbacks: any
  vi.spyOn(AiStream, 'streamChat').mockImplementation(async (_request, chunk, event, complete) => {
    callbacks = { chunk, event, complete }
    await new Promise<void>(resolve => { finish = resolve })
  })
  const cancel = vi.spyOn(AiStream, 'cancel')
  const sending = chat.sendMessage('A的私密问题')
  await nextTick()
  user.logout(); setToken('B'); await user.fetchUserInfo(true)
  callbacks.chunk('迟到的A回答')
  callbacks.event?.('start', { conversationId: 77 })
  callbacks.event?.('article-results', { items: [{ id: 1, title: 'A文章' }] })
  callbacks.complete?.({ conversationId: 77 })
  finish(); await sending
  expect(cancel).toHaveBeenCalled()
  expect(chat.messages).toEqual([])
  expect(chat.conversationId).toBeNull()
  expect(chat.isLoading).toBe(false)
})

it('旧token的身份响应在登出后不重新认证用户', async () => {
  let resolve!: (value: any) => void
  vi.mocked(UserService.getCurrentUser).mockImplementationOnce(() => new Promise(done => { resolve = done }))
  setToken('A')
  const user = useUserStore()
  const loading = user.fetchUserInfo(true)
  user.logout()
  resolve({ id: 1, username: 'A', email: '', points: 0 })
  await loading
  expect(user.authenticatedUserId).toBeNull()
  expect(user.userInfo).toBeNull()
})

it('普通回复模式在登出时同样中止HTTP请求', async () => {
  setToken('A')
  const user = useUserStore(); await user.fetchUserInfo()
  const chat = useChatStore(); chat.setMode('normal')
  let signal: AbortSignal | undefined
  vi.spyOn(Ai, 'chat').mockImplementation(async (_request, requestSignal) => {
    signal = requestSignal
    return new Promise((_resolve, reject) => requestSignal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))))
  })
  const sending = chat.sendMessage('A普通模式问题')
  await nextTick()
  user.logout(); await sending
  expect(signal?.aborted).toBe(true)
  expect(chat.messages).toEqual([])
  expect(chat.errorMessage).toBe('')
})
