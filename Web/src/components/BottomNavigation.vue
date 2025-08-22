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
      <button
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
      <button
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const scrollToBottom = () => {
  window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })
}

const goCreate = () => {
  router.push('/create')
}

const goMyPosts = () => {
  router.push('/my-posts')
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
