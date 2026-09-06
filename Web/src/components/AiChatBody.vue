<script setup lang="ts">
import { nextTick, ref } from 'vue'
import type { ChatMessage } from '@/stores/chat'
import MarkdownRenderer from './MarkdownRenderer.vue'
import Icon from './Icon.vue'

type DisplayMessage = ChatMessage & {
  displayContent: string
}

const props = defineProps<{
  messages: DisplayMessage[]
  hasMessages: boolean
  isLoading: boolean
  isStreaming: boolean
  errorMessage: string
  isGuestMode: boolean
  guestBannerText: string
  expanded?: boolean
}>()

const emit = defineEmits<{
  clearError: []
  openPost: [postId: number]
}>()

const chatContainer = ref<HTMLElement | null>(null)
const FOLLOW_THRESHOLD = 48
let interactionVersion = 0
let followingLatest = true

const updateFollowingState = () => {
  const el = chatContainer.value
  if (!el) return
  followingLatest = el.scrollHeight - el.scrollTop - el.clientHeight <= FOLLOW_THRESHOLD
}

const handleUserWheel = (deltaY: number) => {
  const el = chatContainer.value
  if (!el) return
  interactionVersion++
  if (deltaY < 0) followingLatest = false
  el.scrollTop += deltaY
  requestAnimationFrame(updateFollowingState)
}

const handleTouchStart = () => {
  interactionVersion++
}

type FollowSnapshot = { following: boolean; interactionVersion: number }

const captureFollowSnapshot = (): FollowSnapshot => ({ following: followingLatest, interactionVersion })

const followLatestIfUnchanged = async (snapshot: FollowSnapshot) => {
  await nextTick()
  if (snapshot.following && snapshot.interactionVersion === interactionVersion && chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    followingLatest = true
  }
}

const getScrollElement = () => chatContainer.value

defineExpose({
  scrollToBottom,
  getScrollElement,
  handleUserWheel,
  captureFollowSnapshot,
  followLatestIfUnchanged
})
</script>

