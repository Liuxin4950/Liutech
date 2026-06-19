import { ref, nextTick } from 'vue'
import { ConversationService, type Conversation } from '@/services/conversation'
import type { useChatStore } from '@/stores/chat'
import { showConfirm, showWarning } from '@/utils/errorHandler'
import { isLoggedIn } from '@/utils/auth'

type ChatStore = ReturnType<typeof useChatStore>

/**
 * 会话侧边栏管理 composable
 *
 * 职责：会话列表加载、加载/删除/重命名会话、侧边栏开关。
 * 从 AiChat.vue 中提取，供 AiChat.vue 直接使用。
 */
export function useConversationManager(chatStore: ChatStore) {
  const conversations = ref<Conversation[]>([])
  const isLoadingHistory = ref(false)
  const showHistorySidebar = ref(false)
  const isAuthenticated = ref(isLoggedIn())
  const editingConversationId = ref<number | null>(null)
  const editingTitle = ref('')

  const syncAuthState = () => {
    isAuthenticated.value = isLoggedIn()
  }

  const loadConversations = async () => {
    if (!isAuthenticated.value || isLoadingHistory.value) return
    try {
      isLoadingHistory.value = true
      conversations.value = await ConversationService.list('general', 1, 50)
    } catch {
      // 加载会话历史失败时静默处理
    } finally {
      isLoadingHistory.value = false
    }
  }

  const toggleHistorySidebar = () => {
    syncAuthState()
    if (!isAuthenticated.value) {
      showWarning('登录后可查看与保存历史记录', '游客体验模式')
      return
    }
    showHistorySidebar.value = !showHistorySidebar.value
    if (showHistorySidebar.value && conversations.value.length === 0) {
      loadConversations()
    }
  }

  const loadConversation = async (conversationId: number, scrollToBottom: () => Promise<void>) => {
    try {
      isLoadingHistory.value = true
      const historyMessages = await ConversationService.messages(conversationId, 1, 100)

      chatStore.clearHistory()
      chatStore.conversationId = conversationId

      historyMessages.forEach(msg => {
        if (msg.role === 'user') {
          chatStore.addUserMessage(msg.content, msg.id)
        } else if (msg.role === 'assistant') {
          const aiMessage = chatStore.addAiMessage(msg.content, msg.id)
          aiMessage.isStreaming = false
        }
      })

      showHistorySidebar.value = false
      await scrollToBottom()
    } catch {
      // 加载会话失败时静默处理
    } finally {
      isLoadingHistory.value = false
    }
  }

  const deleteConversation = async (conversationId: number, event: Event) => {
    event.stopPropagation()
    const ok = await showConfirm('确定要删除这个会话吗？', '确认删除')
    if (!ok) return

    try {
      await ConversationService.remove(conversationId)
      conversations.value = conversations.value.filter(conv => conv.id !== conversationId)
      if (chatStore.conversationId === conversationId) {
        chatStore.clearHistory()
      }
    } catch {
      // 删除会话失败时静默处理
    }
  }

  const startEditTitle = (conversationId: number, currentTitle: string) => {
    editingConversationId.value = conversationId
    editingTitle.value = currentTitle || `会话 ${conversationId}`
    nextTick(() => {
      const input = document.querySelector('.title-edit-input') as HTMLInputElement | null
      input?.focus()
      input?.select()
    })
  }

  const saveTitle = async (conversationId: number) => {
    if (!editingTitle.value.trim()) {
      cancelEditTitle()
      return
    }

    try {
      await ConversationService.rename(conversationId, editingTitle.value.trim())
      const conversation = conversations.value.find(c => c.id === conversationId)
      if (conversation) {
        conversation.title = editingTitle.value.trim()
      }
    } catch {
      // 重命名失败时静默处理
    } finally {
      cancelEditTitle()
    }
  }

  const cancelEditTitle = () => {
    editingConversationId.value = null
    editingTitle.value = ''
  }

  const formatConversationTime = (dateString?: string) => {
    if (!dateString) return ''
    const date = new Date(dateString)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    const days = Math.floor(diff / (1000 * 60 * 60 * 24))

    if (days === 0) {
      return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    }
    if (days === 1) return '昨天'
    if (days < 7) return `${days}天前`
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  }

  return {
    conversations,
    isLoadingHistory,
    showHistorySidebar,
    isAuthenticated,
    editingConversationId,
    editingTitle,
    syncAuthState,
    loadConversations,
    toggleHistorySidebar,
    loadConversation,
    deleteConversation,
    startEditTitle,
    saveTitle,
    cancelEditTitle,
    formatConversationTime,
  }
}
