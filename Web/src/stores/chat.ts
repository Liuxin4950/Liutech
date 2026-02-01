import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { Ai, type AiChatRequest } from '@/services/ai'
import { AiStream, StreamError } from '@/services/aiStream'
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
  isError?: boolean
  conversationId?: number
  modelName?: string // 使用的模型名称
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

  // ===== 持久化方法 =====
  /**
   * 保存聊天历史到localStorage
   */
  const saveToStorage = () => {
    try {
      const data = {
        messages: messages.value.map(msg => ({
          ...msg,
          timestamp: msg.timestamp.toISOString()
        })),
        conversationId: conversationId.value,
        mode: mode.value
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
    } catch (error) {
      console.error('保存聊天历史失败:', error)
    }
  }

  /**
   * 从localStorage加载聊天历史
   */
  const loadFromStorage = () => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
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

        conversationId.value = data.conversationId || null
      }

      // 加载模式设置
      const savedMode = localStorage.getItem(MODE_KEY)
      if (savedMode && ['stream', 'normal'].includes(savedMode)) {
        mode.value = savedMode as ChatMode
      }

      // 加载语音开关（用户偏好）
      const savedTts = localStorage.getItem(TTS_ENABLED_KEY)
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
      localStorage.setItem(TTS_ENABLED_KEY, String(enabled))
    } catch {
    }
  }

  const setTtsAvailable = (available: boolean) => {
    ttsAvailable.value = available
    if (!available) {
      setTtsEnabled(false)
    }
  }

  /**
   * 清理localStorage
   */
  const clearStorage = () => {
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem(CONVERSATION_ID_KEY)
    localStorage.removeItem(MODE_KEY)
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
  const addAiMessage = (content: string = '', id?: number): ChatMessage => {
    const message: ChatMessage = {
      id: id ?? generateTempId(),  // 优先使用传入的ID
      type: 'ai',
      content,
      timestamp: new Date(),
      isStreaming: true,
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
      // Clear rendered cache since streaming is complete
      streamingMsg.renderedContent = undefined
    }
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
        ...(conversationId.value && { conversationId: conversationId.value })
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

    // 创建空的AI消息用于流式更新
    const aiMessage = addAiMessage()

    try {
      await AiStream.streamChat(
        request,
        // onChunk - 接收到内容块
        (chunk: string) => {
          updateStreamingMessage(chunk)
        },
        // onComplete - 流完成
        (response) => {
          completeStreamingMessage()

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
      isStreaming.value = false
    }
  }

  /**
   * 发送普通消息
   */
  const sendNormalMessage = async (request: AiChatRequest) => {
    const response = await Ai.chat(request)

    // 使用统一方法添加AI响应
    addAiMessage(response.message)

    // 更新会话ID
    if (response.conversationId && !conversationId.value) {
      conversationId.value = response.conversationId
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

      console.log('聊天记录已清空')
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
    localStorage.setItem(MODE_KEY, newMode)
  }

  /**
   * 加载默认模型并更新当前模型信息
   */
  const loadDefaultModel = async () => {
    if (isModelLoading.value) return
    try {
      isModelLoading.value = true
      const modelName = await Ai.getDefaultModel()
      defaultModel.value = modelName

      // 更新当前模型信息
      currentModelInfo.value = {
        modelName: modelName,
        displayName: formatModelName(modelName)
      }

      console.log('已加载默认模型:', modelName)
    } catch (error) {
      console.error('加载默认模型失败:', error)
      errorMessage.value = '加载模型配置失败，请刷新页面重试'
    } finally {
      isModelLoading.value = false
    }
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
        localStorage.setItem(CONVERSATION_ID_KEY, conversationId.value.toString())
      } else {
        localStorage.removeItem(CONVERSATION_ID_KEY)
      }
    }
  )

  // ===== 初始化 =====
  // 组件加载时从localStorage恢复状态
  loadFromStorage()
  // 加载默认模型
  loadDefaultModel()

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
    loadDefaultModel,
    addUserMessage,
    addAiMessage,
    addErrorMessage
  }
})
