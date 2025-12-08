<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useChatStore, type ChatMessage, type ChatMode } from '@/stores/chat'
import { AiStream } from '@/services/ai'

/**
 * 简化版AI聊天组件
 * 作者：刘鑫
 * 时间：2025-01-27
 * 功能：专注于聊天功能，支持流式和普通模式切换，使用Pinia管理状态
 */

const route = useRoute()
const chatStore = useChatStore()

// 组件本地状态
const chatInput = ref('')
const chatContainer = ref<HTMLElement>()
const isModeDropdownOpen = ref(false)

// 计算属性
const messages = computed(() => chatStore.messages)
const isLoading = computed(() => chatStore.isLoading)
const isStreaming = computed(() => chatStore.isStreaming)
const mode = computed(() => chatStore.mode)
const hasMessages = computed(() => chatStore.hasMessages)
const errorMessage = computed(() => chatStore.errorMessage)

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
  <div class="chat-box">
    <div class="chat-popup">
      <!-- 聊天头部 -->
      <div class="chat-header">
        <div class="header-left">
          <h3>纳西妲</h3>
          <div class="mode-indicator">
            <span :class="['mode-dot', mode]"></span>
            <span class="mode-text">{{ mode === 'stream' ? '流式' : '普通' }}</span>
          </div>
        </div>
        <div class="header-right">
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

        <div v-for="message in messages" :key="message.id" :class="[
          'message',
          message.type,
          {
            'streaming': message.isStreaming,
            'error-message': message.isError
          }
        ]">
          <div class="message-content">
            <div class="message-text">
              {{ message.content }}
              <span v-if="message.isStreaming" class="streaming-indicator">▋</span>
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
      </div>

      <!-- 聊天输入区域 -->
      <div class="chat-input">
        <div class="input-container">
          <textarea v-model="chatInput" @keypress="handleKeyPress" placeholder="输入消息... (Enter发送，Shift+Enter换行)"
            rows="1" :disabled="isLoading"></textarea>
          <button @click="sendMessage" :disabled="!chatInput.trim() || isLoading" class="send-btn" title="发送消息">
            {{ isLoading ? '发送中' : '发送' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-box {
  width: 100%;
  height: 100%;
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
  border: 1px solid var(--border-soft);
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
  border: 1px solid var(--border-soft);
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
  border: 1px solid var(--border-soft);
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
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--bg-main);
  min-height: 350px;
  max-height: 500px;
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
  border: 1px dashed var(--border-soft);
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
  border: 1px solid var(--border-soft);
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
  padding: 16px;
  border-top: 1px solid var(--border-soft);
  background: var(--bg-card);
  border-radius: 0 0 16px 16px;
}

.input-container {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-container textarea {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid var(--border-soft);
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

/* 响应式设计 */
@media (max-width: 768px) {
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
</style>