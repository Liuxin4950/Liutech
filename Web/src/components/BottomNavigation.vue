<template>
  <div class="bottom-nav fixed bottom-0 right-0 p-12">
    <div class="flex flex-col gap-16 mb-16">

      <!-- 回到顶部 -->
      <button
        class="fab"
        @click="scrollToTop"
        aria-label="回到顶部"
        title="回到顶部"
      >⬆</button>

      <!-- 跳到底部 -->
      <button
        class="fab"
        @click="scrollToBottom"
        aria-label="回到底部"
        title="回到底部"
      >⬇</button>

      <!-- 夜间模式 -->
      <button
        class="fab"
        @click="theme.toggle"
        aria-label="切换主题"
        title="切换主题"
      > {{ theme.current.value === 'light' ? '🌙' : '☀️' }}</button>

      <!-- 发布文章（主操作） -->
      <button
        class="fab fab--primary"
        @click="goCreate"
        aria-label="发布文章"
        title="发布文章"
      >✍️</button>

      <!-- 我的文章 -->
      <button
        class="fab"
        @click="goMyPosts"
        aria-label="我的文章"
        title="我的文章"
      >📄</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import theme from '../utils/theme.ts'

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

  color: var(--text-color);
  background: var(--bg-color);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-md);
  transition: all 0.2s ease-in-out;

  &:hover {
    background: var(--bg-hover);
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
  }
}

.fab--primary {
  background: var(--primary-color);
  color: #fff;
  border: none;

  &:hover {
    background: var(--primary-hover-color);
  }
}
</style>
