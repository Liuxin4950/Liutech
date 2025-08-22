<template>
  <div class="card bg-card ">
    <h4 class="card-title">推荐阅读</h4>
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="posts.length === 0" class="empty-text">暂无推荐</div>
    <div v-else class="list gap-12">
      <div 
        v-for="post in posts" 
        :key="post.id" 
        class="flex flex-col gap-8 p-12 rounded link transition bg-soft"
        @click="handlePostClick(post.id)"
      >
        <h5 class="text-base font-semibold text-primary mb-0">{{ post.title }}</h5>
        <div class="flex flex-sb flex-ac text-sm text-muted">
          <span class="font-medium">{{ post.author?.username }}</span>
          <span>{{ formatDate(post.createdAt) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/uitls'

// 定义props
interface Author {
  username: string
}

interface Post {
  id: number
  title: string
  author?: Author
  createdAt: string
}

interface Props {
  posts: Post[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

// 定义事件
const emit = defineEmits<{
  postClick: [postId: number]
}>()

// 处理文章点击
const handlePostClick = (postId: number) => {
  emit('postClick', postId)
}


</script>

<style scoped>
</style>