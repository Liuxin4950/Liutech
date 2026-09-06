import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { Ai, type AiChatRequest, type PostSummaryDTO } from '@/services/ai'
import { getAiRuntime, type AiRuntimeDTO } from '@/services/aiRuntime'
import { AiStream, StreamError } from '@/services/aiStream'
import { isLoggedIn } from '@/utils/auth'
import { debounce } from 'lodash-es'
import { useUserStore } from '@/stores/user'
import { useChatTts } from '@/composables/chatTts'

/**
 * 聊天消息接口
 */
export interface ChatMessage {
  id: number
  type: 'user' | 'ai'
  content: string
  renderedContent?: string // Cache for rendered HTML
  timestamp: Date
  isStreaming?: boolean
  isThinking?: boolean
  isError?: boolean
  conversationId?: number
  modelName?: string // 使用的模型名称
  articleResults?: PostSummaryDTO[]
  articleResultReason?: string
}

export interface TtsAudioItem {
  seq: number
  audioUrl?: string
  text?: string
  conversationId?: number
  enqueuedAt?: number
  audioEl?: HTMLAudioElement
  status?: 'ready' | 'skipped'
  reason?: string
  cue?: AvatarCueItem
}

export interface AvatarCueItem {
  seq: number
  expression?: string
  motion?: string | null
  intensity?: number
  durationMs?: number
  text?: string
  conversationId?: number
  enqueuedAt?: number
}

/**
 * 回复模式。
 *
 * 注意：这里只表示响应返回方式，不再表示是否启用 Agent。
 * Web 看板娘聊天始终走 Agent；stream 为 SSE 实时返回，normal 为一次性完整返回。
 */
export type ChatMode = 'stream' | 'normal'

/**
 * 当前模型信息
 */
interface ModelInfo {
  modelName: string
  displayName?: string
}

/**
 * AI聊天状态管理Store
 *
 * 作者：刘鑫
 * 时间：2025-01-27
 * 功能：集中管理聊天状态、历史记录和持久化
 */
