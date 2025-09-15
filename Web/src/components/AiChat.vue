<script setup lang="ts">
import { Ai, type AiChatRequest, type AiChatResponse } from '@/services/ai.ts'
import { ref, nextTick, onUnmounted, onMounted } from 'vue'

/**
 * AI聊天组件
 * 作者：刘鑫
 * 时间：2025-01-27
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


// 消息ID计数器
let messageIdCounter = 0
let statusCheckInterval: number | null = null

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
  statusCheckInterval = window.setInterval(checkAiStatus, 60000)
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
  if (!chatInput.value.trim() || isLoading.value || isStreaming.value) {
    return
  }

  const userMessage = {
    id: ++messageIdCounter,
    type: 'user' as const,
    content: chatInput.value.trim(),
    timestamp: new Date(),
    status: 'sending' as const
  }

  messages.value.push(userMessage)
  const messageContent = chatInput.value.trim()
  chatInput.value = ''

  // 模拟发送状态变化
  setTimeout(() => {
    const messageIndex = messages.value.findIndex(msg => msg.id === userMessage.id)
    if (messageIndex !== -1) {
      messages.value[messageIndex].status = 'sent'
    }
  }, 500)

  // 滚动到底部
  await scrollToBottom()

  try {
    isLoading.value = true
    connectionStatus.value = 'connecting'
    errorMessage.value = ''

    const request: AiChatRequest = {
      message: messageContent
    }

    const response: AiChatResponse = await Ai.chat(request)

    // 添加AI回复消息
    const aiMessage = {
      id: ++messageIdCounter,
      type: 'ai' as const,
      content: response.message || '抱歉，我现在无法回复。',
      timestamp: new Date()
    }

    messages.value.push(aiMessage)
    connectionStatus.value = 'connected'
    retryCount.value = 0
    await scrollToBottom()

  } catch (error) {
    console.error('发送聊天消息失败:', error)
    connectionStatus.value = 'error'
    // 更新用户消息状态为失败
    const messageIndex = messages.value.findIndex(msg => msg.id === userMessage.id)
    if (messageIndex !== -1) {
      messages.value[messageIndex].status = 'failed'
    }
    handleChatError(error, messageContent)
    await scrollToBottom()
  } finally {
    isLoading.value = false
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

// 清空聊天记录
const clearChat = () => {
  messages.value = []
  messageIdCounter = 0
  errorMessage.value = ''
  connectionStatus.value = 'disconnected'
  retryCount.value = 0
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
      <!-- 聊天头部 -->
      <div class="chat-header">
        <div class="header-left">
          <h3>AI助手</h3>
          <!-- 连接状态指示器 -->
          <div class="connection-status" :class="connectionStatus" @click="refreshStatus" title="点击刷新状态">
            <div class="status-dot"></div>
            <span class="status-text">
              {{ connectionStatus === 'connected' ? '已连接' :
                 connectionStatus === 'connecting' ? '连接中...' :
                 connectionStatus === 'error' ? '连接错误' : '未连接' }}
            </span>
            <div v-if="connectionStatus === 'connecting'" class="status-spinner"></div>
          </div>
        </div>
        <div class="chat-controls">
          <button @click="clearChat" class="clear-btn" title="清空聊天">🗑️</button>
        </div>
      </div>

      <!-- 错误消息提示 -->
      <div v-if="errorMessage" class="error-banner">
        <span class="error-icon">⚠️</span>
        <span class="error-text">{{ errorMessage }}</span>
        <button @click="errorMessage = ''" class="error-close">✕</button>
      </div>

      <!-- 聊天消息列表 -->
      <div class="chat-messages" ref="chatContainer">
        <div v-if="messages.length === 0" class="empty-state">
          <p>👋 你好！我是AI助手，有什么可以帮助你的吗？</p>
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
            <div class="message-actions" v-if="message.isRetry">
              <button
                @click="retryMessage(message.retryData)"
                class="retry-btn"
                :disabled="isLoading || isStreaming"
              >
                🔄 重试
              </button>
            </div>
            <div class="message-meta">
              <div class="message-time">{{ formatTime(message.timestamp) }}</div>
              <div class="message-status" v-if="message.type === 'user'">
                <span v-if="message.status === 'sending'" class="status-icon sending" title="发送中">⏳</span>
                <span v-else-if="message.status === 'sent'" class="status-icon sent" title="已发送">✓</span>
                <span v-else-if="message.status === 'delivered'" class="status-icon delivered" title="已送达">✓✓</span>
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
  position: fixed;
  bottom: 20px;
  right: 20px;
  width: 380px;
  height: 500px;
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  font-family: system-ui, Avenir, Helvetica, Arial, sans-serif;
  z-index: 1000;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-box {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100%;
    height: 100%;
    border-radius: 0;
    border: none;
  }
}

@media (max-width: 480px) {
  .chat-box {
    width: 100vw;
    height: 100vh;
    max-width: none;
    max-height: none;
  }
}

.chat-popup {
  display: flex;
  flex-direction: column;
  height: 100%;
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
  background: var(--color-primary-soft);
}

.message.user.status-failed .message-text {
  background: var(--color-error);
  border: 1px solid var(--color-error-soft);
}

.message.user.status-sent .message-text {
  opacity: 1;
}

.message.user.status-delivered .message-text {
  opacity: 1;
  box-shadow: 0 0 0 1px var(--color-primary-soft);
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
  color: var(--color-danger);
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
  padding: 12px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 20px;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  max-height: 120px;
  min-height: 44px;
  background: var(--bg-main);
  color: var(--text-main);
  line-height: 1.5;
}

.input-container textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(107, 166, 197, 0.1);
  transform: translateY(-1px);
}

.input-container textarea:disabled {
  background: var(--bg-hover);
  color: var(--text-subtle);
  cursor: not-allowed;
  opacity: 0.7;
}

.input-container textarea::placeholder {
  color: var(--text-subtle);
  transition: color 0.2s ease;
}

.input-container textarea:focus::placeholder {
  color: var(--text-subtle);
}

/* 响应式输入区域 */
@media (max-width: 768px) {
  .chat-input {
    padding: 16px 12px;
    border-radius: 0;
  }

  .input-container textarea {
    font-size: 16px;
    min-height: 48px;
    padding: 14px 18px;
  }
}

