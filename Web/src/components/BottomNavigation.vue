<!-- 推荐使用 SVG 图标（示例） -->
<template>
  <div class="bottom-nav fixed bottom-0 right-0 p-12">
    <div class="flex flex-col gap-16 mb-16">

      <!-- 回到顶部 -->
      <button
        class="fab"
        @click="scrollToTop"
        aria-label="回到顶部"
        title="回到顶部"
      >
        <!-- 使用 SVG 替代 Unicode 箭头 -->
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M7.41 15.41L12 10.83l4.59 4.58L18 12l-6-6-6 6z"/>
        </svg>
      </button>

      <!-- 跳到底部 -->
      <button
        class="fab"
        @click="scrollToBottom"
        aria-label="回到底部"
        title="回到底部"
      >
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M7.41 8.59L12 13.17l4.59-4.58L18 12l-6 6-6-6z"/>
        </svg>
      </button>

      <!-- 发布文章（主操作） -->
      <button v-if="userStore.isAdmin"
        class="fab fab--primary"
        @click="goCreate"
        aria-label="发布文章"
        title="发布文章"
      >
        <!-- 更现代的笔触图标 -->
        <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
          <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
        </svg>
      </button>

      <!-- 我的文章 -->
      <button v-if="userStore.isAdmin"
        class="fab"
        @click="goMyPosts"
        aria-label="我的文章"
        title="我的文章"
      >
        <!-- 更直观的文档图标 -->
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-7V3.5L18.5 9H13z"/>
        </svg>
      </button>

      <!-- AI助手/模型控制 -->
      <button
        class="fab"
        @click="goAiChat"
        aria-label="AI助手"
        title="AI助手"
      >
        <!-- AI机器人图标 -->
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M12 2C13.1 2 14 2.9 14 4C14 5.1 13.1 6 12 6C10.9 6 10 5.1 10 4C10 2.9 10.9 2 12 2ZM21 9V7L15 1H5C3.89 1 3 1.89 3 3V7H1V9H3V15C3 16.1 3.9 17 5 17H8.5C8.5 18.4 9.6 19.5 11 19.5S13.5 18.4 13.5 17H19C20.1 17 21 16.1 21 15V9H21ZM7.5 11.5C7.5 10.7 8.2 10 9 10S10.5 10.7 10.5 11.5S9.8 13 9 13S7.5 12.3 7.5 11.5ZM16.5 11.5C16.5 12.3 15.8 13 15 13S13.5 12.3 13.5 11.5S14.2 10 15 10S16.5 10.7 16.5 11.5ZM11 16H13C13 16.6 12.6 17 12 17S11 16.6 11 16Z"/>
        </svg>
      </button>

    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const emit = defineEmits(['ai-chat-active', 'auth-required'])

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const scrollToBottom = () => {
  window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })
}

const goCreate = () => {
  // 需要登录验证的操作
  emit('auth-required', () => router.push('/create'), '发布文章需要登录，请先登录您的账户。')
}

const goMyPosts = () => {
  // 需要登录验证的操作
  emit('auth-required', () => router.push('/my-posts'), '查看我的文章需要登录，请先登录您的账户。')
}

const goAiChat = () => {
  // 更改父组件的状态
  emit('ai-chat-active')
}

</script>

<style scoped lang="scss">
.bottom-nav {
  z-index: 1000;

}

.fab {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;

  color: var(--text-main);
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-sm);
  transition: all 0.2s ease-in-out;

  &:hover {
    background: var(--bg-hover);
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
  }
}
</style>
