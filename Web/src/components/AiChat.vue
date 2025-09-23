<script setup lang="ts">
import {Ai, type AiChatRequest, type AiChatResponse} from '@/services/ai.ts'
import {nextTick, onMounted, onUnmounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import { post } from '@/services/api'
import { usePostInteractionStore } from '@/stores/postInteraction'

/**
 * AI聊天组件
 * 作者：刘鑫
 * 时间：2025-09-27
 * 功能：支持普通聊天，显示历史记录
 */

// 消息类型定义
interface ChatMessage {
  id: number
  type: 'user' | 'ai'
  content: string
  timestamp: Date
  isStreaming?: boolean
  isError?: boolean
  isRetry?: boolean
  retryData?: any
  status?: 'sending' | 'sent' | 'delivered' | 'failed'
}
const route = useRoute()
const router = useRouter()
// 聊天相关状态
const chatInput = ref('')
const messages = ref<ChatMessage[]>([])
const isLoading = ref(false)
const isStreaming = ref(false)
const chatContainer = ref<HTMLElement>()
const connectionStatus = ref<'connected' | 'connecting' | 'disconnected' | 'error'>('disconnected')
const errorMessage = ref('')
const retryCount = ref(0)
const maxRetries = 3
let lastUserMessage = '' // 定义全局变量用于存储最后一条用户消息，用于意图检测

// 消息ID计数器
let messageIdCounter = 0
let statusCheckInterval: number | null = null


//聊天显示面板
let isActive = ref(false)

const emit = defineEmits<{
  (e: "status-change", value: boolean): void
}>()

// 检查AI服务状态
const checkAiStatus = async () => {
  try {
    if (connectionStatus.value === 'connecting') return

    connectionStatus.value = 'connecting'
    const response = await Ai.chatStatus()
    console.log('AI服务状态:', response);


    if (response) {
      connectionStatus.value = 'connected'
      errorMessage.value = ''
    } else {
      connectionStatus.value = 'error'
      errorMessage.value = 'AI服务连接失败'
    }
  } catch (error) {
    console.error('检查AI服务状态失败:', error)
    connectionStatus.value = 'error'
    errorMessage.value = '无法连接到AI服务'
  }
}

// 开始定时检查状态
const startStatusCheck = () => {
  // 立即检查一次
  checkAiStatus()
  // 每60秒检查一次
  // statusCheckInterval = window.setInterval(checkAiStatus, 60000)
}

// 停止定时检查
const stopStatusCheck = () => {
  if (statusCheckInterval) {
    clearInterval(statusCheckInterval)
    statusCheckInterval = null
  }
}

// 手动刷新状态
const refreshStatus = async () => {
  await checkAiStatus()
}

// 发送普通聊天消息
const sendChat = async () => {
  if (!chatInput.value.trim() || isLoading.value || isStreaming.value) return

  const text = chatInput.value.trim()
  // 记录最后一条用户输入（用于客户端保护）
  lastUserMessage = text

  // 推送用户消息
  const msgId = ++messageIdCounter
  messages.value.push({
    id: msgId,
    type: 'user',
    content: text,
    timestamp: new Date(),
    status: 'sending'
  })
  chatInput.value = ''

  // 显示“思考中”
  isLoading.value = true
  await scrollToBottom()

  try {
    const context: Record<string, any> = {
      page: route.name || '',
    }
    if (route.name === 'post-detail' && route.params.id) {
      const n = Number(route.params.id)
      if (Number.isFinite(n)) {
        context.postId = n
      }
    }

    const req: AiChatRequest = { message: text, context }
    const resp: AiChatResponse = await Ai.chat(req)

    // 更新用户消息状态
    const idx = messages.value.findIndex(m => m.id === msgId)
    if (idx > -1) messages.value[idx].status = 'delivered'

    // 展示AI回复
    if (resp?.message) {
      messages.value.push({
        id: ++messageIdCounter,
        type: 'ai',
        content: resp.message,
        timestamp: new Date()
      })
    }

    // 动作分发（默认none不执行）
    const action = resp?.action || 'none'
    const meta = resp?.metadata || {}
    await dispatchAction(action, meta)
  } catch (err) {
    // 标记失败
    const idx = messages.value.findIndex(m => m.id === msgId)
    if (idx > -1) messages.value[idx].status = 'failed'
    handleChatError(err, text)
  } finally {
    isLoading.value = false
    await scrollToBottom()
  }
}


// 根据AI返回的动作执行页面跳转或业务操作
const dispatchAction = async (action: string, meta: Record<string, any> = {}) => {
  try {
    const normalizeId = (): number | undefined => {
      const raw = meta.postId ?? meta.articleId ?? meta.id ?? (route.name === 'post-detail' ? route.params.id : undefined)
      const n = Number(raw)
      return Number.isFinite(n) ? n : undefined
    }
    const hasLikeIntent = (text: string) => /((给)?(这篇)?(文|文章)?点个?赞|点赞|喜欢|like)/i.test(text)
    const hasFavoriteIntent = (text: string) => /(收藏|加(个)?星|favorite|mark)/i.test(text)
    console.log('当前参数:', normalizeId())
    switch (action) {
      // 导航类
      case 'go_home':
        console.log("触发动作，跳转首页")
        await router.push({ name: 'home' })
        break
      // 文章操作类
      case 'like_post': {
        // 客户端保护：只有当用户输入里出现明显的点赞意图时才执行
        if (!hasLikeIntent(lastUserMessage)) {
          messages.value.push({
            id: ++messageIdCounter,
            type: 'ai',
            content: `已为您解析到可能的操作：点赞。但未检测到明确的“点赞”指令，因此未执行。如需点赞请明确说明。`,
            timestamp: new Date()
          })
          break
        }
        const likePostId = normalizeId()
        if (likePostId) {
          console.log("触发动作，点赞文章", likePostId)
          await likePost(likePostId)
          // 同步全局交互状态
          usePostInteractionStore().toggleLike(likePostId)
          messages.value.push({
            id: ++messageIdCounter,
            type: 'ai',
            content: `✅ 已为您点赞文章`,
            timestamp: new Date()
          })
        } else {
          console.warn("点赞失败：未找到文章ID")
          messages.value.push({
            id: ++messageIdCounter,
            type: 'ai',
            content: `❌ 点赞失败：未找到文章ID`,
            timestamp: new Date(),
            isError: true
          })
        }
        break
      }
      case 'favorite_post': {
        // 客户端保护：只有当用户输入里出现明显的收藏意图时才执行
        if (!hasFavoriteIntent(lastUserMessage)) {
          messages.value.push({
            id: ++messageIdCounter,
            type: 'ai',
            content: `已为您解析到可能的操作：收藏。但未检测到明确的“收藏”指令，因此未执行。如需收藏请明确说明。`,
            timestamp: new Date()
          })
          break
        }
        const favoritePostId = normalizeId()
        if (favoritePostId) {
          console.log("触发动作，收藏文章", favoritePostId)
          await favoritePost(favoritePostId)
          // 同步全局交互状态
          usePostInteractionStore().toggleFavorite(favoritePostId)
          messages.value.push({
            id: ++messageIdCounter,
            type: 'ai',
            content: `✅ 已为您收藏文章`,
            timestamp: new Date()
          })
        } else {
          console.warn("收藏失败：未找到文章ID")
          messages.value.push({
            id: ++messageIdCounter,
            type: 'ai',
            content: `❌ 收藏失败：未找到文章ID`,
            timestamp: new Date(),
            isError: true
          })
        }
        break
      }
      case 'none':
        break
      default:
        console.debug('未识别的动作：', action, meta)
        // 给用户一个温柔提示
        messages.value.push({
          id: ++messageIdCounter,
          type: 'ai',
          content: `我收到一个暂不支持的动作：${action}`,
          timestamp: new Date()
        })
    }
  } catch (err: any) {
    console.warn('动作执行异常:', err)
    messages.value.push({
      id: ++messageIdCounter,
      type: 'ai',
      content: `❌ 动作执行失败：${err?.message || '未知错误'}`,
      timestamp: new Date(),
      isError: true
    })
  } finally {
    await scrollToBottom()
  }
}

// 点赞文章
const likePost = async (postId: number) => {
  try {
    // 调用点赞API
    const response = await post(`/posts/${postId}/like`, {})
    if (response.code === 200) {
      return true
    } else {
      throw new Error(response.message || '点赞失败')
    }
  } catch (error) {
    console.error('点赞失败:', error)
    throw error
  }
}

// 收藏文章
const favoritePost = async (postId: number) => {
  try {
    // 调用收藏API
    const response = await post(`/posts/${postId}/favorite`, {})
    if (response.code === 200) {
      return true
    } else {
      throw new Error(response.message || '收藏失败')
    }
  } catch (error) {
    console.error('收藏失败:', error)
    throw error
  }
}


// 处理聊天错误
const handleChatError = (error: any, originalMessage: string) => {
  let errorMsg = '发送消息失败'

  if (error.code === 'NETWORK_ERROR' || !navigator.onLine) {
    errorMsg = '网络连接失败，请检查网络设置'
  } else if (error.status === 429) {
    errorMsg = '请求过于频繁，请稍后再试'
  } else if (error.status === 500) {
    errorMsg = '服务器内部错误，请稍后重试'
  } else if (error.status === 503) {
    errorMsg = '服务暂时不可用，请稍后重试'
  }

  errorMessage.value = errorMsg

  // 添加错误消息到聊天记录
  const errorChatMessage = {
    id: ++messageIdCounter,
    type: 'ai' as const,
    content: `❌ ${errorMsg}`,
    timestamp: new Date(),
    isError: true
  }
  messages.value.push(errorChatMessage)

  // 如果重试次数未达到上限，显示重试选项
  if (retryCount.value < maxRetries) {
    showRetryOption(originalMessage)
  }
}

// 显示重试选项
const showRetryOption = (originalMessage: string) => {
  const retryMessage = {
    id: ++messageIdCounter,
    type: 'ai' as const,
    content: `🔄 点击重试发送消息 (${retryCount.value + 1}/${maxRetries})`,
    timestamp: new Date(),
    isRetry: true,
    retryData: { message: originalMessage }
  }
  messages.value.push(retryMessage)
}

// 重试发送消息
const retryMessage = async (retryData: any) => {
  retryCount.value++
  chatInput.value = retryData.message

  // 移除重试消息
  const retryIndex = messages.value.findIndex(msg => msg.isRetry && msg.retryData?.message === retryData.message)
  if (retryIndex > -1) {
    messages.value.splice(retryIndex, 1)
  }

  // 重新发送
  await sendChat()
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}
//emit("status-change", isActive.value)
// 清空聊天记录
const clearChat = async () => {
  try {
      //隐藏聊天框
      isActive.value = false
      emit("status-change", isActive.value)
      messages.value = []
      messageIdCounter = 0
      errorMessage.value = ''
      connectionStatus.value = 'disconnected'
      retryCount.value = 0
    // // 调用后端API清空聊天记忆
    // const response = await Ai.clearChatMemory()
    
    // if (response.success) {
    //   // 后端清空成功，清空前端显示
    //   isActive.value = false
    //   emit("status-change", isActive.value)
    //   messages.value = []
    //   messageIdCounter = 0
    //   errorMessage.value = ''
    //   connectionStatus.value = 'disconnected'
    //   retryCount.value = 0
      
    //   console.log('聊天记忆已清空')
    // } else {
    //   errorMessage.value = response.message || '清空聊天记忆失败'
    // }
  } catch (error) {
    console.error('清空聊天记忆失败:', error)
    errorMessage.value = '清空聊天记忆失败，请稍后重试'
  }
}

// 检查网络状态
const checkNetworkStatus = () => {
  if (typeof navigator !== 'undefined' && navigator.onLine !== undefined) {
    if (!navigator.onLine) {
      connectionStatus.value = 'disconnected'
      errorMessage.value = '网络连接已断开'
    } else if (connectionStatus.value === 'disconnected') {
      connectionStatus.value = 'connected'
      errorMessage.value = ''
    }
  }
}

// 安全获取网络状态
const isOnline = () => {
  return typeof navigator !== 'undefined' && navigator.onLine !== undefined ? navigator.onLine : true
}

// 监听网络状态变化
if (typeof window !== 'undefined') {
  window.addEventListener('online', checkNetworkStatus)
  window.addEventListener('offline', checkNetworkStatus)
}



// 处理回车发送
const handleKeyPress = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    //通知父组件已经开始聊天了
    isActive.value = true
    emit("status-change", isActive.value)
    sendChat()
  }
}