<template>
  <div class="chat-body" :class="{ expanded, compact: !expanded }">
    <div v-if="isGuestMode" class="guest-banner">
      {{ guestBannerText }}
    </div>

    <div v-if="errorMessage" class="error-banner">
      <span class="error-icon"><Icon name="warning" /></span>
      <span class="error-text">{{ errorMessage }}</span>
      <button class="error-close" @click="emit('clearError')"><Icon name="close" /></button>
    </div>

    <div
      ref="chatContainer"
      class="chat-messages"
      data-lenis-prevent
      @scroll.passive="updateFollowingState"
      @touchstart.passive="handleTouchStart"
    >
      <div v-if="!hasMessages" class="empty-state text-sm">
        <p>你好！我是纳西妲，有什么我可以帮助你的吗？</p>
      </div>

      <div
        v-for="message in messages"
        :key="message.id"
        :class="[
          'message',
          message.type,
          {
            streaming: message.isStreaming && message.type === 'ai',
            'error-message': message.isError
          }
        ]"
      >
        <div class="message-content">
          <div class="message-text">
            <div v-if="message.type === 'user'">
              {{ message.content }}
            </div>

            <div v-else-if="message.isThinking" class="thinking-message">
              <span class="thinking-dot"></span>
              <span class="thinking-dot"></span>
              <span class="thinking-dot"></span>
              <span class="thinking-label">思考中</span>
            </div>

            <div v-else>
              <MarkdownRenderer :content="message.displayContent" :is-streaming="message.isStreaming || false" />

              <div v-if="message.type === 'ai' && !message.isStreaming" class="inline-recommendation">
                <div class="recommendation-section" v-if="message.articleResults?.length">
                  <div class="recommendation-header">
                    <span class="recommendation-icon"><Icon name="book" /></span>
                    <span class="recommendation-title">{{ message.articleResultReason || '这些文章可以继续阅读' }}</span>
                  </div>
                  <div class="recommendation-list">
                    <div
                      v-for="post in message.articleResults"
                      :key="post.id"
                      class="recommendation-item"
                      @click="emit('openPost', post.id)"
                    >
                      <div class="recommendation-item-content">
                        <span class="recommendation-item-title">{{ post.title }}</span>
                        <div class="recommendation-item-meta">
                          <span v-if="post.categoryName" class="meta-tag">{{ post.categoryName }}</span>
                          <span v-if="post.reason" class="meta-reason">{{ post.reason }}</span>
                        </div>
                      </div>
                      <span class="recommendation-arrow">›</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="message-time">
            {{ message.timestamp.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
.chat-body {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chat-body.expanded {
  background: var(--bg-card);
  border-radius: 24px;
  overflow: hidden;
}

@include respond(md) {
  .chat-body.expanded {
    border-radius: 0;
  }
}

.chat-body.compact {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-top: none;
  border-bottom: none;
}

.guest-banner {
  padding: 10px 16px;
  background: var(--bg-warning);
  border-bottom: 1px solid var(--border-light);
  color: var(--text-subtle);
  font-size: 13px;
}

.chat-body.expanded .guest-banner {
  background: var(--bg-warning);
  border-bottom-color: var(--border-light);
}

.error-banner {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1.5rem;
  background: var(--bg-error);
  border-bottom: 1px solid var(--color-error);
  color: var(--color-error);
  font-size: 0.875rem;
}

.chat-body.expanded .error-banner {
  background: var(--bg-error);
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
  background: var(--bg-error);
}

.chat-messages {
  width: 100%;
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.chat-body.expanded .empty-state p {
  background: var(--bg-card);
}

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
  max-width: 78%;
  display: flex;
  flex-direction: column;
}

.message-text {
  padding: 12px 16px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  position: relative;
}

.message.user .message-text {
  background: var(--color-primary);
  color: var(--text-on-primary);
  border-bottom-right-radius: 6px;
}

.message.ai .message-text {
  background: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--border-light);
  border-bottom-left-radius: 6px;
  box-shadow: var(--shadow-xl);
}

.chat-body.expanded .message.ai .message-text {
  background: var(--bg-card);
  border-color: var(--border-light);
  box-shadow: var(--shadow-xl);
}

.thinking-message {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  min-height: 1.5rem;
}

.thinking-dot {
  width: 0.46rem;
  height: 0.46rem;
  border-radius: 999px;
  background: var(--color-primary);
  animation: thinkingPulse 1.2s ease-in-out infinite;
}

.thinking-dot:nth-child(2) {
  animation-delay: 0.16s;
}

.thinking-dot:nth-child(3) {
  animation-delay: 0.32s;
}

.thinking-label {
  margin-left: 0.25rem;
  color: var(--text-subtle);
  font-size: 13px;
}

.message.error-message .message-text {
  background: var(--bg-error);
  border-color: var(--color-error);
  color: var(--color-error);
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

.chat-body.compact .chat-messages {
  padding: 14px;
  gap: 10px;
}

.chat-body.compact .message-content {
  max-width: 86%;
}

.chat-body.compact .message-text {
  padding: 11px 14px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.55;
}

.chat-body.compact .guest-banner {
  padding: 8px 14px;
  font-size: 12px;
}

.recommendation-section {
  margin: 16px 0 0;
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  animation: slideUp 0.3s ease-out;
}

.chat-body.expanded .recommendation-section {
  background: var(--bg-card);
}

.chat-body.compact .recommendation-section {
  margin: 12px 0 0;
  padding: 12px;
  border-radius: 10px;
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
  color: var(--text-on-primary);
  border-radius: 4px;
  font-size: 11px;
}

.meta-reason {
  min-width: 0;
  color: var(--text-subtle);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

.chat-body.compact .recommendation-item {
  padding: 10px;
}

.chat-body.compact .recommendation-item-title {
  font-size: 13px;
}

.chat-body.compact .recommendation-item-meta {
  gap: 8px;
  font-size: 11px;
}

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

@keyframes thinkingPulse {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-2px);
  }
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
</style>
