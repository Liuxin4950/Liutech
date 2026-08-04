<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore, type ChatMode } from '@/stores/chat'
import { AiStream } from '@/services/ai'
import AiChatBody from './AiChatBody.vue'
import AiChatHeader from './AiChatHeader.vue'
import AiChatInput from './AiChatInput.vue'
import Icon from './Icon.vue'
import { useConversationManager } from '@/composables/useConversationManager'
import { useVoiceRecognition } from '@/composables/useVoiceRecognition'
import { parsePostId } from '@/utils/postPath'
import { useNestedLenis } from '@/composables/useLenis'

const historyContentRef = ref<HTMLElement | null>(null)
useNestedLenis(historyContentRef)

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

// 显示状态统一来自 chatStore，避免 props/emit 中转
const expanded = computed(() => chatStore.isExpanded)
const modelVisible = computed(() => chatStore.showModel)

const chatInput = ref('')
const bodyRef = ref<InstanceType<typeof AiChatBody> | null>(null)

// 会话管理（侧边栏：列表、加载、删除、重命名）
const {
  conversations,
  isLoadingHistory,
  showHistorySidebar,
  isAuthenticated,
  editingConversationId,
  editingTitle,
  menuOpenId,
  syncAuthState,
  toggleHistorySidebar,
  deleteConversation,
  startEditTitle,
  saveTitle,
  cancelEditTitle,
  toggleConversationMenu,
  closeConversationMenu,
  formatConversationTime,
  loadConversation: _loadConversation,
} = useConversationManager(chatStore)

// 语音识别
const {
  voiceSupported,
  voiceListening,
  voiceInterimText,
  voiceError,
  initVoiceRecognition,
  startVoiceRecognition,
  stopVoiceRecognition,
  cleanupVoiceRecognition,
} = useVoiceRecognition(chatInput)

const messages = computed(() => chatStore.messages)
const isLoading = computed(() => chatStore.isLoading)
const isStreaming = computed(() => chatStore.isStreaming)
const mode = computed(() => chatStore.mode)
const hasMessages = computed(() => chatStore.hasMessages)
const errorMessage = computed(() => chatStore.errorMessage)
const isGuestMode = computed(() => !isAuthenticated.value)
const isCompact = computed(() => !expanded.value)
const guestBannerText = computed(() => isCompact.value
  ? '游客体验中，聊天记录不会保存'
  : '当前为游客体验模式，聊天记录不会保存。登录后可保存历史会话。'
)
const quickPrompts = computed(() => {
  if (route.name === 'post-detail') {
    return ['帮我总结这篇文章', '推荐几篇相关文章']
  }
  return ['推荐几篇文章', '介绍一下这个博客']
})

const ttsStatusText = ref<string>('语音检测中...')
const isTtsToggleDisabled = computed(() => !chatStore.ttsAvailable)
const ttsToggleTitle = computed(() => {
  if (chatStore.ttsAvailable) {
    return chatStore.ttsEnabled ? '语音已开启（点击关闭）' : '语音已关闭（点击开启）'
  }
  return ttsStatusText.value || '语音不可用'
})

const toggleTts = () => {
  if (isTtsToggleDisabled.value) return
  chatStore.setTtsEnabled(!chatStore.ttsEnabled)
}

const cleanedMessages = computed(() => {
  return messages.value.map((msg) => ({
    ...msg,
    displayContent: msg.content
  }))
})

const buildChatContext = (): Record<string, any> => {
  const ctx: Record<string, any> = { page: route.name || '' }
  if (route.name === 'post-detail' && route.params.id) {
    const n = parsePostId(route.params.id)
    if (Number.isFinite(n)) ctx.postId = n
  }
  return ctx
}

let mediaPrimed = false
const primeMediaOnce = () => {
  if (mediaPrimed) return
  mediaPrimed = true
  try {
    const Ctx = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext | undefined
    if (Ctx) {
      const ctx = new Ctx()
      ctx.resume().catch(() => {})
      const gain = ctx.createGain()
      gain.gain.value = 0
      const osc = ctx.createOscillator()
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start()
      osc.stop(ctx.currentTime + 0.01)
      window.setTimeout(() => { ctx.close().catch(() => {}) }, 50)
    }
  } catch {}

  try {
    const audio = new Audio('data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQAAAAA=')
    audio.volume = 0
    audio.play().catch(() => {})
  } catch {}
}

const scrollToBottom = async () => {
  await bodyRef.value?.scrollToBottom?.()
}

const scrollBodyBy = (deltaY: number) => {
  const scrollElement = bodyRef.value?.getScrollElement?.()
  if (!scrollElement) return
  scrollElement.scrollTop += deltaY
}

const setMode = (newMode: ChatMode) => {
  chatStore.setMode(newMode)
}

const clearHistory = async () => {
  await chatStore.clearHistory()
}

// 加载会话历史消息（模板中 @click 调用）
const loadConversation = (conversationId: number) =>
  _loadConversation(conversationId, scrollToBottom)

