<template>
  <div class="posts-section">
    <div class="section-header">
      <h2><Icon name="fire" size="20" /> 热门文章</h2>
      <div class="header-actions">
        <button class="view-all-btn" @click="$emit('create-post')">发布文章</button>
        <button class="view-all-btn" @click="$emit('view-all')">查看全部</button>
      </div>
    </div>

    <div v-if="loading" class="loading text-sm">
      <p>加载中...</p>
    </div>
    <div v-else-if="error" class="error text-sm">
      <p>{{ error }}</p>
      <button @click="$emit('retry')" class="retry-btn">重试</button>
    </div>
    <div v-else-if="posts.length === 0" class="empty flex flex-col flex-ac text-sm">
      <p>暂无热门文章</p>
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
    </div>
    <div v-else class="posts-list">
      <article
        v-for="post in posts"
        :key="post.id"
        class="post-item"
        @click="$emit('post-click', post.id)"
      >
        <div class="post-content">
          <!-- 缩略图 -->
          <div class="post-thumbnail">
            <img
              :src="post.thumbnail || post.coverImage || defaultPostImage"
              :alt="post.title"
              class="thumbnail-image"
              loading="lazy"
              @error="handleImageError"
            />
          </div>
          
          <div class="post-header">
            <h3 class="post-title">{{ post.title }}</h3>
            <span v-if="post.category" class="post-category">{{ post.category.name }}</span>
          </div>
          <p v-if="post.summary" class="post-summary">{{ post.summary }}</p>
          <div class="post-meta">
            <div class="author-info">
              <img
                v-if="post.author?.avatarUrl"
                :src="post.author.avatarUrl"
                :alt="post.author.username"
                class="author-avatar"
                @error="handleImageError"
              >
              <span class="author-name">{{ post.author?.username || '匿名用户' }}</span>
            </div>
            <div class="post-stats">
              <span class="flex flex-ac gap-4"><Icon name="eye" size="14" /> {{ post.viewCount || 0 }}</span>
              <span class="flex flex-ac gap-4"><Icon name="heart" size="14" /> {{ post.likeCount || 0 }}</span>
              <span class="flex flex-ac gap-4"><Icon name="message" size="14" /> {{ post.commentCount || 0 }}</span>
              <span class="post-date">{{ formatDate(post.createdAt) }}</span>
            </div>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PostListItem } from '@/services/post'
import { formatDate } from '@/utils/utils'
import { handleImageError } from '@/composables/useImageFallback'
import Icon from './Icon.vue'
import defaultPostImage from '@/assets/image/err.png'

interface Props {
  posts: PostListItem[]
  loading?: boolean
  error?: string
}

defineProps<Props>()

defineEmits<{
  'post-click': [postId: number]
  'create-post': []
  'view-all': []
  'retry': []
}>()


</script>

<style scoped>
@use "@/assets/styles/tokens" as *;
/* 文章区域 */
.posts-section {
  background: var(--bg-card);
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-base);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-base);
}

.section-header h2 {
  font-size: 1.5rem;
  color: var(--text-main);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.view-all-btn {
  padding: 6px 12px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: background-color 0.3s;
}

.view-all-btn:hover {
  background: var(--color-primary-dark);
}

.loading, .error, .empty {
  text-align: center;
  padding: 40px;
  color: var(--text-main);
  opacity: 0.7;
}

/* 文章列表 */
.posts-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.post-item {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.post-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary);
}

.post-content {
  width: 100%;
}

/* 缩略图样式 */
.post-thumbnail {
  margin-bottom: 12px;
  border-radius: 8px;
  overflow: hidden;
  transition: transform 0.3s ease;
}

.post-thumbnail:hover {
  transform: scale(1.02);
}

.thumbnail-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.thumbnail-image:hover {
  transform: scale(1.05);
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.post-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-main);
  margin: 0;
  line-height: 1.4;
  flex: 1;
  margin-right: 12px;
}

.post-category {
  background: var(--color-primary);
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.post-summary {
  color: var(--text-main);
  opacity: 0.7;
  line-height: 1.6;
  margin: 12px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 0.9rem;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0;
  font-size: 0.85rem;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.author-name {
  color: var(--text-main);
  opacity: 0.8;
  font-weight: 500;
}

.post-stats {
  display: flex;
  gap: 12px;
  color: var(--text-main);
  opacity: 0.6;
}

/* 响应式设计 */
@include respond(md) {
  .posts-section {
    padding: 20px;
  }
}

.section-header {
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.header-actions {
  width: 100%;
  justify-content: flex-start;
}

.post-item {
  padding: 16px;
}

.post-header {
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.post-category {
  align-self: flex-start;
}

.post-meta {
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.thumbnail-image {
  height: 150px;
}

</style>
