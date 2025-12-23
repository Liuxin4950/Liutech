<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore, type ChatMessage, type ChatMode } from '@/stores/chat'
import { Ai, AiStream, type RecommendResponse, type PostSummaryDTO } from '@/services/ai'
import { ConversationService, type Conversation, type ChatMessageItem } from '@/services/conversation'
import MarkdownRenderer from './MarkdownRenderer.vue'

// 接收父组件传入的扩展状态
const props = defineProps<{
  expanded?: boolean
}>()

// 定义emit事件
const emit = defineEmits<{
  expand: []
}>()

/**
 * 简化版AI聊天组件
 * 作者：刘鑫
 * 时间：2025-01-27
 * 功能：专注于聊天功能，支持流式和普通模式切换，使用Pinia管理状态
 */

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

// 组件本地状态
const chatInput = ref('')
const chatContainer = ref<HTMLElement>()
const isModeDropdownOpen = ref(false)

// 历史记录相关状态
const conversations = ref<Conversation[]>([])
const isLoadingHistory = ref(false)
const showHistorySidebar = ref(false)

// 推荐内容状态
const recommendations = ref<RecommendResponse | null>(null)
const isLoadingRecommend = ref(false)

// 会话编辑状态
const editingConversationId = ref<number | null>(null)
const editingTitle = ref('')

// 计算属性
const messages = computed(() => chatStore.messages)
const isLoading = computed(() => chatStore.isLoading)
const isStreaming = computed(() => chatStore.isStreaming)
const mode = computed(() => chatStore.mode)
const hasMessages = computed(() => chatStore.hasMessages)
const errorMessage = computed(() => chatStore.errorMessage)

// 清理消息中的[[RECOMMEND]]标记
const cleanMessageContent = (content: string): string => {
  // 移除 [[RECOMMEND]] ... [[/RECOMMEND]] 标记块
  return content.replace(/\[\[RECOMMEND\]\][\s\S]*?\[\[\/RECOMMEND\]\]/g, '')
}

// 清理后的消息列表（用于显示）
const cleanedMessages = computed(() => {
  return messages.value.map(msg => ({
    ...msg,
    displayContent: msg.type === 'ai' ? cleanMessageContent(msg.content) : msg.content
  }))
})

// 构建聊天上下文
const buildChatContext = (): Record<string, any> => {
  const ctx: Record<string, any> = { page: route.name || '' }
  if (route.name === 'post-detail' && route.params.id) {
    const n = Number(route.params.id)
    if (Number.isFinite(n)) ctx.postId = n
  }
  return ctx
}

// 发送消息
const sendMessage = async () => {
  if (!chatInput.value.trim() || isLoading.value) return

  // 发送前清空之前的推荐内容
  recommendations.value = null

  const content = chatInput.value.trim()
  chatInput.value = ''

  await chatStore.sendMessage(content, buildChatContext())
  await scrollToBottom()
}

// 切换聊天模式
const setMode = (newMode: ChatMode) => {
  chatStore.setMode(newMode)
  isModeDropdownOpen.value = false
}

// 清空聊天记录
const clearHistory = async () => {
  await chatStore.clearHistory()
}

// 加载会话历史列表
const loadConversations = async () => {
  if (isLoadingHistory.value) return

  try {
    isLoadingHistory.value = true
    conversations.value = await ConversationService.list('general', 1, 50)
  } catch (error) {
    console.error('加载会话历史失败:', error)
  } finally {
    isLoadingHistory.value = false
  }
}

// 切换历史记录侧边栏
const toggleHistorySidebar = () => {
  showHistorySidebar.value = !showHistorySidebar.value
  if (showHistorySidebar.value && conversations.value.length === 0) {
    loadConversations()
  }
}

// 加载指定会话的消息
const loadConversation = async (conversationId: number) => {
  try {
    isLoadingHistory.value = true

    // 获取会话消息
    const messages = (await ConversationService.messages(conversationId, 1, 100)).reverse()

    // 清空当前消息
    chatStore.clearHistory()

    // 设置会话ID
    chatStore.conversationId = conversationId

    // 转换并添加消息到store
    messages.forEach(msg => {
      if (msg.role === 'user') {
        chatStore.addUserMessage(msg.content)
      } else if (msg.role === 'assistant') {
        chatStore.addAiMessage(msg.content)
      }
    })

    // 关闭侧边栏
    showHistorySidebar.value = false

    // 滚动到底部
    await scrollToBottom()
  } catch (error) {
    console.error('加载会话失败:', error)
  } finally {
    isLoadingHistory.value = false
  }
}

