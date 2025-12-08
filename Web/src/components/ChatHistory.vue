<template>
  <div class="chat-history">
    <h3>聊天历史记录</h3>
    <div class="controls">
      <button @click="loadHistory" :disabled="loading">加载历史记录</button>
      <span v-if="loading">加载中...</span>
    </div>
    
    <div v-if="error" class="error">
      错误: {{ error }}
    </div>
    
    <div v-if="historyData" class="history-info">
      <p>总记录数: {{ historyData.total }}</p>
      <p>当前页: {{ historyData.page }} / {{ Math.ceil(historyData.total / historyData.size) }}</p>
    </div>
    
    <div class="messages" v-if="messages.length > 0">
      <div v-for="message in messages" :key="message.id" class="message" :class="message.role">
        <div class="message-header">
          <span class="role">{{ message.role === 'user' ? '用户' : 'AI' }}</span>
          <span class="time">{{ formatTime(message.createdAt) }}</span>
        </div>
        <div class="message-content">
          <!-- User messages: plain text -->
          <div v-if="message.role === 'user'">
            {{ message.content }}
          </div>
          <!-- AI messages: markdown rendering -->
          <div v-else>
            <MarkdownRenderer :content="message.content" />
          </div>
        </div>
      </div>
    </div>
    
    <div v-else-if="!loading" class="no-messages">
      暂无聊天记录
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Ai } from '../services/ai.ts'
import MarkdownRenderer from './MarkdownRenderer.vue'

const loading = ref(false)
const error = ref('')
const messages = ref([])
const historyData = ref(null)


const loadHistory = async () => {
  loading.value = true
  error.value = ''
  
  try {
    const response = await Ai.chatHistory(1, 10)
    console.log('聊天历史记录响应:', response)
    
    if (response.success) {
      messages.value = response.data || []
      historyData.value = {
        total: response.total || 0,
        page: response.page || 1,
        size: response.size || 10
      }
    } else {
      error.value = response.message || '获取历史记录失败'
    }
  } catch (err) {
    console.error('获取聊天历史记录失败:', err)
    error.value = '网络错误: ' + err.message
  } finally {
    loading.value = false
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  return new Date(timeStr).toLocaleString('zh-CN')
}
</script>

<style scoped>
.chat-history {
  max-width: 1200px;
  margin: 20px auto;
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
}

.controls {
  margin-bottom: 20px;
}

.controls button {
  padding: 8px 16px;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.controls button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.error {
  color: red;
  margin-bottom: 20px;
  padding: 10px;
  background: #ffe6e6;
  border-radius: 4px;
}

.history-info {
  background: #f8f9fa;
  padding: 10px;
  border-radius: 4px;
  margin-bottom: 20px;
}

.messages {
  max-height: 400px;
  overflow-y: auto;
}

.message {
  margin-bottom: 15px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #eee;
}

.message.user {
  background: #e3f2fd;
}

.message.assistant {
  background: #f3e5f5;
}

.message-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  color: #666;
}

.role {
  font-weight: bold;
}

.message-content {
  white-space: pre-wrap;
  word-break: break-word;
}

.no-messages {
  text-align: center;
  color: #666;
  padding: 40px;
}
</style>