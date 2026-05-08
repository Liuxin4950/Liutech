<template>
  <div class="message-list">
    <div v-if="loading" class="loading-state">
      加载中...
    </div>
    <div v-else-if="messages.length === 0" class="empty-state">
      暂无留言，快来抢沙发吧~
    </div>
    <div v-else class="message-items">
      <div v-for="message in messages" :key="message.id" class="message-item">
        <div class="message-header">
          <span class="message-author">{{ message.nickname }}</span>
          <span class="message-time">{{ formatTime(message.createdAt) }}</span>
        </div>
        <div class="message-content">{{ message.content }}</div>
        <div v-if="message.reply" class="message-reply">
          <div class="reply-label">博主回复：</div>
          <div class="reply-content">{{ message.reply }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MessageService, type Message } from '@/services/message'

const loading = ref(true)
const messages = ref<Message[]>([])

const loadMessages = async () => {
  loading.value = true
  try {
    messages.value = await MessageService.getPublicMessages()
  } catch {
    // 加载留言失败时静默处理
  } finally {
    loading.value = false
  }
}

const formatTime = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    const hours = Math.floor(diff / (1000 * 60 * 60))
    if (hours === 0) {
      const minutes = Math.floor(diff / (1000 * 60))
      return minutes === 0 ? '刚刚' : `${minutes}分钟前`
    }
    return `${hours}小时前`
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
}

onMounted(() => {
  loadMessages()
})

defineExpose({
  refresh: loadMessages
})
</script>

<style scoped>
.message-list {
  width: 100%;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-subtle);
}

.message-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  background: var(--bg-soft);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid var(--border-light);
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.message-author {
  font-weight: 600;
  color: var(--color-primary);
  font-size: 14px;
}

.message-time {
  font-size: 12px;
  color: var(--text-subtle);
}

.message-content {
  color: var(--text-main);
  line-height: 1.6;
  font-size: 14px;
  white-space: pre-wrap;
}

.message-reply {
  margin-top: 12px;
  padding: 12px;
  background: var(--bg-card);
  border-radius: 6px;
  border-left: 3px solid var(--color-primary);
}

.reply-label {
  font-size: 12px;
  color: var(--color-primary);
  font-weight: 600;
  margin-bottom: 6px;
}

.reply-content {
  color: var(--text-main);
  line-height: 1.5;
  font-size: 13px;
}
</style>