// 删除会话
const deleteConversation = async (conversationId: number, event: Event) => {
  event.stopPropagation()

  if (!confirm('确定要删除这个会话吗？')) return

  try {
    await ConversationService.remove(conversationId)
    // 从列表中移除
    conversations.value = conversations.value.filter(conv => conv.id !== conversationId)

    // 如果删除的是当前会话，清空聊天
    if (chatStore.conversationId === conversationId) {
      chatStore.clearHistory()
    }
  } catch (error) {
    console.error('删除会话失败:', error)
  }
}

// 开始编辑会话标题
const startEditTitle = (conversationId: number, currentTitle: string) => {
  editingConversationId.value = conversationId
  editingTitle.value = currentTitle || `会话 ${conversationId}`
  nextTick(() => {
    const input = document.querySelector('.title-edit-input') as HTMLInputElement
    if (input) {
      input.focus()
      input.select()
    }
  })
}

// 保存会话标题
const saveTitle = async (conversationId: number) => {
  if (!editingTitle.value.trim()) {
    cancelEditTitle()
    return
  }

  try {
    await ConversationService.rename(conversationId, editingTitle.value.trim())

    // 更新本地会话列表
    const conversation = conversations.value.find(c => c.id === conversationId)
    if (conversation) {
      conversation.title = editingTitle.value.trim()
    }

    cancelEditTitle()
  } catch (error) {
    console.error('重命名失败:', error)
    cancelEditTitle()
  }
}

// 取消编辑会话标题
const cancelEditTitle = () => {
  editingConversationId.value = null
  editingTitle.value = ''
}

// 格式化会话时间
const formatConversationTime = (dateString?: string) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  }
}

// 处理展开聊天框
const handleExpandChat = () => {
  emit('expand')
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

// 处理回车发送
const handleKeyPress = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

// 格式化时间
const formatTime = (date: Date) => {
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 点击外部关闭下拉菜单
const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (!target.closest('.mode-selector')) {
    isModeDropdownOpen.value = false
  }
}

// 监听滚动以自动隐藏下拉菜单
const handleScroll = () => {
  isModeDropdownOpen.value = false
}

// 解析[[RECOMMEND]]标记
const parseRecommendMarker = (content: string): object | null => {
  const regex = /\[\[RECOMMEND\]\]\s*([\s\S]*?)\s*\[\[\/RECOMMEND\]\]/g
  const match = regex.exec(content)
  if (match && match[1]) {
    try {
      return JSON.parse(match[1])
    } catch (e) {
      console.error('解析推荐标记失败:', e)
      return null
    }
  }
  return null
}

// 获取推荐内容
const fetchRecommendation = async (params: { type: string; keyword?: string; categoryId?: number; limit?: number }) => {
  if (isLoadingRecommend.value) return

  try {
    isLoadingRecommend.value = true
    const response = await Ai.recommend(params)
    recommendations.value = response
    await scrollToBottom()
  } catch (error) {
    console.error('获取推荐失败:', error)
  } finally {
    isLoadingRecommend.value = false
  }
}

// 检查消息是否包含推荐标记
const checkForRecommend = async (messageId: number) => {
  const message = messages.value.find(m => m.id === messageId)
  if (message && message.type === 'ai') {
    const params = parseRecommendMarker(message.content)
    if (params && typeof params === 'object') {
      // 确保流式结束再请求推荐
      if (message.isStreaming) {
        // 等待流结束
        const unwatch = watch(() => message.isStreaming, (streaming) => {
          if (!streaming) {
            unwatch()
            fetchRecommendation(params as any)
          }
        })
      } else {
        await fetchRecommendation(params as any)
      }
    }
  }
}

// 点击文章跳转到详情页
const handlePostClick = (postId: number) => {
  router.push(`/post/${postId}`)
}

// 点击分类跳转到分类页
const handleCategoryClick = (categoryId: number) => {
  router.push(`/category/${categoryId}`)
}

// 监听新消息，检查是否包含推荐标记
watch(() => chatStore.messages.length, async (newLen, oldLen) => {
  if (newLen > oldLen) {
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage.type === 'ai' && !lastMessage.isStreaming) {
      await checkForRecommend(lastMessage.id)
    }
  }
})