// 格式化时间
const formatTime = (date: Date) => {
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 组件挂载时启动状态检查
onMounted(() => {
  startStatusCheck()
})

// 组件卸载时清理资源
onUnmounted(() => {
  stopStatusCheck()

  // 清理网络状态监听器
  if (typeof window !== 'undefined') {
    window.removeEventListener('online', checkNetworkStatus)
    window.removeEventListener('offline', checkNetworkStatus)
  }
})
</script>

<template>
  <div class="chat-box">
    <div class="chat-popup">
      <div v-show="isActive" class="chat-top">
        <!-- 聊天头部 -->
        <div class="chat-header">
          <div class="header-left">
            <h3>AI助手</h3>
            <!-- 连接状态指示器 -->
            <div :class="connectionStatus" class="connection-status" title="点击刷新状态" @click="refreshStatus">
              <div class="status-dot"></div>
              <span class="status-text">
              {{ connectionStatus === 'connected' ? '已连接' :
                  connectionStatus === 'connecting' ? '连接中...' :
                      connectionStatus === 'error' ? '连接错误' : '未连接'
                }}
            </span>
              <div v-if="connectionStatus === 'connecting'" class="status-spinner"></div>
            </div>
          </div>
          <div class="chat-controls">
            <button class="clear-btn" title="清空聊天" @click="clearChat">隐藏</button>
          </div>
        </div>

        <!-- 错误消息提示 -->
        <div v-if="errorMessage" class="error-banner">
          <span class="error-icon">⚠️</span>
          <span class="error-text">{{ errorMessage }}</span>
          <button class="error-close" @click="errorMessage = ''">✕</button>
        </div>

        <!-- 聊天消息列表 -->
        <div ref="chatContainer" class="chat-messages">
          <div v-if="messages.length === 0" class="empty-state">
            <p>👋 你好！欢迎来到我的博客，有什么可以我帮助你的吗？我可以为你总结文章，跳转页面哦！</p>
            <div v-if="!isOnline()" class="offline-notice">
              <span>📶</span>
              <span>当前网络不可用</span>
            </div>
          </div>

          <div
              v-for="message in messages"
              :key="message.id"
              :class="['message', message.type, {
            'streaming': message.isStreaming,
            'error-message': message.isError,
            'retry-message': message.isRetry,
            'status-sending': message.status === 'sending',
            'status-sent': message.status === 'sent',
            'status-delivered': message.status === 'delivered',
            'status-failed': message.status === 'failed'
          }]"
          >
            <div class="message-content">
              <div class="message-text">
                {{ message.content }}
                <span v-if="message.isStreaming" class="streaming-indicator">▋</span>
              </div>
              <div v-if="message.isRetry" class="message-actions">
                <button
                    :disabled="isLoading || isStreaming"
                    class="retry-btn"
                    @click="retryMessage(message.retryData)"
                >
                  🔄 重试
                </button>
              </div>
              <div class="message-meta">
                <div class="message-time">{{ formatTime(message.timestamp) }}</div>
                <div v-if="message.type === 'user'" class="message-status">
                  <span v-if="message.status === 'sending'" class="status-icon sending" title="发送中">⏳</span>
                  <span v-else-if="message.status === 'sent'" class="status-icon sent" title="已发送">✓</span>
                  <span v-else-if="message.status === 'delivered'" class="status-icon delivered"
                        title="已送达">✓✓</span>
                  <span v-else-if="message.status === 'failed'" class="status-icon failed" title="发送失败">✗</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 加载指示器 -->
          <div v-if="isLoading" class="message ai">
            <div class="message-content">
              <div class="message-text loading">
                <span class="loading-dots">思考中</span>
              </div>
            </div>
          </div>
        </div>
      </div>


      <!-- 聊天输入区域 -->
      <div class="chat-input">
        <div class="input-container">
          <textarea
            v-model="chatInput"
            @keypress="handleKeyPress"
            placeholder="(Enter发送，Shift+Enter换行)"
            rows="1"
            :disabled="isLoading || isStreaming"
          ></textarea>
          <div class="input-buttons">
            <button
              @click="sendChat"
              :disabled="!chatInput.trim() || isLoading || isStreaming"
              class="send-btn"
              title="发送普通消息"
            >
              发送
            </button>

          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/**
 * AI聊天组件样式
 * 作者：刘鑫
 * 时间：2025-01-27
 * 功能：现代化聊天界面设计，集成项目主题系统
 */

