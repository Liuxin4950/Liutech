<script setup lang="ts">
import { onMounted, ref, watch, nextTick } from 'vue'
import ConversationService, { type ChatMessageItem } from '@/services/conversation'
import { Ai, type AiChatRequest, type AiChatResponse } from '@/services/ai.ts'

const props = defineProps<{ conversationId: number | null }>()

const messages = ref<ChatMessageItem[]>([])
const input = ref('')
const loading = ref(false)

const load = async () => {
  if (!props.conversationId) { messages.value = []; return }
  const data = await ConversationService.messages(props.conversationId, 1, 50)
  // 转为升序，最新在底部，符合聊天阅读习惯
  messages.value = [...data].reverse()
  await nextTick()
  const container = document.querySelector('.history') as HTMLElement
  if (container) container.scrollTop = container.scrollHeight
}

const send = async () => {
  if (!input.value.trim() || !props.conversationId) return
  loading.value = true
  try {
    const req: AiChatRequest = { message: input.value.trim(), context: {}, conversationId: props.conversationId }
    const resp: AiChatResponse = await Ai.chat(req)
    input.value = ''
    await load()
  } finally { loading.value = false }
}

const handleKeyDown = async (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    await send()
  }
}

watch(() => props.conversationId, load)
onMounted(load)
</script>

<template>
  <div class="chat-detail">
    <div class="history">
      <div v-for="m in messages" :key="m.id" :class="['msg', m.role]">
        <div class="bubble">{{ m.content }}</div>
        <div class="stamp">{{ new Date(m.createdAt).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}</div>
      </div>
      <div v-if="messages.length===0" class="empty">请选择左侧会话或新建会话</div>
    </div>
    <div class="composer">
      <textarea v-model="input" placeholder="输入消息...（Enter发送，Shift+Enter换行）" :disabled="loading" @keydown="handleKeyDown"></textarea>
      <button class="send" @click="send" :disabled="loading || !props.conversationId">发送</button>
    </div>
  </div>
</template>

<style scoped>
.chat-detail{ flex:1; display:flex; flex-direction:column; background: var(--bg-main) }
.history{ flex:1; overflow:auto; padding:16px; display:flex; flex-direction:column; gap:14px; background: var(--bg-main) }
.msg{ display:flex; align-items:flex-end; gap:8px }
.msg.user{ justify-content:flex-end }
.msg.assistant{ justify-content:flex-start }
.bubble{ max-width:68%; padding:12px 16px; border-radius:16px; border:1px solid var(--border-soft); background: var(--bg-card); color: var(--text-main); box-shadow: var(--shadow-sm); line-height:1.6; font-size:14px }
.msg.user .bubble{ background: var(--color-primary); color: #fff }
.stamp{ font-size:12px; color: var(--text-subtle) }
.msg.user .stamp{ text-align:right }
.msg.assistant .stamp{ text-align:left }
.empty{ color: var(--text-subtle) }
.composer{ display:flex; gap:10px; padding:12px; border-top:1px solid var(--border-soft); background: var(--bg-card) }
.composer textarea{ flex:1; min-height:42px; background: var(--bg-main); color: var(--text-main); border:1px solid var(--border-soft); border-radius:8px; padding:10px; transition:.2s }
.composer textarea:focus{ border-color: var(--color-primary) }
.send{ min-width:90px; height:42px; background: var(--color-primary); color:#fff; border:none; border-radius:8px; cursor:pointer; transition:.2s }
.send:hover{ transform: translateY(-1px) }
.send:disabled{ background: var(--bg-hover); color: var(--text-subtle) }
@media (max-width: 768px){ .bubble{ max-width:86% } }
</style>