// 监听流式结束
watch(() => chatStore.isStreaming, async (streaming) => {
  if (!streaming && chatStore.messages.length > 0) {
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage && lastMessage.type === 'ai') {
      await checkForRecommend(lastMessage.id)
    }
  }
})

// 生命周期
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  if (chatContainer.value) {
    chatContainer.value.addEventListener('scroll', handleScroll)
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  if (chatContainer.value) {
    chatContainer.value.removeEventListener('scroll', handleScroll)
  }
  // 取消正在进行的流式请求
  AiStream.cancel()
})
</script>

<template>
  <div class="chat-box" :class="{ 'expanded': expanded }">
    <div class="chat-popup">
      <!-- 历史记录侧边栏 -->
      <div v-if="expanded" class="history-sidebar" :class="{ 'show': showHistorySidebar }">
        <div class="history-header">
          <h4>会话历史</h4>
          <button class="close-sidebar" @click="toggleHistorySidebar">✕</button>
        </div>

        <div class="history-content">
          <div v-if="isLoadingHistory" class="history-loading">
            <div class="loading-spinner"></div>
            <span>加载中...</span>
          </div>

          <div v-else-if="conversations.length === 0" class="history-empty">
            <p>暂无历史会话</p>
          </div>

          <div v-else class="conversation-list">
            <div v-for="conversation in conversations" :key="conversation.id" class="conversation-item"
              :class="{ 'active': chatStore.conversationId === conversation.id }"
              @click="loadConversation(conversation.id)">
              <div class="conversation-info">
                <div class="conversation-title">
                  <span v-if="editingConversationId !== conversation.id"
                    @click.stop="startEditTitle(conversation.id, conversation.title || '')" class="editable-title">
                    {{ conversation.title || `会话 ${conversation.id}` }}
                  </span>
                  <input v-else v-model="editingTitle" @blur="saveTitle(conversation.id)"
                    @keyup.enter="saveTitle(conversation.id)" @keyup.esc="cancelEditTitle()" class="title-edit-input" />
                </div>
                <div class="conversation-meta">
                  <span class="message-count">{{ conversation.messageCount }} 条消息</span>
                  <span class="conversation-time">{{ formatConversationTime(conversation.lastMessageAt) }}</span>
                </div>
              </div>
              <button class="delete-conversation" @click="deleteConversation(conversation.id, $event)" title="删除会话">
                🗑️
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 主聊天区域 -->
      <div class="chat-main" :class="{ 'with-sidebar': expanded && showHistorySidebar }">
        <!-- 聊天头部 -->
        <div class="chat-header">
          <div class="header-left">
            <h3 @click="handleExpandChat" class="expandable-title">纳西妲</h3>
            <div class="mode-indicator">
              <span :class="['mode-dot', mode]"></span>
              <span class="mode-text">{{ mode === 'stream' ? '流式' : '普通' }}</span>
            </div>
          </div>
          <div class="header-right">
            <!-- 历史记录按钮 (仅在扩展模式下显示) -->
            <button v-if="expanded" class="history-btn" @click="toggleHistorySidebar" title="查看会话历史">
              📜
            </button>

            <!-- 模式选择器 -->
            <div class="mode-selector">
              <button class="mode-toggle-btn" @click="isModeDropdownOpen = !isModeDropdownOpen" title="切换聊天模式">
                {{ mode === 'stream' ? '流式' : '普通' }}
                <span class="dropdown-arrow">▼</span>
              </button>
              <div v-show="isModeDropdownOpen" class="mode-dropdown">
                <button :class="['mode-option', { active: mode === 'stream' }]" @click="setMode('stream')">
                  <span class="mode-option-dot stream"></span>
                  流式模式（实时显示）
                </button>
                <button :class="['mode-option', { active: mode === 'normal' }]" @click="setMode('normal')">
                  <span class="mode-option-dot normal"></span>
                  普通模式（等待完整回复）
                </button>
              </div>
            </div>
            <button class="control-btn" @click="clearHistory" title="清空聊天">清空</button>
          </div>
        </div>

        <!-- 错误提示 -->
        <div v-if="errorMessage" class="error-banner">
          <span class="error-icon">⚠️</span>
          <span class="error-text">{{ errorMessage }}</span>
          <button class="error-close" @click="chatStore.errorMessage = ''">✕</button>
        </div>

        <!-- 聊天消息列表 -->
        <div ref="chatContainer" class="chat-messages">
          <div v-if="!hasMessages" class="empty-state">
            <p>你好！我是纳西妲，有什么我可以帮助你的吗？</p>
          </div>

          <div v-for="message in cleanedMessages" :key="message.id" :class="[
            'message',
            message.type,
            {
              'streaming': message.isStreaming,
              'error-message': message.isError
            }
          ]">
            <div class="message-content">
              <div class="message-text">
                <!-- User messages: plain text -->
                <div v-if="message.type === 'user'">
                  {{ message.content }}
                  <span v-if="message.isStreaming" class="streaming-indicator">▋</span>
                </div>
                <!-- AI messages: markdown rendering -->
                <div v-else>
                  <MarkdownRenderer :content="message.displayContent" :is-streaming="message.isStreaming || false" />
                  <span v-if="message.isStreaming" class="streaming-indicator">▋</span>
                </div>
              </div>
              <div class="message-time">{{ formatTime(message.timestamp) }}</div>
            </div>
          </div>

          <!-- 加载指示器 -->
          <div v-if="isLoading && !isStreaming" class="message ai loading">
            <div class="message-content">
              <div class="message-text">
                <span class="loading-dots">思考中</span>
              </div>
            </div>
          </div>

          <!-- 推荐内容 -->
          <div v-if="recommendations && recommendations.posts.length > 0" class="recommendation-section">
            <div class="recommendation-header">
              <span class="recommendation-icon">📚</span>
              <span class="recommendation-title">{{ recommendations.reason }}</span>
            </div>
            <div v-if="isLoadingRecommend" class="recommendation-loading">
              <span class="loading-spinner-small"></span>
              <span>加载推荐内容...</span>
            </div>
            <div v-else class="recommendation-list">
              <div
                v-for="post in recommendations.posts"
                :key="post.id"
                class="recommendation-item"
                @click="handlePostClick(post.id)"
              >
                <div class="recommendation-item-content">
                  <span class="recommendation-item-title">{{ post.title }}</span>
                  <div class="recommendation-item-meta">
                    <span v-if="post.categoryName" class="meta-tag">{{ post.categoryName }}</span>
                    <span class="meta-views">👁️ {{ post.viewCount }}</span>
                  </div>
                </div>
                <span class="recommendation-arrow">›</span>
              </div>
            </div>
            <div v-if="recommendations.category" class="recommendation-more">
              <span @click="handleCategoryClick(recommendations.category!.id)">
                查看 {{ recommendations.category.name }} 分类的全部文章 →
              </span>
            </div>
          </div>
        </div>

        <!-- 聊天输入区域 -->
        <div class="chat-input">
          <div class="input-container">
            <textarea v-model="chatInput" @keypress="handleKeyPress" placeholder="输入消息... "
              rows="1" :disabled="isLoading"></textarea>
            <button @click="sendMessage" :disabled="!chatInput.trim() || isLoading" class="send-btn" title="发送消息">
              {{ isLoading ? '发送中' : '发送' }}
            </button>
          </div>
        </div>
        <!-- 结束主聊天区域 -->
      </div>
    </div>
  </div>