.chat-box {
  width: 100%;
  height: auto;
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  z-index: 11;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.chat-popup {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 聊天头部 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-soft);
  background: var(--bg-soft);
  border-radius: 16px 16px 0 0;
}

.chat-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-title);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

/* 连接状态指示器 */
.connection-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-subtle);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: background-color 0.3s ease;
}

.connection-status.connected .status-dot {
  background-color: var(--color-success);
  animation: pulse 2s infinite;
}

.connection-status.connecting .status-dot {
  background-color: var(--color-warning);
  animation: blink 1s infinite;
}

.connection-status.error .status-dot {
  background-color: var(--color-error);
}

.connection-status.disconnected .status-dot {
  background-color: var(--text-muted);
}

.connection-status {
  cursor: pointer;
  user-select: none;
  transition: opacity 0.2s ease;
}

.connection-status:hover {
  opacity: 0.8;
}

.connection-status:active {
  transform: scale(0.95);
}

.status-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--border-base);
  border-top: 2px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.chat-controls {
  display: flex;
  gap: 8px;
}

.clear-btn, .stop-btn {
  background: none;
  border: none;
  font-size: 16px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background-color 0.2s;
  color: var(--text-subtle);
}

.clear-btn:hover, .stop-btn:hover {
  background: var(--bg-hover);
  color: var(--text-main);
}

