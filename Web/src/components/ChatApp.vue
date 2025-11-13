<script setup lang="ts">
import { ref } from 'vue'
import ConversationList from '@/components/ConversationList.vue'
import ChatDetail from '@/components/ChatDetail.vue'

const currentId = ref<number | null>(null)
</script>

<template>
  <div class="chat-app content">
    <div class="sidebar">
      <ConversationList v-model="currentId" />
    </div>
    <div class="panel">
      <ChatDetail :conversationId="currentId" />
    </div>
  </div>
</template>

<style scoped>
.chat-app { display:flex; width:100%; min-height:70vh; background: var(--bg-card); border:1px solid var(--border-soft); box-shadow: var(--shadow-lg); border-radius:16px }
.sidebar { width:320px; min-width:260px; background: var(--bg-soft); border-right:1px solid var(--border-soft); overflow:auto; border-radius:16px 0 0 16px }
.panel { flex:1; background: var(--bg-main); border-radius:0 16px 16px 0; display:flex; flex-direction:column }

@media (max-width: 1024px) {
  .sidebar { width:280px }
}
@media (max-width: 768px) {
  .chat-app { flex-direction:column; min-height:calc(100vh - 120px) }
  .sidebar { width:100%; border-right:none; border-bottom:1px solid var(--border-soft); border-radius:16px 16px 0 0 }
  .panel { border-radius:0 0 16px 16px }
}
</style>
