<template>
  <div class="card bg-card ">
    <div class="flex flex-col flex-ac ">
      <img :src="avatar" :alt="name" class="avatar">
      <div class="flex flex-col flex-ac">
        <h3 class="text-lg font-semibold mb-4">{{ name }}</h3>
        <p class="text-muted text-lg mb-0">{{ title }}</p>
      </div>
    </div>
    <p class="text-light text-lg mb-0" style="line-height: 1.5;">{{ bio }}</p>
    <div class="flex flex-sb mb-16 p-12 border-t border-b">
      <div class="flex-1 text-center">
        <span class="stat-number">{{ stats.posts }}</span>
        <span class="stat-label">文章</span>
      </div>
      <div class="flex-1 text-center">
        <span class="stat-number">{{ stats.comments }}</span>
        <span class="stat-label">评论</span>
      </div>
      <div class="flex-1 text-center">
        <span class="stat-number">{{ stats.views }}</span>
        <span class="stat-label">访问</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">

// 定义props
interface Stats {
  posts: number
  comments: number
  views: number
}

interface Props {
  name: string
  title: string
  avatar: string
  bio: string
  stats: Stats
}

withDefaults(defineProps<Props>(), {
  avatar: '/default-avatar.svg',
  bio: '专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。'
})
</script>

<style scoped>

</style>