/* 错误提示横幅 */
.error-banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1.5rem;
  background: rgba(239, 68, 68, 0.1);
  border-bottom: 1px solid var(--color-error);
  color: var(--color-error);
  font-size: 0.875rem;
  animation: slideDown 0.3s ease-out;
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
  transition: background-color 0.2s ease;
}

.error-close:hover {
  background: rgba(239, 68, 68, 0.2);
}

/* 消息列表 */
.chat-messages {
  width: 100%;
  min-height: 350px;
  max-height: 500px;
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--bg-main);
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

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: var(--border-strong);
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
  border: 1px dashed var(--border-soft);
}

.offline-notice {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
  padding: 0.5rem 1rem;
  background: rgba(245, 158, 11, 0.1);
  color: var(--color-warning);
  border-radius: 8px;
  font-size: 0.875rem;
}

/* 消息样式 */
.message {
  display: flex;
  margin-bottom: 8px;
  animation: messageSlideIn 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: 0;
  animation-fill-mode: forwards;
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
  transform: translateY(10px);
  animation: messageTextSlideIn 0.4s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

/* 响应式消息气泡 */
@media (max-width: 768px) {
  .message-content {
    max-width: 85%;
  }

  .message-text {
    font-size: 16px;
    padding: 14px 18px;
  }
}

@media (max-width: 480px) {
  .message-content {
    max-width: 90%;
  }

  .message-text {
    font-size: 16px;
    line-height: 1.6;
  }
}

.message.user .message-text {
  background: var(--color-primary);
  color: #ffffff;
  border-bottom-right-radius: 6px;
}

.message.user.status-sending .message-text {
  opacity: 0.7;
  background: var(--color-primary);
}

.message.user.status-failed .message-text {
  background: var(--color-error);
  border: 1px solid var(--color-primary);
}

.message.user.status-sent .message-text {
  opacity: 1;
}

.message.user.status-delivered .message-text {
  opacity: 1;
  box-shadow: 0 0 0 1px var(--color-primary);
}

.message.ai .message-text {
  background: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--border-soft);
  border-bottom-left-radius: 6px;
}

