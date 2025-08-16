<template>
  <div class="bottom-nav fixed bottom-0 right-0 p-20">
    <div class="flex flex-col gap-12">
      <!-- 回到顶部 -->
      <button
        class="fab bg-white rounded-full flex flex-ct transition hover-lift border"
        @click="scrollToTop"
        aria-label="回到顶部"
        title="回到顶部"
      >⬆</button>

      <!-- 发布文章（主操作） -->
      <button
        class="fab fab--primary rounded-full flex flex-ct transition hover-lift"
        @click="goCreate"
        aria-label="发布文章"
        title="发布文章"
      >✍️</button>

      <!-- 我的文章 -->
      <button
        class="fab bg-white rounded-full flex flex-ct transition hover-lift border"
        @click="goMyPosts"
        aria-label="我的文章"
        title="我的文章"
      >📄</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
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
  color: var(--text-color);
  background: var(--bg-color);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
}

.fab--primary {
  background: var(--primary-color);
  color: #fff;
}
</style>

