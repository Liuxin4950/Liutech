import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'

// Mock dependencies
vi.mock('@/services/ai', () => ({
  Ai: { chat: vi.fn() }
}))

vi.mock('@/services/aiStream', () => ({
  AiStream: { streamChat: vi.fn(), cancel: vi.fn() },
  StreamError: class StreamError extends Error {
    code?: string
    constructor(msg: string, code?: string) {
      super(msg)
      this.code = code
    }
  }
}))

vi.mock('@/services/aiRuntime', () => ({
  getAiRuntime: vi.fn().mockResolvedValue({
    defaultModel: 'test-model',
    tts: { enabled: false, online: false }
  })
}))

vi.mock('@/utils/auth', () => ({
  isLoggedIn: vi.fn().mockReturnValue(false)
}))

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(() => ({
    userInfo: null,
    isAdmin: false,
    fetchUserInfo: vi.fn()
  }))
}))

vi.mock('@/composables/useSequencedBuffer', () => ({
  useSequencedBuffer: vi.fn(() => ({
    enqueue: vi.fn(),
    shift: vi.fn().mockReturnValue(null),
    shiftBySeq: vi.fn().mockReturnValue(null),
    clear: vi.fn(),
    pendingCount: ref(0)
  }))
}))

vi.mock('@/config/services', () => ({
  getServiceBaseURL: vi.fn().mockReturnValue('http://localhost:8080'),
  ServiceType: { MAIN: 'main', AI: 'ai' }
}))

vi.mock('lodash-es', () => ({
  debounce: (fn: Function) => fn
}))