</template>

<style scoped>
@use "@/assets/styles/tokens" as *;

.chat-box {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  position: relative;
    border: 4px solid var(--bg-soft);
  overflow: hidden;
  border-radius:16px;
  /* box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); */
}

.chat-box.expanded {
  border-radius: 16px;
  overflow: hidden;
}

.chat-popup {
  width: 100%;
  height: 100%;
  display: flex;
  position: relative;

}

.chat-main {
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: margin-right 0.3s ease;
}

.chat-main.with-sidebar {
  margin-right: 300px;
}

/* 历史记录侧边栏 */
.history-sidebar {
  position: absolute;
  top: 0;
  right: -300px;
  width: 300px;
  height: 100%;
  background: var(--bg-card);
  border-left: 1px solid var(--border-light);
  transition: right 0.3s ease;
  display: flex;
  flex-direction: column;
  z-index: 10;
}

.history-sidebar.show {
  right: 0;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-soft);
}

.history-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-title);
}

.close-sidebar {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-subtle);
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.close-sidebar:hover {
  background: var(--bg-hover);
  color: var(--text-main);
}

.history-content {
  width: 100%;
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.history-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--text-subtle);
  gap: 12px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border-light);
  border-top: 2px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.history-empty {
  width: 100%;
  text-align: center;
  padding: 40px 20px;
  color: var(--text-subtle);
}

