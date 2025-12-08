import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { Ai, type AiChatRequest } from '@/services/ai'
import { AiStream, StreamError } from '@/services/aiStream'

/**
 * 聊天消息接口
 */
export interface ChatMessage {
  id: number
  type: 'user' | 'ai'
  content: string
  timestamp: Date
  isStreaming?: boolean
  isError?: boolean
  conversationId?: number
}

/**
 * 聊天模式
 */
export type ChatMode = 'stream' | 'normal'

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

  // 内部计数器
  let messageIdCounter = 0

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
        conversationId.value = data.conversationId || null
      }

      // 加载模式设置
      const savedMode = localStorage.getItem(MODE_KEY)
      if (savedMode && ['stream', 'normal'].includes(savedMode)) {
        mode.value = savedMode as ChatMode
      }
    } catch (error) {
      console.error('加载聊天历史失败:', error)
      clearStorage()
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
   */
  const addUserMessage = (content: string): ChatMessage => {
    const message: ChatMessage = {
      id: ++messageIdCounter,
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
   */
  const addAiMessage = (content: string = ''): ChatMessage => {
    const message: ChatMessage = {
      id: ++messageIdCounter,
      type: 'ai',
      content,
      timestamp: new Date(),
      isStreaming: true,
      conversationId: conversationId.value || undefined
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
    }
  }

  /**
   * 添加错误消息
   */
  const addErrorMessage = (error: string): ChatMessage => {
    const message: ChatMessage = {
      id: ++messageIdCounter,
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
   */
  const sendMessage = async (content: string, context?: Record<string, any>) => {
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

    // 添加AI响应
    messages.value.push({
      id: ++messageIdCounter,
      type: 'ai',
      content: response.message,
      timestamp: new Date(),
      conversationId: response.conversationId
    })

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

    if (error instanceof StreamError) {
      errorMsg = error.message
    } else if (error.status === 429) {
      errorMsg = '请求过于频繁，请稍后再试'
    } else if (error.status === 500) {
      errorMsg = '服务器内部错误，请稍后重试'
    } else if (error.status === 503) {
      errorMsg = '服务暂时不可用，请稍后重试'
    }

    errorMessage.value = errorMsg
    addErrorMessage(errorMsg)
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
      messageIdCounter = 0

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

  // ===== 监听器 =====
  // 监听消息变化，自动保存
  watch(
    () => messages.value,
    () => {
      saveToStorage()
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

  return {
    // 状态
    messages,
    conversationId,
    isLoading,
    isStreaming,
    mode,
    errorMessage,

    // 计算属性
    hasMessages,
    lastMessage,
    streamingMessage,

    // 方法
    sendMessage,
    clearHistory,
    setMode,
    addUserMessage,
    addAiMessage,
    addErrorMessage
  }
})