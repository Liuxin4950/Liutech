<template>
  <div>
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-text">加载中...</div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="loading-text text-primary">
      <p>{{ error }}</p>
      <button @click="$emit('retry')" class="retry-btn">重试</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="posts.length === 0" class="empty-text">
      <slot name="empty">
        <p>暂无文章</p>
      </slot>
    </div>

    <!-- 文章列表 -->
    <div v-else class="list gap-16">
      <article
        v-for="post in posts"
        :key="post.id"
        class="flex gap-16 p-16 rounded-lg transition link card bg-card"
        @click="$emit('post-click', post.id)"
      >
        <!-- 缩略图 -->
        <div class="posts-img">
          <img
            :src="post.coverImage || post.thumbnail || '/src/assets/image/images.jpg'"
            :alt="post.title"
            class="fit"
          />
        </div>

        <div class="flex flex-col flex-sb flex-1 relative">
          <span v-if="post.category" class="article-category">{{ post.category.name }}</span>
          <div class="flex-1 flex flex-col gap-12">
            <h3 class="font-semibold text-primary text-xl">{{ post.title }}</h3>
            <p v-if="post.summary" class="text-subtle text-base text-sm post-summary">
              {{ post.summary }}
            </p>
            <div class="tags-cloud" v-if="post.tags && post.tags.length > 0">
              <span v-for="tag in post.tags" :key="tag.id" class="tag">
                {{ tag.name }}
              </span>
            </div>
          </div>

          <div class="flex flex-sb flex-ac mt-8">
            <div class="flex flex-ac gap-8 text-subtle">
              <img
                v-if="post.author?.avatarUrl"
                :src="post.author.avatarUrl"
                :alt="post.author.username"
                class="rounded"
                style="width: 24px; height: 24px; object-fit: cover"
              />
              <span class="text-sm">{{ post.author?.username || '匿名用户' }}</span>
            </div>
            <div class="flex gap-12 text-sm text-subtle">
              <span>👁️ {{ post.viewCount || 0 }}</span>
              <span>❤️ {{ post.likeCount || 0 }}</span>
              <span>💬 {{ post.commentCount }}</span>
              <span>{{ formatDate(post.createdAt) }}</span>
            </div>
          </div>
        </div>
      </article>
    </div>

    <!-- 分页器 -->
    <Pagination
      v-if="!loading && posts.length > 0"
      :current-page="pagination.current"
      :total-pages="pagination.pages"
      @page-change="$emit('page-change', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { formatDate } from '@/utils/uitls'
import Pagination from '@/components/Pagination.vue'

const props = defineProps<{
  posts: any[]
  loading: boolean
  error: string
  pagination: {
    current: number
    size: number
    total: number
    pages: number
  }
}>()

defineEmits<{
  'post-click': [postId: number]
  'page-change': [page: number]
  'retry': []
}>()
</script>

<style scoped>
.posts-img {
  width: 200px;
  height: 150px;
  border-radius: 8px;
  overflow: hidden;
}

.posts-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.posts-img:hover img {
  transform: scale(1.05);
}


.post-summary {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  max-height: 3em;
  word-break: break-word;
}

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.retry-btn:hover {
  background: var(--secondary-color);
}
</style>