.conversation-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.conversation-item:hover {
  background: var(--bg-hover);
  border-color: var(--border-light);
}

.conversation-item.active {
  background: var(--bg-active);
  border-color: var(--color-primary);
}

.conversation-info {
  flex: 1;
  min-width: 0;
}

.conversation-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-main);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.editable-title {
  cursor: text;
  padding: 2px 4px;
  border-radius: 3px;
  transition: background-color 0.2s;
  display: inline-block;
}

.editable-title:hover {
  background-color: rgba(0, 0, 0, 0.05);
}

.title-edit-input {
  width: 100%;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 2px 4px;
  font-size: 14px;
  font-weight: 500;
  outline: none;
  background: var(--bg-card);
  color: var(--text-main);
}

.title-edit-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.conversation-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-subtle);
}

.message-count {
  flex-shrink: 0;
}

.conversation-time {
  flex-shrink: 0;
  margin-left: 8px;
}

.delete-conversation {
  background: none;
  border: none;
  font-size: 14px;
  cursor: pointer;
  color: var(--text-subtle);
  padding: 4px;
  border-radius: 4px;
  opacity: 0;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.conversation-item:hover .delete-conversation {
  opacity: 1;
}

.delete-conversation:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error);
}

/* 历史记录按钮 */
.history-btn {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--text-main);
}

.history-btn:hover {
  background: var(--bg-active);
  border-color: var(--color-primary);
}

/* 聊天头部 */
.chat-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-soft);
}

.chat-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-title);
}

.expandable-title {
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: 4px;
  padding: 4px 8px;
  margin: -4px -8px;
}

.expandable-title:hover {
  background: var(--bg-hover);
  color: var(--color-primary);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 模式指示器 */
.mode-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--text-subtle);
}

.mode-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.mode-dot.stream {
  background-color: var(--color-success);
}

.mode-dot.normal {
  background-color: var(--color-primary);
}

/* 模式选择器 */
.mode-selector {
  position: relative;
}

.mode-toggle-btn {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-main);
}

.mode-toggle-btn:hover {
  background: var(--bg-active);
  border-color: var(--color-primary);
}

.dropdown-arrow {
  font-size: 10px;
  transition: transform 0.2s ease;
}

.mode-toggle-btn:hover .dropdown-arrow {
  transform: translateY(1px);
}

.mode-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  z-index: 1000;
  min-width: 200px;
}

.mode-option {
  width: 100%;
  padding: 10px 12px;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.875rem;
  color: var(--text-main);
}

.mode-option:hover {
  background: var(--bg-hover);
}

.mode-option.active {
  background: var(--bg-active);
  color: var(--color-primary);
}

.mode-option-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.mode-option-dot.stream {
  background-color: var(--color-success);
}

.mode-option-dot.normal {
  background-color: var(--color-primary);
}

/* 控制按钮 */
.control-btn {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--text-main);
}

.control-btn:hover {
  background: var(--bg-active);
  border-color: var(--color-primary);
}

/* 错误提示横幅 */
.error-banner {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1.5rem;
  background: rgba(239, 68, 68, 0.1);
  border-bottom: 1px solid var(--color-error);
  color: var(--color-error);
  font-size: 0.875rem;
}

.error-icon {
  flex-shrink: 0;
}

.error-text {
  flex: 1;
}

.error-close {
  background: none;
  border: none;
  color: var(--color-error);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
}

.error-close:hover {
  background: rgba(239, 68, 68, 0.2);
}

/* 消息列表 */
.chat-messages {
  width: 100%;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--bg-main);
  height: 100%;
  max-height: 1000px;
}

.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: var(--border-base);
  border-radius: 3px;
}

.empty-state {
  text-align: center;
  color: var(--text-subtle);
  font-size: 14px;
  margin-top: 40px;
}

.empty-state p {
  margin: 0;
  padding: 16px;
  background: var(--bg-card);
  border-radius: 12px;
  border: 1px dashed var(--border-light);
}

