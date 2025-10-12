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

/**
 * 上下文与消息工具函数
 * 说明：这些工具仅做封装以提升代码可读性，不改变原有行为。
 */
type ActionMeta = Record<string, any>

// 从当前路由构建聊天上下文
const buildChatContext = (): Record<string, any> => {
  const ctx: Record<string, any> = { page: route.name || '' }
  if (route.name === 'post-detail' && route.params.id) {
    const n = Number(route.params.id)
    if (Number.isFinite(n)) ctx.postId = n
  }
  return ctx
}

// 推送一条用户消息并返回其ID
const pushUserMessage = (text: string): number => {
  const id = ++messageIdCounter
  messages.value.push({
    id,
    type: 'user',
    content: text,
    timestamp: new Date(),
    status: 'sending'
  })
  return id
}

// 更新用户消息的投递状态
const updateUserMessageStatus = (id: number, status: ChatMessage['status']) => {
  const idx = messages.value.findIndex(m => m.id === id)
  if (idx > -1) messages.value[idx].status = status
}

// 推送一条AI消息（支持错误标记）
const pushAiMessage = (text: string, opts?: { isError?: boolean }) => {
  messages.value.push({
    id: ++messageIdCounter,
    type: 'ai',
    content: text,
    timestamp: new Date(),
    isError: !!opts?.isError
  })
}

// 安全滚动到底部
const safeScrollToBottom = async () => { await scrollToBottom() }

// 意图检测：点赞/收藏（保持原有正则）
const hasLikeIntent = (text: string) => /((给)?(这篇)?(文|文章)?点个?赞|点赞|喜欢|like)/i.test(text)
const hasFavoriteIntent = (text: string) => /(收藏|加(个)?星|favorite|mark)/i.test(text)

// 提取当前活跃文章ID（优先使用AI返回的meta，其次路由）
const getActivePostId = (meta: ActionMeta): number | undefined => {
  const raw = meta.postId ?? meta.articleId ?? meta.id ?? (route.name === 'post-detail' ? route.params.id : undefined)
  const n = Number(raw)
  return Number.isFinite(n) ? n : undefined
}

// 发送普通聊天消息
const sendChat = async () => {
  if (!chatInput.value.trim() || isLoading.value || isStreaming.value) return

  const text = chatInput.value.trim()
  lastUserMessage = text // 用于客户端意图保护

  const msgId = pushUserMessage(text)
  chatInput.value = ''

  isLoading.value = true
  await safeScrollToBottom()

  try {
    const req: AiChatRequest = { message: text, context: buildChatContext() }
    const resp: AiChatResponse = await Ai.chat(req)

    updateUserMessageStatus(msgId, 'delivered')

    if (resp?.message) {
      pushAiMessage(resp.message)
    }

    const action = resp?.action || 'none'
    const meta = resp?.metadata || {}
    await dispatchAction(action, meta)
  } catch (err) {
    updateUserMessageStatus(msgId, 'failed')
    handleChatError(err, text)
  } finally {
    isLoading.value = false
    await safeScrollToBottom()
  }
}


// 根据AI返回的动作执行页面跳转或业务操作
/**
 * 根据AI返回的动作执行页面跳转或业务操作
 * 说明：保留原有动作集合与行为，仅整理结构与注释。
 */
const dispatchAction = async (action: string, meta: ActionMeta = {}) => {
  try {
    const postId = getActivePostId(meta)
    
    console.log('执行动作:', action, '参数:', meta)
    
    // 解析结构化动作
    const [actionType, actionValue] = action.split(':')
    
    switch (actionType) {
      case 'navigate':
        await handleNavigateAction(actionValue)
        break
      case 'interact':
        await handleInteractAction(actionValue, postId, hasLikeIntent, hasFavoriteIntent)
        break
      case 'search':
        await handleSearchAction(actionValue, meta)
        break
      case 'show':
        await handleShowAction(actionValue)
        break
      case 'none':
        break
      default:
        // 兼容旧格式的动作
        await handleLegacyAction(action, postId, hasLikeIntent, hasFavoriteIntent)
    }
  } catch (err: any) {
    console.warn('动作执行异常:', err)
    pushAiMessage(`❌ 动作执行失败：${err?.message || '未知错误'}`, { isError: true })
  } finally {
    await safeScrollToBottom()
  }
}

