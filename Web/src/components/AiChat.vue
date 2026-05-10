<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore, type ChatMode } from '@/stores/chat'
import { AiStream } from '@/services/ai'
import { ConversationService, type Conversation } from '@/services/conversation'
import AiChatBody from './AiChatBody.vue'
import AiChatHeader from './AiChatHeader.vue'
import AiChatInput from './AiChatInput.vue'
import Icon from './Icon.vue'
import { showConfirm, showWarning } from '@/utils/errorHandler'
import { isLoggedIn } from '@/utils/auth'

interface SpeechRecognitionLike {
  continuous: boolean
  interimResults: boolean
  lang: string
  onstart: ((event: Event) => void) | null
  onend: ((event: Event) => void) | null
  onerror: ((event: any) => void) | null
  onresult: ((event: any) => void) | null
  start(): void
  stop(): void
  abort(): void
}

interface SpeechRecognitionConstructor {
  new (): SpeechRecognitionLike
}

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
  }
}

const props = defineProps<{
  expanded?: boolean
  modelVisible?: boolean
}>()

const emit = defineEmits<{
  expand: []
  close: []
  toggleModelVisibility: []
}>()

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

const chatInput = ref('')
const bodyRef = ref<InstanceType<typeof AiChatBody> | null>(null)
const conversations = ref<Conversation[]>([])
const isLoadingHistory = ref(false)
const showHistorySidebar = ref(false)
const isAuthenticated = ref(isLoggedIn())
const editingConversationId = ref<number | null>(null)
const editingTitle = ref('')

const voiceSupported = ref(false)
const voiceListening = ref(false)
const voiceInterimText = ref('')
const voiceError = ref('')
const recognition = ref<SpeechRecognitionLike | null>(null)

const messages = computed(() => chatStore.messages)
const isLoading = computed(() => chatStore.isLoading)
const isStreaming = computed(() => chatStore.isStreaming)
const mode = computed(() => chatStore.mode)
const hasMessages = computed(() => chatStore.hasMessages)
const errorMessage = computed(() => chatStore.errorMessage)
const isGuestMode = computed(() => !isAuthenticated.value)
const isCompact = computed(() => !props.expanded)
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

const syncAuthState = () => {
  isAuthenticated.value = isLoggedIn()
}

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
    const n = Number(route.params.id)
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
      ctx.resume().catch(() => {
      })
      const gain = ctx.createGain()
      gain.gain.value = 0
      const osc = ctx.createOscillator()
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start()
      osc.stop(ctx.currentTime + 0.01)
      window.setTimeout(() => {
        ctx.close().catch(() => {
        })
      }, 50)
    }
  } catch {
  }

  try {
    const audio = new Audio('data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQAAAAA=')
    audio.volume = 0
    audio.play().catch(() => {
    })
  } catch {
  }
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

const loadConversation = async (conversationId: number) => {
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
  if (days === 1) {
    return '昨天'
  }
  if (days < 7) {
    return `${days}天前`
  }
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const mapSpeechError = (code?: string) => {
  switch (code) {
    case 'not-allowed':
    case 'service-not-allowed':
      return '语音识别权限被拒绝，请允许浏览器使用麦克风。'
    case 'audio-capture':
      return '未检测到可用麦克风，请检查设备后重试。'
    case 'network':
      return '语音识别网络异常，请稍后重试。'
    case 'no-speech':
      return '没有识别到语音，请再说一次。'
    default:
      return '语音识别暂时不可用，请改用文字输入。'
  }
}

const initVoiceRecognition = () => {
  if (typeof window === 'undefined') return
  const RecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!RecognitionCtor) {
    voiceSupported.value = false
    return
  }

  voiceSupported.value = true
  const speechRecognition = new RecognitionCtor()
  speechRecognition.continuous = true
  speechRecognition.interimResults = true
  speechRecognition.lang = 'zh-CN'

  speechRecognition.onstart = () => {
    voiceListening.value = true
    voiceError.value = ''
  }

  speechRecognition.onresult = (event: any) => {
    let finalText = ''
    let interimText = ''
    for (let i = event.resultIndex; i < event.results.length; i += 1) {
      const transcript = event.results[i][0]?.transcript || ''
      if (event.results[i].isFinal) {
        finalText += transcript
      } else {
        interimText += transcript
      }
    }

    if (finalText.trim()) {
      chatInput.value = [chatInput.value.trim(), finalText.trim()].filter(Boolean).join(chatInput.value.trim() ? '\n' : '')
    }
    voiceInterimText.value = interimText.trim()
  }

  speechRecognition.onerror = (event: any) => {
    voiceError.value = mapSpeechError(event?.error)
    voiceListening.value = false
    voiceInterimText.value = ''
  }

  speechRecognition.onend = () => {
    voiceListening.value = false
    voiceInterimText.value = ''
  }

  recognition.value = speechRecognition
}

const startVoiceRecognition = () => {
  if (!voiceSupported.value || voiceListening.value || !recognition.value) return
  voiceError.value = ''
  voiceInterimText.value = ''
  try {
    recognition.value.start()
  } catch (error) {
    voiceError.value = '语音识别启动失败，请稍后重试。'
  }
}

const stopVoiceRecognition = () => {
  if (!voiceListening.value || !recognition.value) return
  try {
    recognition.value.stop()
  } catch {
  }
}

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
  emit('expand')
}

const handleCloseChat = () => {
  stopVoiceRecognition()
  emit('close')
}

const handleToggleModelVisibility = () => {
  emit('toggleModelVisibility')
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


watch(
  () => messages.value.map(msg => `${msg.id}:${msg.content.length}:${msg.isStreaming ? 1 : 0}:${msg.isThinking ? 1 : 0}:${msg.agentPlanSteps?.length || 0}:${msg.agentToolEvents?.length || 0}:${msg.articleResults?.length || 0}`).join('|'),
  async () => {
    await scrollToBottom()
  }
)

onMounted(async () => {
  syncAuthState()
  window.addEventListener('focus', syncAuthState)
  window.addEventListener('storage', syncAuthState)
  window.addEventListener('ai-chat-apply-prompt', handleOpenChatEvent as EventListener)
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
  stopVoiceRecognition()
  recognition.value?.abort()
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

        <div class="history-content">
          <div v-if="isLoadingHistory" class="history-loading text-sm">
            <div class="loading-spinner"></div>
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
                    @click.stop="startEditTitle(conversation.id, conversation.title || '')"
                    class="editable-title"
                  >
                    {{ conversation.title || `会话 ${conversation.id}` }}
                  </span>
                  <input
                    v-else
                    v-model="editingTitle"
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
              <button class="delete-conversation" @click="deleteConversation(conversation.id, $event)" title="删除会话">
                <Icon name="trash" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-main" :class="{ 'with-sidebar': expanded && showHistorySidebar }">
        <div class="chat-header-layer">
          <AiChatHeader
            :expanded="expanded"
            :mode="mode"
            :is-guest-mode="isGuestMode"
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
  background-color: var(--bg-hover);
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
  background: var(--bg-error);
  color: var(--color-error);
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}
</style>