@media (max-width: 480px) {
  .input-container {
    flex-direction: column;
    gap: 12px;
  }

  .input-buttons {
    align-self: stretch;
  }

  .input-buttons button {
    flex: 1;
  }
}

.input-buttons {
  display: flex;
  gap: 4px;
}

.send-btn, .stream-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn {
  background: var(--color-primary);
  color: white;
}

.send-btn:hover:not(:disabled) {
  background: var(--color-primary);
  transform: scale(1.05);
}

.stream-btn {
  background: var(--color-success);
  color: white;
}

.stream-btn:hover:not(:disabled) {
  background: var(--color-primary-light);
  transform: scale(1.05);
}

.send-btn:disabled, .stream-btn:disabled {
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

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes buttonPulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes shake {
  0%, 100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-2px);
  }
  75% {
    transform: translateX(2px);
  }
}

/* 加载动画优化 */
@keyframes typingDots {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-10px);
  }
}

.typing-indicator {
  display: inline-flex;
  gap: 2px;
}

.typing-indicator span {
  width: 4px;
  height: 4px;
  background: var(--color-primary);
  border-radius: 50%;
  animation: typingDots 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

/* 主题适配完成 - 使用项目统一的主题系统 */
/* 深色主题通过 .dark 类自动应用，无需额外配置 */

/* 性能优化 */
.chat-box * {
  box-sizing: border-box;
}

/* 滚动条优化 */
.chat-messages {
  scrollbar-width: thin;
  scrollbar-color: var(--border-base) transparent;
}

/* 触摸设备优化 */
@media (hover: none) and (pointer: coarse) {
  .clear-btn, .stop-btn {
    min-height: 44px;
    min-width: 44px;
  }

  .send-btn, .stream-btn {
    min-height: 48px;
    min-width: 48px;
  }

  .input-container textarea {
    font-size: 16px; /* 防止iOS缩放 */
  }
}

/* 高对比度模式支持 */
@media (prefers-contrast: high) {
  .chat-box {
    border-width: 2px;
  }

  .message-text {
    border-width: 2px;
  }

  .input-container textarea {
    border-width: 2px;
  }
}

/* 减少动画模式支持 */
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}

/* 焦点可见性优化 */
.clear-btn:focus-visible,
.stop-btn:focus-visible,
.send-btn:focus-visible,
.stream-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.input-container textarea:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
</style>