// 处理导航类动作
const handleNavigateAction = async (target: string) => {
  const navigationMap:any = {
    'home': { route: 'home', message: '首页' },
    'create-post': { route: 'create-post', message: '发布文章页面' },
    'my-posts': { route: 'my-posts', message: '我的文章页面' },
    'drafts': { route: 'drafts', message: '草稿箱页面' },
    'favorites': { route: 'favorites', message: '我的收藏页面' },
    'posts': { route: 'posts', message: '全部文章页面' },
    'categories': { route: 'category-list', message: '分类页面' },
    'tags': { route: 'tags', message: '标签页面' },
    'archive': { route: 'archive', message: '文章归档页面' },
    'profile': { route: 'profile', message: '个人资料页面' },
    'about': { route: 'about', message: '关于我页面' },
    'chat-history': { route: 'chat-history', message: '聊天历史记录页面' }
  }
  
  const navInfo = navigationMap[target]
  if (navInfo) {
    console.log(`触发导航动作，跳转${navInfo.message}`)
    await router.push({ name: navInfo.route })
    messages.value.push({
      id: ++messageIdCounter,
      type: 'ai',
      content: `✅ 已为您跳转到${navInfo.message}`,
      timestamp: new Date()
    })
  } else {
    throw new Error(`未知的导航目标：${target}`)
  }
}

// 处理交互类动作
const handleInteractAction = async (actionType: string, postId: number | undefined, hasLikeIntent: Function, hasFavoriteIntent: Function) => {
  switch (actionType) {
    case 'like':
      // 客户端保护：只有当用户输入里出现明显的点赞意图时才执行
      if (!hasLikeIntent(lastUserMessage)) {
        messages.value.push({
          id: ++messageIdCounter,
          type: 'ai',
          content: `已为您解析到可能的操作：点赞。但未检测到明确的"点赞"指令，因此未执行。如需点赞请明确说明。`,
          timestamp: new Date()
        })
        break
      }
      if (postId) {
        console.log("触发交互动作，点赞文章", postId)
        await likePost(postId)
        // 同步全局交互状态
        usePostInteractionStore().toggleLike(postId)
        messages.value.push({
          id: ++messageIdCounter,
          type: 'ai',
          content: `✅ 已为您点赞文章`,
          timestamp: new Date()
        })
      } else {
        throw new Error('点赞失败：未找到文章ID')
      }
      break
      
    case 'favorite':
      // 客户端保护：只有当用户输入里出现明显的收藏意图时才执行
      if (!hasFavoriteIntent(lastUserMessage)) {
        messages.value.push({
          id: ++messageIdCounter,
          type: 'ai',
          content: `已为您解析到可能的操作：收藏。但未检测到明确的"收藏"指令，因此未执行。如需收藏请明确说明。`,
          timestamp: new Date()
        })
        break
      }
      if (postId) {
        console.log("触发交互动作，收藏文章", postId)
        await favoritePost(postId)
        // 同步全局交互状态
        usePostInteractionStore().toggleFavorite(postId)
        messages.value.push({
          id: ++messageIdCounter,
          type: 'ai',
          content: `✅ 已为您收藏文章`,
          timestamp: new Date()
        })
      } else {
        throw new Error('收藏失败：未找到文章ID')
      }
      break
      
    case 'share':
      messages.value.push({
        id: ++messageIdCounter,
        type: 'ai',
        content: `📤 分享功能正在开发中，敬请期待！`,
        timestamp: new Date()
      })
      break
      
    case 'comment':
      messages.value.push({
        id: ++messageIdCounter,
        type: 'ai',
        content: `💬 评论功能正在开发中，敬请期待！`,
        timestamp: new Date()
      })
      break
      
    default:
      throw new Error(`未知的交互动作：${actionType}`)
  }
}

