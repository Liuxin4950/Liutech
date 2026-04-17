import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { Ai, type AiChatRequest } from '@/services/ai'
import { getAiRuntime, type AiRuntimeDTO } from '@/services/aiRuntime'
import { AiStream, StreamError } from '@/services/aiStream'
import { isLoggedIn } from '@/utils/auth'
import { debounce } from 'lodash-es'

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
}

/**
 * 聊天模式
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
  const GUEST_CONTEXT_LIMIT = 10
  // ===== 状态 =====
  const messages = ref<ChatMessage[]>([])
  const conversationId = ref<number | null>(null)
  const isLoading = ref(false)
  const isStreaming = ref(false)
  const mode = ref<ChatMode>('stream')
  const errorMessage = ref('')
  const defaultModel = ref<string>('zai-org/GLM-4.6')  // 默认模型
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

  /**
   * 语音片段缓冲区（按 seq 存储）
   *
   * 设计动机：
   * - AI 端的 TTS 推理是异步/可能并发的，audio 事件到达顺序不一定等于“应该播放顺序”
   * - 因此前端必须按 seq 进行排序播放：只有拿到 nextSeq 才能播放下一段
   */
  const ttsAudioBuffer = ref<Record<number, TtsAudioItem>>({})
  /**
   * 下一段应该播放的序号（从 1 开始）
   */
  const ttsNextSeq = ref<number>(1)
  /**
   * 当前缓冲区待播放的片段数（用于 UI/监听触发播放）
   */
  const ttsPendingCount = computed(() => Object.keys(ttsAudioBuffer.value).length)

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
        messages.value = data.messages?.map((msg: any) => ({
          ...msg,
          timestamp: new Date(msg.timestamp)
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

  const setTtsEnabled = (enabled: boolean) => {
    ttsEnabled.value = enabled
    try {
      const storage = getStorage()
      storage.setItem(isGuestSession() ? GUEST_TTS_ENABLED_KEY : TTS_ENABLED_KEY, String(enabled))
    } catch {
    }
  }

  const setTtsAvailable = (available: boolean) => {
    ttsAvailable.value = available
    if (!available) {
      setTtsEnabled(false)
    }
  }

  const clearTtsAudioQueue = () => {
    ttsAudioBuffer.value = {}
    ttsNextSeq.value = 1
  }

  const enqueueTtsAudio = (item: TtsAudioItem) => {
    if (!item) return
    if (typeof item.seq !== 'number' || item.seq <= 0) return
    const now = Date.now()
    const enriched: TtsAudioItem = {
      ...item,
      status: item.status ?? (item.audioUrl ? 'ready' : 'skipped'),
      enqueuedAt: item.enqueuedAt ?? now
    }
    if (enriched.status === 'ready' && enriched.audioUrl) {
      try {
        // 收到音频地址后立即预加载，尽量把“网络/磁盘等待”提前到播放之前完成
        const pre = new Audio(enriched.audioUrl)
        pre.preload = 'auto'
        pre.crossOrigin = 'anonymous'
        pre.load()
        enriched.audioEl = pre
      } catch {
      }
    }
    ttsAudioBuffer.value[item.seq] = enriched
  }

  const shiftTtsAudioQueue = (): TtsAudioItem | null => {
    const next = ttsAudioBuffer.value[ttsNextSeq.value]
    if (!next) return null
    delete ttsAudioBuffer.value[ttsNextSeq.value]
    ttsNextSeq.value += 1
    return next
  }

  /**
   * 清理localStorage
   */
  const clearStorage = () => {
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem(CONVERSATION_ID_KEY)
    localStorage.removeItem(MODE_KEY)
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
   * @param model 模型名称（可选，不传则使用默认模型）
   */
  const sendMessage = async (content: string, context?: Record<string, any>, model?: string) => {
    if (!content.trim() || isLoading.value) return

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
        model: model || defaultModel.value,  // 使用传入的模型或默认模型
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
    clearTtsAudioQueue()

    // 创建空的AI消息用于流式更新
    const aiMessage = addAiMessage('', undefined, { isStreaming: true, isThinking: true })

    try {
      await AiStream.streamChat(
        {
          ...request,
          ttsEnabled: ttsEnabled.value === true && ttsAvailable.value === true
        },
        // onChunk - 接收到内容块
        (chunk: string) => {
          updateStreamingMessage(chunk)
        },
        // onEvent - 接收到语音/心跳事件
        (eventType: string, payload: any) => {
          if (ttsEnabled.value !== true || ttsAvailable.value !== true) return
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
  const handleError = (error: any) => {
    console.error('发送消息失败:', error)

    let errorMsg = '发送消息失败'
    let detail = ''

    if (error instanceof StreamError) {
      errorMsg = error.message || '流式连接失败'
      detail = error.code ? `错误码: ${error.code}` : ''
    } else if (error.status === 429) {
      errorMsg = '请求过于频繁，请稍后再试'
      detail = 'AI服务有访问频率限制，请等待几秒后重试'
    } else if (error.status === 500) {
      errorMsg = '服务器内部错误，请稍后重试'
      detail = 'AI服务暂时出现问题，我们正在处理中'
    } else if (error.status === 503) {
      errorMsg = '服务暂时不可用，请稍后重试'
      detail = 'AI服务正在维护中，请稍后再试'
    } else if (error.message) {
      // 尝试解析后端返回的错误信息
      try {
        const parsed = JSON.parse(error.message)
        errorMsg = parsed.message || errorMsg
        detail = parsed.detail || ''
      } catch {
        errorMsg = error.message
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

  const loadDefaultModel = async () => {
    await loadRuntime()
  }

  /**
   * 格式化模型名称用于显示
   * 将 "zai-org/GLM-4.6" 转换为 "GLM-4.6"
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
  watch(
    () => messages.value,
    () => {
      debouncedSave()
    },
    { deep: true }
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
    isLoading,
    isStreaming,
    mode,
    errorMessage,
    defaultModel,
    isModelLoading,
    currentModelInfo,
    ttsEnabled,
    ttsAvailable,
    ttsPendingCount,

    // 计算属性
    hasMessages,
    lastMessage,
    streamingMessage,

    // 方法
    sendMessage,
    clearHistory,
    setMode,
    setTtsEnabled,
    setTtsAvailable,
    enqueueTtsAudio,
    clearTtsAudioQueue,
    shiftTtsAudioQueue,
    loadRuntime,
    loadDefaultModel,
    addUserMessage,
    addAiMessage,
    addErrorMessage
  }
})