const sendMessage = async () => {
  if (!chatInput.value.trim() || isLoading.value) return
  syncAuthState()
  stopVoiceRecognition()

  const content = chatInput.value.trim()
  chatInput.value = ''
  voiceInterimText.value = ''

  primeMediaOnce()
  await chatStore.sendMessage(content, buildChatContext())
  await scrollToBottom()
}

const applyPrompt = (prompt: string) => {
  chatInput.value = prompt
}

const handleExpandChat = () => {
  chatStore.expandChat()
}

const handleCloseChat = () => {
  stopVoiceRecognition()
  chatStore.closeChat()
}

const handleToggleModelVisibility = () => {
  chatStore.toggleModelVisibility()
}

const handleOpenChatEvent = async (event: Event) => {
  const detail = (event as CustomEvent<{ prompt?: string; autoSend?: boolean }>).detail
  if (!detail?.prompt) return
  chatInput.value = detail.prompt
  if (detail.autoSend) {
    await sendMessage()
  }
}

const handlePostClick = (postId: number) => {
  router.push(`/post/${postId}`)
}

const handleMenuClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (!target.closest('.conversation-actions')) {
    closeConversationMenu()
  }
}


watch(
  () => messages.value.map(msg => `${msg.id}:${msg.content.length}:${msg.isStreaming ? 1 : 0}:${msg.isThinking ? 1 : 0}:${msg.articleResults?.length || 0}`).join('|'),
  async () => {
    await scrollToBottom()
  }
)

onMounted(async () => {
  syncAuthState()
  window.addEventListener('focus', syncAuthState)
  window.addEventListener('storage', syncAuthState)
  window.addEventListener('ai-chat-apply-prompt', handleOpenChatEvent as EventListener)
  document.addEventListener('click', handleMenuClickOutside)
  initVoiceRecognition()

  try {
    const runtime = await chatStore.loadRuntime()
    const status = runtime?.tts
    const available = status?.enabled === true && status?.online === true
    if (status) {
      ttsStatusText.value = status.message || (available ? '语音可用' : '语音不可用')
    } else {
      ttsStatusText.value = available ? '语音可用' : '语音不可用'
    }
  } catch {
    chatStore.setTtsAvailable(false)
    ttsStatusText.value = '语音检测失败'
  }

  await nextTick()
})

onUnmounted(() => {
  window.removeEventListener('focus', syncAuthState)
  window.removeEventListener('storage', syncAuthState)
  window.removeEventListener('ai-chat-apply-prompt', handleOpenChatEvent as EventListener)
  document.removeEventListener('click', handleMenuClickOutside)
  cleanupVoiceRecognition()
  AiStream.cancel()
})

defineExpose({
  scrollToBottom,
  scrollBodyBy
})
</script>

<template>
  <div class="chat-box" :class="{ expanded, compact: !expanded }">
    <div class="chat-popup">
      <div v-if="expanded && isAuthenticated" class="history-sidebar" :class="{ show: showHistorySidebar }">
        <div class="history-header">
          <h4>会话历史</h4>
          <button class="close-sidebar" @click="toggleHistorySidebar"><Icon name="close" /></button>
        </div>

        <div ref="historyContentRef" class="history-content" data-lenis-prevent>
          <div v-if="isLoadingHistory" class="history-loading text-sm">
            <div class="spinner-sm"></div>
            <span>加载中...</span>
          </div>

          <div v-else-if="conversations.length === 0" class="history-empty flex flex-col flex-ac text-sm">
            <p>暂无历史会话</p>
            <img src="@/assets/image/扑到.png" alt="" class="fit-err">
          </div>

          <div v-else class="conversation-list">
            <div
              v-for="conversation in conversations"
              :key="conversation.id"
              class="conversation-item"
              :class="{ active: chatStore.conversationId === conversation.id }"
              @click="loadConversation(conversation.id)"
            >
              <div class="conversation-info">
                <div class="conversation-title">
                  <span
                    v-if="editingConversationId !== conversation.id"
                    class="conversation-title-text"
                  >
                    {{ conversation.title || `会话 ${conversation.id}` }}
                  </span>
                  <input
                    v-else
                    v-model="editingTitle"
                    @click.stop
                    @blur="saveTitle(conversation.id)"
                    @keyup.enter="saveTitle(conversation.id)"
                    @keyup.esc="cancelEditTitle()"
                    class="title-edit-input"
                  />
                </div>
                <div class="conversation-meta">
                  <span class="message-count">{{ conversation.messageCount }} 条消息</span>
                  <span class="conversation-time">{{ formatConversationTime(conversation.lastMessageAt) }}</span>
                </div>
              </div>
              <div class="conversation-actions">
                <button class="more-btn" @click.stop="toggleConversationMenu(conversation.id)" title="更多操作">
                  <Icon name="more" :size="16" />
                </button>
                <div v-show="menuOpenId === conversation.id" class="action-dropdown">
                  <button class="action-option" @click.stop="startEditTitle(conversation.id, conversation.title || ''); closeConversationMenu()">
                    <Icon name="edit" :size="14" />
                    <span>重命名</span>
                  </button>
                  <button class="action-option danger" @click.stop="deleteConversation(conversation.id, $event); closeConversationMenu()">
                    <Icon name="trash" :size="14" />
                    <span>删除</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-main" :class="{ 'with-sidebar': expanded && showHistorySidebar }">
        <div class="chat-header-layer">
          <AiChatHeader
            :expanded="expanded"
            :mode="mode"
            :tts-enabled="chatStore.ttsEnabled"
            :tts-available="chatStore.ttsAvailable"
            :tts-toggle-title="ttsToggleTitle"
            :show-history-button="!!expanded"
            :show-model-toggle-button="!!expanded"
            :model-visible="!!modelVisible"
            @expand="handleExpandChat"
            @close="handleCloseChat"
            @clear="clearHistory"
            @toggle-history="toggleHistorySidebar"
            @toggle-model="handleToggleModelVisibility"
            @toggle-tts="toggleTts"
            @set-mode="setMode"
          />
        </div>

        <div class="chat-body-layer">
          <AiChatBody
            ref="bodyRef"
            :messages="cleanedMessages"
            :has-messages="hasMessages"
            :is-loading="isLoading"
            :is-streaming="isStreaming"
            :error-message="errorMessage"
            :is-guest-mode="isGuestMode"
            :guest-banner-text="guestBannerText"
            :expanded="expanded"
            @clear-error="chatStore.errorMessage = ''"
            @open-post="handlePostClick"
          />
        </div>

        <div class="chat-input-layer">
          <AiChatInput
            v-model="chatInput"
            :is-loading="isLoading"
            :expanded="expanded"
            :quick-prompts="quickPrompts"
            :voice-supported="voiceSupported"
            :voice-listening="voiceListening"
            :voice-interim-text="voiceInterimText"
            :voice-error="voiceError"
            @apply-prompt="applyPrompt"
            @send="sendMessage"
            @start-voice="startVoiceRecognition"
            @stop-voice="stopVoiceRecognition"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.chat-box {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: visible;
}

