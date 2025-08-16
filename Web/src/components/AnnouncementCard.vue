<template>
  <div class="card bg-card ">
    <h4 class="card-title">公告</h4>
    <div class="list gap-12">
      <div 
        v-for="announcement in announcements" 
        :key="announcement.id" 
        class="list-item"
      >
        <span class="text-sm text-primary font-medium">{{ announcement.date }}</span>
        <p class="text-base text-light mb-0 mt-4">{{ announcement.text }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">


// 定义props
interface Announcement {
  id: number
  date: string
  text: string
}

interface Props {
  announcements: Announcement[]
}

const props = withDefaults(defineProps<Props>(), {
  announcements: () => [
    {
      id: 1,
      date: '2025-01-27',
      text: '博客系统正式上线，欢迎大家访问！'
    },
    {
      id: 2,
      date: '2025-01-26',
      text: '新增夜间模式功能，提升阅读体验。'
    },
    {
      id: 3,
      date: '2025-01-25',
      text: '评论系统优化完成，支持实时回复。'
    }
  ]
})
</script>

<style scoped>
</style>