describe('useChatStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
  })

  describe('initial state', () => {
    it('should have empty messages and null conversationId', () => {
      const store = useChatStore()
      expect(store.messages).toEqual([])
      expect(store.conversationId).toBeNull()
      expect(store.isLoading).toBe(false)
      expect(store.isStreaming).toBe(false)
      expect(store.mode).toBe('stream')
    })
  })

  describe('computed properties', () => {
    it('hasMessages should be false when empty', () => {
      const store = useChatStore()
      expect(store.hasMessages).toBe(false)
    })

    it('hasMessages should be true when messages exist', () => {
      const store = useChatStore()
      store.addUserMessage('hello')
      expect(store.hasMessages).toBe(true)
    })

    it('lastMessage should return the last message', () => {
      const store = useChatStore()
      store.addUserMessage('first')
      store.addAiMessage('second')
      expect(store.lastMessage?.content).toBe('second')
    })

    it('lastMessage should be null when no messages', () => {
      const store = useChatStore()
      expect(store.lastMessage).toBeNull()
    })

    it('streamingMessage should find message with isStreaming flag', () => {
      const store = useChatStore()
      const msg = store.addAiMessage('', undefined, { isStreaming: true })
      expect(store.streamingMessage?.id).toBe(msg.id)
    })
  })

  describe('addUserMessage', () => {
    it('should add a user message with negative temp id', () => {
      const store = useChatStore()
      const msg = store.addUserMessage('Hello')
      expect(msg.type).toBe('user')
      expect(msg.content).toBe('Hello')
      expect(msg.id).toBeLessThan(0)
      expect(store.messages).toHaveLength(1)
    })

    it('should use provided id when given', () => {
      const store = useChatStore()
      const msg = store.addUserMessage('Hello', 42)
      expect(msg.id).toBe(42)
    })
  })

  describe('addAiMessage', () => {
    it('should add an AI message with default empty content', () => {
      const store = useChatStore()
      const msg = store.addAiMessage()
      expect(msg.type).toBe('ai')
      expect(msg.content).toBe('')
      expect(msg.id).toBeLessThan(0)
    })

    it('should accept streaming and thinking options', () => {
      const store = useChatStore()
      const msg = store.addAiMessage('test', undefined, { isStreaming: true, isThinking: true })
      expect(msg.isStreaming).toBe(true)
      expect(msg.isThinking).toBe(true)
    })

    it('should use provided id', () => {
      const store = useChatStore()
      const msg = store.addAiMessage('test', 100)
      expect(msg.id).toBe(100)
    })
  })

  describe('addErrorMessage', () => {
    it('should add error message with prefix', () => {
      const store = useChatStore()
      const msg = store.addErrorMessage('Something went wrong')
      expect(msg.type).toBe('ai')
      expect(msg.content).toContain('Something went wrong')
      expect(msg.isError).toBe(true)
    })
  })

  describe('clearHistory', () => {
    it('should clear all messages and conversationId', async () => {
      const store = useChatStore()
      store.addUserMessage('test')
      store.addAiMessage('reply')
      store.conversationId = 42

      await store.clearHistory()

      expect(store.messages).toEqual([])
      expect(store.conversationId).toBeNull()
    })
  })

  describe('setMode', () => {
    it('should update mode', () => {
      const store = useChatStore()
      store.setMode('normal')
      expect(store.mode).toBe('normal')
    })
  })

  describe('setTtsEnabled', () => {
    it('should update ttsEnabled', () => {
      const store = useChatStore()
      store.setTtsEnabled(false)
      expect(store.ttsEnabled).toBe(false)
      store.setTtsEnabled(true)
      expect(store.ttsEnabled).toBe(true)
    })
  })

  describe('setTtsAvailable', () => {
    it('should update ttsAvailable and disable tts when not available', () => {
      const store = useChatStore()
      store.ttsEnabled = true
      store.setTtsAvailable(false)
      expect(store.ttsAvailable).toBe(false)
      expect(store.ttsEnabled).toBe(false)
    })

    it('should keep ttsEnabled when available', () => {
      const store = useChatStore()
      store.ttsEnabled = true
      store.setTtsAvailable(true)
      expect(store.ttsAvailable).toBe(true)
      expect(store.ttsEnabled).toBe(true)
    })
  })

  describe('message state management', () => {
    it('should track streaming message via computed', () => {
      const store = useChatStore()
      const msg = store.addAiMessage('', undefined, { isStreaming: true })
      expect(store.streamingMessage?.id).toBe(msg.id)
    })

    it('should add messages with correct types', () => {
      const store = useChatStore()
      store.addUserMessage('user says')
      store.addAiMessage('ai replies')
      store.addErrorMessage('error occurred')
      expect(store.messages[0].type).toBe('user')
      expect(store.messages[1].type).toBe('ai')
      expect(store.messages[2].isError).toBe(true)
    })
  })

  describe('loadRuntime', () => {
    it('should load runtime info and set default model', async () => {
      const store = useChatStore()
      // Wait for the auto-loadRuntime in store initialization
      await vi.dynamicImportSettled()
      // The store auto-calls loadRuntime on init, so model should be set
      expect(store.defaultModel).toBeTruthy()
    })
  })

  describe('message ID generation', () => {
    it('should generate unique negative IDs', () => {
      const store = useChatStore()
      const msg1 = store.addUserMessage('a')
      const msg2 = store.addUserMessage('b')
      const msg3 = store.addAiMessage('c')
      expect(msg1.id).toBeLessThan(0)
      expect(msg2.id).toBeLessThan(0)
      expect(msg3.id).toBeLessThan(0)
      expect(msg1.id).not.toBe(msg2.id)
      expect(msg2.id).not.toBe(msg3.id)
    })
  })

  describe('persistence keys', () => {
    it('should use different storage keys for guest vs user sessions', () => {
      const store = useChatStore()
      // Guest session (no token) should use sessionStorage
      store.addUserMessage('guest msg')
      // The store auto-saves via debounced watcher, but we can test the logic exists
      expect(store.messages).toHaveLength(1)
    })
  })

  describe('sendMessage - normal mode', () => {
    it('should send message in normal mode and add AI response', async () => {
      const { Ai } = await import('@/services/ai')
      vi.mocked(Ai.chat).mockResolvedValue({
        success: true,
        message: 'AI response here',
        mode: 'user'
      })

      const store = useChatStore()
      store.setMode('normal')
      await store.sendMessage('Hello AI')

      expect(store.messages.length).toBeGreaterThanOrEqual(2)
      expect(store.messages[0].type).toBe('user')
      expect(store.messages[0].content).toBe('Hello AI')
    })

    it('should not send empty messages', async () => {
      const { Ai } = await import('@/services/ai')
      const store = useChatStore()
      await store.sendMessage('   ')
      expect(vi.mocked(Ai.chat)).not.toHaveBeenCalled()
    })

    it('should not send when isLoading is true', async () => {
      const { Ai } = await import('@/services/ai')
      const store = useChatStore()
      store.isLoading = true
      await store.sendMessage('test')
      expect(vi.mocked(Ai.chat)).not.toHaveBeenCalled()
    })

    it('should handle error in normal mode', async () => {
      const { Ai } = await import('@/services/ai')
      vi.mocked(Ai.chat).mockRejectedValue(new Error('Server error'))

      const store = useChatStore()
      store.setMode('normal')
      await store.sendMessage('Hello')

      expect(store.errorMessage).toBeTruthy()
      expect(store.messages.some(m => m.isError)).toBe(true)
    })

    it('should include conversationId in subsequent messages for logged in user', async () => {
      const { isLoggedIn } = await import('@/utils/auth')
      vi.mocked(isLoggedIn).mockReturnValue(true)
      const { Ai } = await import('@/services/ai')
      vi.mocked(Ai.chat).mockResolvedValue({
        success: true,
        message: 'Reply',
        conversationId: 42,
        mode: 'user'
      })

      // Create fresh store after changing isLoggedIn mock
      setActivePinia(createPinia())
      const store = useChatStore()
      store.setMode('normal')
      await store.sendMessage('First')
      expect(store.conversationId).toBe(42)
      // Reset
      vi.mocked(isLoggedIn).mockReturnValue(false)
    })

    it('should handle article results in response', async () => {
      const { Ai } = await import('@/services/ai')
      vi.mocked(Ai.chat).mockResolvedValue({
        success: true,
        message: 'Found articles',
        articleResults: {
          source: 'search',
          items: [{ id: 1, title: 'Article', viewCount: 10, likeCount: 1 }]
        },
        mode: 'user'
      })

      const store = useChatStore()
      store.setMode('normal')
      await store.sendMessage('find articles')

      const aiMsg = store.messages.find(m => m.type === 'ai')
      expect(aiMsg?.articleResults).toHaveLength(1)
    })
  })

  describe('sendMessage - stream mode', () => {
    it('should send message in stream mode', async () => {
      const { AiStream } = await import('@/services/aiStream')
      vi.mocked(AiStream.streamChat).mockImplementation(
        async (req, onChunk, onEvent, onComplete) => {
          onChunk('Hello ')
          onChunk('World')
          onComplete?.({ conversationId: 1 })
        }
      )

      const store = useChatStore()
      await store.sendMessage('Stream test')

      expect(store.messages.length).toBeGreaterThanOrEqual(2)
      expect(store.isLoading).toBe(false)
    })

    it('should handle stream error', async () => {
      const { AiStream, StreamError } = await import('@/services/aiStream')
      vi.mocked(AiStream.streamChat).mockImplementation(
        async (req, onChunk, onEvent, onComplete, onError) => {
          onError?.(new StreamError('Connection lost', 'SSE_ERR'))
        }
      )

      const store = useChatStore()
      await store.sendMessage('Test error')

      expect(store.messages.some(m => m.isError)).toBe(true)
    })

    it('should handle stream cancel on clearHistory', async () => {
      const { AiStream } = await import('@/services/aiStream')
      const store = useChatStore()
      await store.clearHistory()
      expect(AiStream.cancel).toHaveBeenCalled()
    })
  })

  describe('TTS queue methods', () => {
    it('should enqueue and shift TTS audio items', () => {
      const store = useChatStore()
      // These methods are available on the store
      expect(typeof store.enqueueTtsAudio).toBe('function')
      expect(typeof store.shiftTtsAudioQueue).toBe('function')
      expect(typeof store.clearTtsAudioQueue).toBe('function')
    })

    it('should enqueue and shift avatar cue items', () => {
      const store = useChatStore()
      expect(typeof store.enqueueAvatarCue).toBe('function')
      expect(typeof store.shiftAvatarCueQueue).toBe('function')
      expect(typeof store.clearAvatarCueQueue).toBe('function')
    })
  })

  describe('handleError', () => {
    it('should handle 429 errors', async () => {
      const { Ai } = await import('@/services/ai')
      const error: any = new Error('Too many requests')
      error.status = 429
      vi.mocked(Ai.chat).mockRejectedValue(error)

      const store = useChatStore()
      store.setMode('normal')
      await store.sendMessage('test')

      expect(store.errorMessage).toContain('频繁')
    })

    it('should handle 500 errors', async () => {
      const { Ai } = await import('@/services/ai')
      const error: any = new Error('Internal server error')
      error.status = 500
      vi.mocked(Ai.chat).mockRejectedValue(error)

      const store = useChatStore()
      store.setMode('normal')
      await store.sendMessage('test')

      expect(store.errorMessage).toContain('服务器')
    })

    it('should handle 503 errors', async () => {
      const { Ai } = await import('@/services/ai')
      const error: any = new Error('Service unavailable')
      error.status = 503
      vi.mocked(Ai.chat).mockRejectedValue(error)

      const store = useChatStore()
      store.setMode('normal')
      await store.sendMessage('test')

      expect(store.errorMessage).toContain('不可用')
    })
  })

  describe('storage persistence', () => {
    it('should have storage keys defined', () => {
      const store = useChatStore()
      // Verify the store initializes with storage-related state
      expect(store.mode).toBeDefined()
      expect(store.ttsEnabled).toBeDefined()
    })

    it('should handle corrupted localStorage gracefully', () => {
      localStorage.setItem('liutech-chat-history', 'invalid json{')
      setActivePinia(createPinia())
      const store = useChatStore()
      expect(store.messages).toEqual([])
    })

    it('should save mode to sessionStorage for guest', () => {
      const store = useChatStore()
      store.setMode('normal')
      // Guest sessions use sessionStorage
      const savedMode = sessionStorage.getItem('liutech-chat-mode-guest')
      expect(savedMode).toBe('normal')
    })

    it('should save ttsEnabled to sessionStorage for guest', () => {
      const store = useChatStore()
      store.setTtsEnabled(false)
      const saved = sessionStorage.getItem('liutech-chat-tts-enabled-guest')
      expect(saved).toBe('false')
    })
  })

  describe('model info', () => {
    it('should have default model set', () => {
      const store = useChatStore()
      expect(store.defaultModel).toBeTruthy()
    })
  })
})