/* 消息样式 */
.message {
  display: flex;
  animation: messageSlideIn 0.4s ease-out;
}

.message.user {
  justify-content: flex-end;
}

.message.ai {
  justify-content: flex-start;
}

.message-content {
  max-width: 75%;
  display: flex;
  flex-direction: column;
}

.message-text {
  padding: 12px 16px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
  position: relative;
}

.message.user .message-text {
  background: var(--color-primary);
  color: #ffffff;
  border-bottom-right-radius: 6px;
}

.message.ai .message-text {
  background: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--border-light);
  border-bottom-left-radius: 6px;
}

.message.streaming .message-text {
  position: relative;
}

.streaming-indicator {
  display: inline-block;
  animation: blink 1s infinite;
  color: var(--color-primary);
  font-weight: bold;
}

.message.error-message .message-text {
  background: rgba(239, 68, 68, 0.1);
  border-color: var(--color-error);
  color: var(--color-error);
}

.message.loading .message-text {
  background: var(--bg-hover);
  color: var(--text-subtle);
}

.message-time {
  font-size: 11px;
  color: var(--text-subtle);
  margin-top: 4px;
  padding: 0 4px;
}

.message.user .message-time {
  text-align: right;
}

.message.ai .message-time {
  text-align: left;
}

.loading-dots::after {
  content: '';
  animation: loadingDots 1.5s infinite;
}

/* 输入区域 */
.chat-input {
  width: 100%;
  padding: 16px;
  background: var(--bg-soft);
  border-top: 1px solid var(--border-light);
  /* position: relative; */
}

/* 展开状态下确保输入框在最顶层 */
.chat-box.expanded .chat-input {
  position: relative;
}

/* 确保输入容器和文本区域也在最顶层 */
.input-container {
  position: relative;
  z-index: 1003;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.chat-input textarea {
  position: relative;
}

.input-container textarea {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  background: var(--bg-main);
  color: var(--text-main);
  min-height: 40px;
  max-height: 120px;
}

.input-container textarea:focus {
  border-color: var(--color-primary);
}

.input-container textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  min-width: 60px;
  min-height: 40px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--color-primary);
  color: white;
}

.send-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.send-btn:disabled {
  background: var(--bg-hover);
  color: var(--text-subtle);
  cursor: not-allowed;
  transform: none;
}

/* 动画 */
@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes blink {

  0%,
  50% {
    opacity: 1;
  }

  51%,
  100% {
    opacity: 0;
  }
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }

  50% {
    transform: scale(1.2);
    opacity: 0.7;
  }

  100% {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes loadingDots {
  0% {
    content: '';
  }

  25% {
    content: '.';
  }

  50% {
    content: '..';
  }

  75% {
    content: '...';
  }

  100% {
    content: '';
  }
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

/* 响应式设计 */
@include respond(md) {
  .message-content {
    max-width: 85%;
  }

  .chat-header {
    padding: 12px 16px;
  }

  .chat-messages {
    padding: 12px;
  }

  .chat-input {
    padding: 12px;
  }
}

/* 推荐内容样式 */
.recommendation-section {
  margin: 16px 0;
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.recommendation-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-main);
}

.recommendation-icon {
  font-size: 18px;
}

.recommendation-title {
  color: var(--color-primary);
}

.recommendation-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  color: var(--text-subtle);
  font-size: 13px;
}

.loading-spinner-small {
  width: 16px;
  height: 16px;
  border: 2px solid var(--border-light);
  border-top: 2px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.recommendation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recommendation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--bg-hover);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.recommendation-item:hover {
  background: var(--bg-active);
  border-color: var(--color-primary);
}

.recommendation-item-content {
  flex: 1;
  min-width: 0;
}

.recommendation-item-title {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recommendation-item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-subtle);
}

.meta-tag {
  padding: 2px 8px;
  background: var(--color-primary);
  color: white;
  border-radius: 4px;
  font-size: 11px;
}

.recommendation-arrow {
  font-size: 18px;
  color: var(--text-subtle);
  margin-left: 8px;
}

.recommendation-more {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
  text-align: center;
  font-size: 13px;
}

.recommendation-more span {
  color: var(--color-primary);
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.recommendation-more span:hover {
  opacity: 0.8;
}
</style>