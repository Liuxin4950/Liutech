<template>
  <div class="card bg-card ">
    <h4 class="card-title"><span class="card-badge"><Icon name="book" size="12" /> Reading</span><span class="card-title-text">推荐<span class="card-highlight">阅读</span></span></h4>
    <div v-if="loading" class="loading-text text-sm">加载中...</div>
    <div v-else-if="posts.length === 0" class="empty-text flex flex-col flex-ac text-sm">
      <p>暂无推荐</p>
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
    </div>
    <div v-else class="list">
      <div
        v-for="post in posts"
        :key="post.id"
        class="list-item flex flex-col gap-8 link transition"
        @click="handlePostClick(post.id)"
      >
        <h5 class="text-lg font-semibold mb-0 recommended-title" style="color: var(--text-title)">{{ post.title }}</h5>
        <div class="flex flex-sb flex-ac text-sm text-muted">
          <span class="font-medium">{{ post.author?.username }}</span>
          <span>{{ formatDate(post.createdAt) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/utils'
import Icon from './Icon.vue'

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

<style scoped lang="scss">
.recommended-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>