.message.error-message .message-text {
  background: rgba(239, 68, 68, 0.1);
  border-color: var(--color-error);
  color: var(--color-error);
}

.message.retry-message .message-text {
  background: rgba(245, 158, 11, 0.1);
  border-color: var(--color-warning);
  color: var(--color-warning);
}

/* 消息操作按钮 */
.message-actions {
  margin-top: 0.5rem;
  display: flex;
  gap: 0.5rem;
}

.retry-btn {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.375rem 0.75rem;
  background: var(--color-warning);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.retry-btn:hover:not(:disabled) {
  background: #d97706;
  transform: translateY(-1px);
}

.retry-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.message-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
  gap: 8px;
}

.message-time {
  font-size: 11px;
  color: var(--text-subtle);
  padding: 0 4px;
}

.message-status {
  display: flex;
  align-items: center;
}

.status-icon {
  font-size: 0.75rem;
  margin-left: 4px;
  transition: all 0.2s ease;
}

.status-icon.sending {
  color: var(--color-warning);
  animation: pulse 1.5s infinite;
}

.status-icon.sent {
  color: var(--color-success);
}

.status-icon.delivered {
  color: var(--color-primary);
  font-weight: bold;
}

.status-icon.failed {
  color: var(--color-error);
  cursor: pointer;
}