export const useChatStore = defineStore('chat', () => {
  const userStore = useUserStore()
  const GUEST_CONTEXT_LIMIT = 10
  // ===== 状态 =====
  const messages = ref<ChatMessage[]>([])
  const conversationId = ref<number | null>(null)
  // 看板娘显示状态（模型/聊天框/展开）
  const showModel = ref(false)
  const showChat = ref(false)
  const isExpanded = ref(false)
  const isLoading = ref(false)
  const isStreaming = ref(false)
  const aiThinking = ref(false)
  // TTS 取消计数器：每次发新消息时递增，MainLayout 的 watcher 监听到后停止旧播放
  const ttsCancelCounter = ref(0)
  const mode = ref<ChatMode>('stream')
  const errorMessage = ref('')
  const defaultModel = ref<string>('deepseek-ai/DeepSeek-V3.2')  // 默认模型
  const isModelLoading = ref(false)  // 模型加载状态
  const currentModelInfo = ref<ModelInfo | null>(null)  // 当前模型信息

  /**
   * 语音开关（用户偏好）
   * - 这里只存“用户想不想开”，不代表服务一定可用
   */
  const ttsEnabled = ref<boolean>(true)

  /**
   * 语音是否可用（由后端在线探测决定）
   * - 用于控制 UI 是否允许开启语音
   */
  const ttsAvailable = ref<boolean>(false)
  const ttsAwaitingAudio = ref<boolean>(false)

  // TTS 音频队列 + Avatar Cue 管理（拆分到 composables/chatTts.ts）
  const chatTts = useChatTts({ ttsEnabled, ttsAvailable, ttsAwaitingAudio })

  // 生成临时消息ID（使用负数，避免与后端返回的正数ID冲突）
  let messageIdCounter = 0
  const generateTempId = (): number => {
    return --messageIdCounter  // 从0开始递减，产生负数ID
  }

  // ===== 计算属性 =====
  const hasMessages = computed(() => messages.value.length > 0)
  const lastMessage = computed(() => messages.value[messages.value.length - 1] || null)
  const streamingMessage = computed(() =>
    messages.value.find(msg => msg.isStreaming)
  )

  // ===== localStorage 键名 =====
  const STORAGE_KEY = 'liutech-chat-history'
  const CONVERSATION_ID_KEY = 'liutech-chat-conversation-id'
  const MODE_KEY = 'liutech-chat-mode'
  const TTS_ENABLED_KEY = 'liutech-chat-tts-enabled'
  const GUEST_STORAGE_KEY = 'liutech-chat-history-guest'
  const GUEST_MODE_KEY = 'liutech-chat-mode-guest'
  const GUEST_TTS_ENABLED_KEY = 'liutech-chat-tts-enabled-guest'

  const isGuestSession = () => !isLoggedIn()
  const ensureUserRoleLoaded = async () => {
    if (isLoggedIn() && !userStore.userInfo) {
      await userStore.fetchUserInfo()
    }
  }
  const getStorage = () => (isGuestSession() ? sessionStorage : localStorage)

  // ===== 持久化方法 =====
  /**
   * 保存聊天历史到localStorage
   */
  const saveToStorage = () => {
    try {
      const storage = getStorage()
      const storageKey = isGuestSession() ? GUEST_STORAGE_KEY : STORAGE_KEY
      const data = {
        messages: messages.value.map(msg => ({
          ...msg,
          timestamp: msg.timestamp.toISOString()
        })),
        conversationId: isGuestSession() ? null : conversationId.value,
        mode: mode.value
      }
      storage.setItem(storageKey, JSON.stringify(data))
    } catch (error) {
      console.error('保存聊天历史失败:', error)
    }
  }

  /**
   * 从localStorage加载聊天历史
   */
  const loadFromStorage = () => {
    try {
      const storage = getStorage()
      const storageKey = isGuestSession() ? GUEST_STORAGE_KEY : STORAGE_KEY
      const modeKey = isGuestSession() ? GUEST_MODE_KEY : MODE_KEY
      const ttsKey = isGuestSession() ? GUEST_TTS_ENABLED_KEY : TTS_ENABLED_KEY
      const stored = storage.getItem(storageKey)
      if (stored) {
        const data = JSON.parse(stored)
        messages.value = data.messages?.map((msg: Record<string, unknown>) => ({
          ...msg,
          timestamp: new Date(msg.timestamp as string),
          // 清除流式/思考状态：流式中刷新保存的消息可能带 isStreaming=true，
          // 若不清，下次新流式的 updateStreamingMessage 会按 isStreaming 找到这条历史消息并覆盖内容
          isStreaming: false,
          isThinking: false
        })) || []

        // 确保按时间戳升序排列（保护措施，防止顺序错乱）
        messages.value.sort((a, b) =>
          a.timestamp.getTime() - b.timestamp.getTime()
        )

        conversationId.value = isGuestSession() ? null : (data.conversationId || null)
      }

      // 加载模式设置
      const savedMode = storage.getItem(modeKey)
      if (savedMode && ['stream', 'normal'].includes(savedMode)) {
        mode.value = savedMode as ChatMode
      }

      // 加载语音开关（用户偏好）
      const savedTts = storage.getItem(ttsKey)
      if (savedTts !== null) {
        ttsEnabled.value = savedTts === 'true'
      }
    } catch (error) {
      console.error('加载聊天历史失败:', error)
      clearStorage()
    }
  }

  const cancelTts = () => {
    ttsCancelCounter.value++
    ttsAwaitingAudio.value = false
    chatTts.clearTtsAudioQueue()
  }
  const setTtsEnabled = (enabled: boolean) => {
    if (!enabled) cancelTts()
    chatTts.setTtsEnabled(enabled)
    try {
      const storage = getStorage()
      storage.setItem(isGuestSession() ? GUEST_TTS_ENABLED_KEY : TTS_ENABLED_KEY, String(enabled))
    } catch {
    }
  }

  const setTtsAvailable = (available: boolean) => {
    if (!available) cancelTts()
    chatTts.setTtsAvailable(available)
  }
  const clearTtsAudioQueue = () => chatTts.clearTtsAudioQueue()
  const clearAvatarCueQueue = () => chatTts.clearAvatarCueQueue()
  const enqueueTtsAudio = (item: TtsAudioItem) => chatTts.enqueueTtsAudio(item)
  const shiftTtsAudioQueue = (): TtsAudioItem | null => chatTts.shiftTtsAudioQueue()
  const enqueueAvatarCue = (item: AvatarCueItem) => chatTts.enqueueAvatarCue(item)
  const shiftAvatarCueQueue = (): AvatarCueItem | null => chatTts.shiftAvatarCueQueue()
  const shiftAvatarCueQueueBySeq = (seq: number): AvatarCueItem | null => chatTts.shiftAvatarCueQueueBySeq(seq)

  /**
   * 清理localStorage
   */
  const clearStorage = () => {
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem(CONVERSATION_ID_KEY)
    localStorage.removeItem(MODE_KEY)
    localStorage.removeItem(TTS_ENABLED_KEY)
    sessionStorage.removeItem(GUEST_STORAGE_KEY)
    sessionStorage.removeItem(GUEST_MODE_KEY)
    sessionStorage.removeItem(GUEST_TTS_ENABLED_KEY)
  }

  const buildGuestTempMessages = () => {
    return messages.value
      .filter(msg => !msg.isError)
      .slice(-GUEST_CONTEXT_LIMIT)
      .map(msg => ({
        role: msg.type === 'user' ? 'user' as const : 'assistant' as const,
        content: msg.content
      }))
  }

  // ===== 消息管理方法 =====
  /**
   * 添加用户消息
   * @param content 消息内容
   * @param id 可选，传入后端返回的ID，否则生成临时ID
   */
  const addUserMessage = (content: string, id?: number): ChatMessage => {
    const message: ChatMessage = {
      id: id ?? generateTempId(),  // 优先使用传入的ID
      type: 'user',
      content,
      timestamp: new Date(),
      conversationId: conversationId.value || undefined
    }
    messages.value.push(message)
    return message
  }

  /**
   * 添加AI消息（用于流式响应的开始）
   * @param content 消息内容
   * @param id 可选，传入后端返回的ID，否则生成临时ID
   */
  const addAiMessage = (
    content: string = '',
    id?: number,
    options?: { isStreaming?: boolean; isThinking?: boolean }
  ): ChatMessage => {
    const message: ChatMessage = {
      id: id ?? generateTempId(),  // 优先使用传入的ID
      type: 'ai',
      content,
      timestamp: new Date(),
      isStreaming: options?.isStreaming ?? false,
      isThinking: options?.isThinking ?? false,
      conversationId: conversationId.value || undefined,
      modelName: currentModelInfo.value?.displayName || currentModelInfo.value?.modelName
    }
    messages.value.push(message)
    return message
  }

  /**
   * 更新流式消息内容
   */
  const updateStreamingMessage = (content: string) => {
    const streamingMsg = messages.value.find(msg => msg.isStreaming)
    if (streamingMsg) {
      streamingMsg.isThinking = false
      streamingMsg.content += content
    }
  }

  /**
   * 完成流式消息
   */
  const completeStreamingMessage = () => {
    const streamingMsg = messages.value.find(msg => msg.isStreaming)
    if (streamingMsg) {
      streamingMsg.isStreaming = false
      streamingMsg.isThinking = false
      // Clear rendered cache since streaming is complete
      streamingMsg.renderedContent = undefined
    }
  }

  const updateAiMessage = (messageId: number, content: string, options?: { isThinking?: boolean; isStreaming?: boolean }) => {
    const message = messages.value.find(msg => msg.id === messageId)
    if (!message) return
    message.content = content
    if (typeof options?.isThinking === 'boolean') {
      message.isThinking = options.isThinking
    }
    if (typeof options?.isStreaming === 'boolean') {
      message.isStreaming = options.isStreaming
    }
    message.renderedContent = undefined
  }

  /**
   * 添加错误消息
   */
  const addErrorMessage = (error: string): ChatMessage => {
    const message: ChatMessage = {
      id: generateTempId(),  // 使用负数临时ID
      type: 'ai',
      content: `❌ ${error}`,
      timestamp: new Date(),
      isError: true
    }
    messages.value.push(message)
    return message
  }

  // ===== 聊天操作方法 =====
  /**
   * 发送消息
   * @param content 消息内容
   * @param context 上下文信息
   */
  const sendMessage = async (content: string, context?: Record<string, any>) => {
    if (!content.trim() || isLoading.value) return

    await ensureUserRoleLoaded()
    const guestTempMessages = isGuestSession() ? buildGuestTempMessages() : undefined

    // 清空之前的错误
    errorMessage.value = ''

    // 添加用户消息
    const userMessage = addUserMessage(content.trim())

    // 根据模式发送请求
    try {
      isLoading.value = true

      // 构建请求，只在有conversationId时才包含该字段
      const request: AiChatRequest = {
        message: content.trim(),
        context,
        ...(isGuestSession()
          ? { tempMessages: guestTempMessages }
          : { ...(conversationId.value && { conversationId: conversationId.value }) })
      }

      if (mode.value === 'stream') {
        await sendStreamMessage(request)
      } else {
        await sendNormalMessage(request)
      }
    } catch (error) {
      handleError(error)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 发送流式消息
   */
  const sendStreamMessage = async (request: AiChatRequest) => {
    isStreaming.value = true
    aiThinking.value = true
    ttsCancelCounter.value++
    clearTtsAudioQueue()
    clearAvatarCueQueue()
    const audioGeneration = ttsCancelCounter.value
    const shouldUseTts = ttsEnabled.value === true && ttsAvailable.value === true
    ttsAwaitingAudio.value = shouldUseTts

    // 创建空的AI消息用于流式更新
    const aiMessage = addAiMessage('', undefined, { isStreaming: true, isThinking: true })

    try {
      await AiStream.streamChat(
        {
          ...request,
          ttsEnabled: shouldUseTts
        },
        // onChunk - 接收到内容块
        (chunk: string) => {
          updateStreamingMessage(chunk)
        },
        // onEvent - SSE 事件分发
        // 处理的事件类型：start, article-results, avatar-cue, audio, audio-skip, audio-complete
        (eventType: string, payload: any) => {
          // start: 首事件携带 conversationId，立即更新 store（避免后续请求用旧 id 或 null）
          if (eventType === 'start') {
            if (payload?.conversationId && !conversationId.value) {
              conversationId.value = payload.conversationId
            }
            return
          }
          if (eventType === 'article-results' && payload?.items?.length) {
            aiMessage.articleResults = payload.items
            aiMessage.articleResultReason = payload.reason || '我找到这些文章，可以直接点开阅读。'
            aiMessage.isThinking = false
            aiMessage.renderedContent = undefined
            return
          }
          // avatar-cue: 表情提示到达，说明 AI 已开始输出，结束"思考"状态
          if (eventType === 'avatar-cue' && payload && typeof payload.seq === 'number') {
            aiThinking.value = false
            enqueueAvatarCue({
              seq: payload.seq,
              expression: payload.expression,
              motion: payload.motion,
              intensity: payload.intensity,
              durationMs: payload.durationMs,
              text: payload.text,
              conversationId: payload.conversationId,
              enqueuedAt: Date.now()
            })
            return
          }
          // audio-complete 用来解除“等待音频绑定 cue”的状态
          if (eventType === 'audio-complete' && audioGeneration === ttsCancelCounter.value) {
            ttsAwaitingAudio.value = false
            return
          }
          // TTS 未启用时，丢弃音频事件（avatar-cue 不受此限制，始终处理）
          if (audioGeneration !== ttsCancelCounter.value || ttsEnabled.value !== true || ttsAvailable.value !== true) return
          if (eventType === 'audio' && payload && payload.audioUrl && typeof payload.seq === 'number') {
            enqueueTtsAudio({
              seq: payload.seq,
              audioUrl: payload.audioUrl,
              text: payload.text,
              conversationId: payload.conversationId,
              enqueuedAt: Date.now()
            })
            return
          }
          if (eventType === 'audio-skip' && payload && typeof payload.seq === 'number') {
            enqueueTtsAudio({
              seq: payload.seq,
              text: payload.text,
              conversationId: payload.conversationId,
              enqueuedAt: Date.now(),
              status: 'skipped',
              reason: payload.reason
            })
          }
        },
        // onComplete - 流完成
        (response) => {
          completeStreamingMessage()
          isStreaming.value = false
          aiThinking.value = false
          ttsAwaitingAudio.value = false
          isLoading.value = false

          // 更新会话ID
          if (response.conversationId && !conversationId.value) {
            conversationId.value = response.conversationId
          }
        },
        // onError - 发生错误
        (error) => {
          // 移除流式消息
          const index = messages.value.findIndex(msg => msg.id === aiMessage.id)
          if (index > -1) {
            messages.value.splice(index, 1)
          }

          // 添加错误消息
          addErrorMessage(error.message)
          errorMessage.value = error.message
          isStreaming.value = false
          aiThinking.value = false
          ttsAwaitingAudio.value = false
          isLoading.value = false
        }
      )
    } catch (error) {
      // 清理流式消息
      const index = messages.value.findIndex(msg => msg.id === aiMessage.id)
      if (index > -1) {
        messages.value.splice(index, 1)
      }

      throw error
    } finally {
      if (isStreaming.value) {
        isStreaming.value = false
      }
      aiThinking.value = false
      ttsAwaitingAudio.value = false
    }
  }

  /**
   * 发送普通消息
   */
  const sendNormalMessage = async (request: AiChatRequest) => {
    const pendingAiMessage = addAiMessage('', undefined, { isThinking: true })

    try {
      const response = await Ai.chat(request)

      updateAiMessage(pendingAiMessage.id, response.message, {
        isThinking: false,
        isStreaming: false
      })
      if (response.articleResults?.items?.length) {
        pendingAiMessage.articleResults = response.articleResults.items
        pendingAiMessage.articleResultReason = response.articleResults.reason || '我找到这些文章，可以直接点开阅读。'
        pendingAiMessage.renderedContent = undefined
      }
      // 更新会话ID
      if (!isGuestSession() && response.conversationId && !conversationId.value) {
        conversationId.value = response.conversationId
      }
    } catch (error) {
      const index = messages.value.findIndex(msg => msg.id === pendingAiMessage.id)
      if (index > -1) {
        messages.value.splice(index, 1)
      }
      throw error
    }
  }

  /**
   * 处理错误
   */
  const handleError = (error: unknown) => {
    console.error('发送消息失败:', error)

    const errObj = (typeof error === 'object' && error !== null) ? error as Record<string, unknown> : null
    const errMsg = (error instanceof Error) ? error.message : (errObj?.message as string | undefined)
    const errStatus = errObj?.status as number | undefined

    let errorMsg = '发送消息失败'
    let detail = ''

    if (error instanceof StreamError) {
      errorMsg = error.message || '流式连接失败'
      detail = error.code ? `错误码: ${error.code}` : ''
    } else if (errStatus === 429) {
      errorMsg = '请求过于频繁，请稍后再试'
      detail = 'AI服务有访问频率限制，请等待几秒后重试'
    } else if (errStatus === 500) {
      errorMsg = '服务器内部错误，请稍后重试'
      detail = 'AI服务暂时出现问题，我们正在处理中'
    } else if (errStatus === 503) {
      errorMsg = '服务暂时不可用，请稍后重试'
      detail = 'AI服务正在维护中，请稍后再试'
    } else if (errMsg) {
      try {
        const parsed = JSON.parse(errMsg)
        errorMsg = parsed.message || errorMsg
        detail = parsed.detail || ''
      } catch {
        errorMsg = errMsg
      }
    }

    // 组合完整错误信息
    const fullErrorMsg = detail ? `${errorMsg}：${detail}` : errorMsg
    errorMessage.value = fullErrorMsg
    addErrorMessage(fullErrorMsg)

    // 3秒后自动清除错误提示
    setTimeout(() => {
      if (errorMessage.value === fullErrorMsg) {
        errorMessage.value = ''
      }
    }, 5000)
  }

  // ===== 清理方法 =====
  /**
   * 清空聊天记录
   */
  const clearHistory = async () => {
    try {
      // 清理状态
      messages.value = []
      conversationId.value = null
      errorMessage.value = ''
      ttsAwaitingAudio.value = false
      messageIdCounter = 0  // 重置计数器

      // 取消正在进行的流式请求
      AiStream.cancel()

      // 清理本地存储
      clearStorage()

    } catch (error) {
      console.error('清空聊天记录失败:', error)
      errorMessage.value = '清空聊天记录失败，请稍后重试'
    }
  }

  /**
   * 切换聊天模式
   */
  const setMode = (newMode: ChatMode) => {
    mode.value = newMode
    getStorage().setItem(isGuestSession() ? GUEST_MODE_KEY : MODE_KEY, newMode)
  }

  // ===== 看板娘显示控制 =====
  // BottomNavigation 入口防抖定时器
  let modelToggleTimer: ReturnType<typeof setTimeout> | null = null

  /** 点击 Live2d 模型：展开态忽略，否则切换聊天框开关 */
  const toggleChat = () => {
    if (isExpanded.value) return
    showChat.value = !showChat.value
    if (!showChat.value) {
      isExpanded.value = false
      showModel.value = true
    }
  }

  /** 切换展开/折叠大窗 */
  const expandChat = () => {
    if (isExpanded.value) {
      showModel.value = true
    }
    isExpanded.value = !isExpanded.value
  }

  /** 关闭聊天框，重置为初始态 */
  const closeChat = () => {
    showChat.value = false
    isExpanded.value = false
    showModel.value = true
  }

  /** 展开/折叠模型显示（仅展开态有效） */
  const toggleModelVisibility = () => {
    if (!isExpanded.value) return
    showModel.value = !showModel.value
  }

  /** 底部导航入口：防抖 toggle 模型显示 */
  const toggleModel = () => {
    if (modelToggleTimer) clearTimeout(modelToggleTimer)
    modelToggleTimer = setTimeout(() => {
      showModel.value = !showModel.value
      modelToggleTimer = null
    }, 300)
  }

  /** 外部唤起（如文章页"问 AI"）：打开并展开聊天框，可选带入 prompt */
  const openChatExternal = (detail?: { prompt?: string; autoSend?: boolean }) => {
    showModel.value = true
    showChat.value = true
    isExpanded.value = true
    if (detail?.prompt) {
      window.setTimeout(() => {
        window.dispatchEvent(new CustomEvent('ai-chat-apply-prompt', { detail }))
      }, 0)
    }
  }

  /**
   * 加载 AI 运行时状态
   * - 默认模型
   * - TTS 可用性
   */
  const loadRuntime = async (): Promise<AiRuntimeDTO | null> => {
    if (isModelLoading.value) return null
    try {
      isModelLoading.value = true
      const runtime = await getAiRuntime()
      const modelName = runtime.defaultModel || defaultModel.value
      defaultModel.value = modelName
      setTtsAvailable(runtime.tts.enabled === true && runtime.tts.online === true)

      // 更新当前模型信息
      currentModelInfo.value = {
        modelName: modelName,
        displayName: formatModelName(modelName)
      }

      return runtime

    } catch (error) {
      console.error('加载 AI 运行时状态失败:', error)
      errorMessage.value = '加载 AI 运行时状态失败，请刷新页面重试'
      setTtsAvailable(false)
      return null
    } finally {
      isModelLoading.value = false
    }
  }


  /**
   * 格式化模型名称用于显示
   * 将 "deepseek-ai/DeepSeek-V3.2" 转换为 "DeepSeek-V3.2"
   */
  const formatModelName = (modelName: string): string => {
    // 如果包含斜杠，取斜杠后的部分
    if (modelName.includes('/')) {
      return modelName.split('/').pop() || modelName
    }
    return modelName
  }

  // ===== 防抖保存 =====
  // 使用防抖避免频繁写入localStorage（500ms延迟）
  const debouncedSave = debounce(saveToStorage, 500)

  // ===== 监听器 =====
  // 监听消息变化，自动保存（使用防抖）
  // 不用 deep：流式时每 chunk 都会深度遍历整个 messages，改为监听关键字段签名
  watch(
    () => messages.value.map(msg => `${msg.id}:${msg.content.length}:${msg.isStreaming ? 1 : 0}:${msg.isThinking ? 1 : 0}:${msg.articleResults?.length || 0}`).join('|'),
    () => {
      debouncedSave()
    }
  )

  // 监听会话ID变化，自动保存
  watch(
    () => conversationId.value,
    () => {
      if (conversationId.value) {
        if (!isGuestSession()) {
          localStorage.setItem(CONVERSATION_ID_KEY, conversationId.value.toString())
        }
      } else {
        localStorage.removeItem(CONVERSATION_ID_KEY)
      }
    }
  )

  // ===== 初始化 =====
  // 组件加载时从localStorage恢复状态
  loadFromStorage()
  // 加载默认模型和运行时状态
  loadRuntime()

  return {
    // 状态
    messages,
    conversationId,
    showModel,
    showChat,
    isExpanded,
    isLoading,
    isStreaming,
    aiThinking,
    ttsCancelCounter,
    mode,
    errorMessage,
    defaultModel,
    isModelLoading,
    currentModelInfo,
    ttsEnabled,
    ttsAvailable,
    ttsAwaitingAudio,
    ttsPendingCount: chatTts.ttsAudioQueue.pendingCount,
    avatarCuePendingCount: chatTts.avatarCueQueue.pendingCount,

    // 计算属性
    hasMessages,
    lastMessage,
    streamingMessage,

    // 方法
    sendMessage,
    clearHistory,
    setMode,
    toggleChat,
    expandChat,
    closeChat,
    toggleModelVisibility,
    toggleModel,
    openChatExternal,
    cancelTts,
    setTtsEnabled,
    setTtsAvailable,
    enqueueTtsAudio,
    clearTtsAudioQueue,
    shiftTtsAudioQueue,
    enqueueAvatarCue,
    clearAvatarCueQueue,
    shiftAvatarCueQueue,
    shiftAvatarCueQueueBySeq,
    loadRuntime,
    addUserMessage,
    addAiMessage,
    addErrorMessage
  }
})