.chat-box.compact {
  overflow: hidden;
  border-radius: 16px;
  box-shadow: var(--shadow-xl);
}

.chat-box.expanded {
  overflow: hidden;
  border-radius: 24px;
  background: var(--bg-card);
}

@include respond(md) {
  .chat-box.expanded {
    border-radius: 0;
  }
}

.chat-popup {
  width: 100%;
  height: 100%;
  display: flex;
  position: relative;
}

.chat-box.expanded .chat-popup {
  background: var(--bg-card);
}

.chat-main {
  width: 100%;
  height: 100%;
  position: relative;
  transition: margin-right 0.3s ease;
}

.chat-box.expanded .chat-main {
  background: var(--bg-card);
}

.chat-main.with-sidebar {
  margin-right: 300px;
}

.chat-header-layer,
.chat-body-layer,
.chat-input-layer {
  position: absolute;
  left: 0;
  right: 0;
}

.chat-header-layer {
  top: 0;
  z-index: 40;
}

.chat-body-layer {
  top: 0;
  bottom: 0;
  z-index: 20;
  min-height: 0;
}

.chat-input-layer {
  bottom: 0;
  z-index: 40;
  max-height: 50vh;
}

.chat-box.compact .chat-main {
  display: flex;
  flex-direction: column;
}

.chat-box.compact .chat-header-layer,
.chat-box.compact .chat-body-layer,
.chat-box.compact .chat-input-layer {
  position: relative;
  inset: auto;
}

.chat-box.compact .chat-body-layer {
  flex: 1;
  min-height: 0;
}

.chat-box.expanded .chat-header-layer {
  top: 0;
  left: 0;
  right: 0;
}

.chat-box.expanded .chat-body-layer {
  top: 0;
  bottom: 0;
}

.chat-box.expanded .chat-input-layer {
  left: 0;
  right: 0;
  bottom: 0;
}

.chat-box.expanded .chat-body-layer :deep(.chat-body) {
  padding-top: 80px;
  padding-bottom: 140px;
}

@include respond(md) {
  .chat-box.expanded .chat-body-layer :deep(.chat-body) {
    padding-bottom: 160px;
  }
}

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
  z-index: 60;
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

.spinner-sm {
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

.conversation-title-text {
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.title-edit-input {
  width: 100%;
  border: 1px solid var(--border-base);
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
  box-shadow: 0 0 0 2px var(--bg-active);
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

.conversation-actions {
  position: relative;
  flex-shrink: 0;
  margin-left: 8px;
}

.more-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--text-subtle);
  cursor: pointer;
  transition: all 0.2s ease;
}

.more-btn:hover {
  background: var(--bg-hover);
  color: var(--text-main);
}

.action-dropdown {
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
  min-width: 120px;
  padding: 4px;
}

.action-option {
  width: 100%;
  padding: 8px 10px;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.8125rem;
  color: var(--text-main);
  border-radius: 6px;
}

.action-option:hover {
  background: var(--bg-hover);
}

.action-option.danger {
  color: var(--color-error);
}

.action-option.danger:hover {
  background: var(--bg-error);
}

</style>