.status-icon.failed:hover {
  transform: scale(1.1);
}

.message.user .message-time {
  text-align: right;
}

.message.ai .message-time {
  text-align: left;
}

/* 加载状态 */
.message-text.loading {
  background: var(--bg-hover);
  color: var(--text-subtle);
  border: 1px solid var(--border-soft);
}

.loading-dots::after {
  content: '';
  animation: loadingDots 1.5s infinite;
}

/* 输入区域 */
.chat-input {
  padding: 16px;
  border-top: 1px solid var(--border-soft);
  background: var(--bg-card);
  border-radius: 0 0 16px 16px;
}

.input-container {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  position: relative;
}

.input-container textarea {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 5px;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  background: var(--bg-main);
  color: var(--text-main);
  min-height: 40px;
}


.input-buttons {
  display: flex;
  gap: 4px;
}

.send-btn {
  width: 60px;
  min-height: 40px;
  border: none;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: white;
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
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes messageTextSlideIn {
  from {
    transform: translateY(10px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
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

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-100%);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes loadingDots {
  0% { content: ''; }
  25% { content: '.'; }
  50% { content: '..'; }
  75% { content: '...'; }
  100% { content: ''; }
}




/* 性能优化 */
.chat-box * {
  box-sizing: border-box;
}

/* 滚动条优化 */
.chat-messages {
  scrollbar-width: thin;
  scrollbar-color: var(--border-base) transparent;
}

</style>