// 处理搜索类动作
const handleSearchAction = async (searchType: string, meta: Record<string, any>) => {
  const searchMap:any = {
    'posts': '文章',
    'tags': '标签',
    'categories': '分类',
    'users': '用户'
  }
  
  const searchName = searchMap[searchType]
  if (searchName) {
    console.log(`触发搜索动作，搜索${searchName}`)
    messages.value.push({
      id: ++messageIdCounter,
      type: 'ai',
      content: `🔍 ${searchName}搜索功能正在开发中，敬请期待！`,
      timestamp: new Date()
    })
  } else {
    throw new Error(`未知的搜索类型：${searchType}`)
  }
}

// 处理展示类动作
const handleShowAction = async (showType: string) => {
  switch (showType) {
    case 'capabilities':
      console.log("触发展示动作，展示AI功能介绍")
      messages.value.push({
        id: ++messageIdCounter,
        type: 'ai',
        content: `🌟 我是纳西妲，我可以为你提供以下服务：
📍 **页面导航**
• 跳转到首页、文章列表、分类、标签等页面
• 访问个人资料、关于我、聊天记录等个人页面
• 快速进入发布文章、草稿箱、收藏夹等功能页面

📝 **文章操作**
• 为你总结文章内容和要点
• 回答关于文章的问题
• 帮你点赞和收藏喜欢的文章

💬 **智能对话**
• 回答你的各种问题
• 提供专业建议和帮助
• 陪你聊天，让浏览更有趣

只需要告诉我你想做什么，比如"跳转到首页"、"总结这篇文章"、"点赞这篇文章"等，我就能帮你完成！有什么需要帮助的吗？ 🍄`,
        timestamp: new Date()
      })
      break
    default:
      throw new Error(`未知的展示类型：${showType}`)
  }
}

// 处理旧格式动作（兼容性）
const handleLegacyAction = async (action: string, postId: number | undefined, hasLikeIntent: Function, hasFavoriteIntent: Function) => {
  const legacyActionMap:any = {
    'go_home': () => handleNavigateAction('home'),
    'go_create_post': () => handleNavigateAction('create-post'),
    'go_my_posts': () => handleNavigateAction('my-posts'),
    'go_drafts': () => handleNavigateAction('drafts'),
    'go_favorites': () => handleNavigateAction('favorites'),
    'go_posts': () => handleNavigateAction('posts'),
    'go_categories': () => handleNavigateAction('categories'),
    'go_tags': () => handleNavigateAction('tags'),
    'go_archive': () => handleNavigateAction('archive'),
    'go_profile': () => handleNavigateAction('profile'),
    'go_about': () => handleNavigateAction('about'),
    'go_chat_history': () => handleNavigateAction('chat-history'),
    'like_post': () => handleInteractAction('like', postId, hasLikeIntent, hasFavoriteIntent),
    'favorite_post': () => handleInteractAction('favorite', postId, hasLikeIntent, hasFavoriteIntent),
    'show_capabilities': () => handleShowAction('capabilities')
  }
  
  const handler = legacyActionMap[action]
  if (handler) {
    await handler()
  } else {
    console.debug('未识别的动作：', action)
    messages.value.push({
      id: ++messageIdCounter,
      type: 'ai',
      content: `我收到一个暂不支持的动作：${action}`,
      timestamp: new Date()
    })
  }
}

// ... existing code ...

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
/**
 * 清空聊天记录并隐藏窗口
 * 说明：保留原有重置项，额外复位 lastUserMessage。
 */
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
      lastUserMessage = ''
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

// 点赞功能
const likePost = async (postId: number) => {
  try {
    console.log('点赞帖子:', postId)
    // 这里可以添加实际的点赞API调用
    // const response = await api.likePost(postId)
    // if (response.success) {
    //   console.log('点赞成功')
    // }
  } catch (error) {
    console.error('点赞失败:', error)
  }
}

// 收藏功能
const favoritePost = async (postId: number) => {
  try {
    console.log('收藏帖子:', postId)
    // 这里可以添加实际的收藏API调用
    // const response = await api.favoritePost(postId)
    // if (response.success) {
    //   console.log('收藏成功')
    // }
  } catch (error) {
    console.error('收藏失败:', error)
  }
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

