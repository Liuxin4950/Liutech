<template>
  <div>
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-text text-sm" style="text-align:center">加载中...</div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="loading-text text-primary text-sm" style="display:flex;flex-direction:column;align-items:center">
      <p>{{ error }}</p>
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
      <button @click="$emit('retry')" class="retry-btn">重试</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="posts.length === 0" class="empty-text flex flex-col flex-ac text-sm" style="text-align:center">
      <slot name="empty">
        <img src="@/assets/image/扑到.png" alt="" class="fit-err">
        <p>暂无文章</p>
      </slot>
    </div>

    <!-- 文章列表 -->
    <div v-else class="list gap-16">
      <article
        v-for="post in posts"
        :key="post.id"
        class="article-box flex gap-16 p-16 rounded-lg transition link card bg-card"
        @click="$emit('post-click', post.id)"
      >
        <!-- 缩略图 -->
        <div class="posts-img">
          <img
            :src="post.thumbnail || post.coverImage || defaultPostImage"
            :alt="post.title"
            class="fit"
            loading="lazy"
            @error="handleImageError"
          />
        </div>

        <div class="flex flex-col flex-sb flex-1 relative article-content ">
          <span v-if="post.category" class="article-category" @click.stop="handleCategoryClick(post.category.id)">{{ post.category.name }}</span>
          <div class="flex-1 flex flex-col gap-12 article-content-box">
            <h3 class="font-semibold post-title">{{ post.title }}</h3>
            <p v-if="post.summary" class="text-subtle text-sm post-summary">
              {{ post.summary }}
            </p>
            <div class="tags-cloud" v-if="post.tags && post.tags.length > 0">
              <span v-for="tag in post.tags" :key="tag.id" class="tag" @click.stop="handleTagClick(tag.id)">
                {{ tag.name }}
              </span>
            </div>
          </div>

          <div class="article-meta mt-8">
            <div class="flex flex-ac gap-8 text-subtle">
              <img
                v-if="post.author?.avatarUrl"
                :src="post.author.avatarUrl"
                :alt="post.author.username"
                class="rounded"
                style="width: 24px; height: 24px; object-fit: cover"
                @error="handleImageError"
              />
              <span class="text-sm">{{ post.author?.username || '匿名用户' }}</span>
            </div>
            <div class="meta-stats flex gap-12 text-sm text-subtle">
              <span class="flex flex-ac gap-4"><Icon name="eye" size="14" /> {{ post.viewCount || 0 }}</span>
              <span class="flex flex-ac gap-4"><Icon name="heart" size="14" /> {{ post.likeCount || 0 }}</span>
              <span class="flex flex-ac gap-4"><Icon name="message" size="14" /> {{ post.commentCount || 0 }}</span>
              <span>{{ showViewedAt && post.viewedAt ? '浏览于 ' + formatDate(post.viewedAt) : formatDate(post.createdAt) }}</span>
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
import { formatDate } from '@/utils/utils'
import { handleImageError } from '@/composables/useImageFallback'
import Pagination from '@/components/Pagination.vue'
import Icon from './Icon.vue'
import defaultPostImage from '@/assets/image/err.png'

const router = useRouter()

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
  /** 浏览历史模式：时间显示"浏览于 viewedAt"而非文章发布时间 */
  showViewedAt?: boolean
}>()

const emit = defineEmits<{
  'post-click': [postId: number]
  'page-change': [page: number]
  'retry': []
}>()

function handleTagClick(tagId: number) {
  router.push(`/tags/${tagId}`)
}

function handleCategoryClick(categoryId: number) {
  router.push(`/category-detail/${categoryId}`)
}
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

  .article-image{
    width: 200px;
    height: 200px;
    position: absolute;
    top: 0;
    right: 0;
    transform: translateY(-11px);
      @include respond(md) {
        display: none;
      }
  }

  .article-box{
    position: relative;
    @include respond(lg) {
      flex-wrap: wrap;
    }
  }

  .article-content{
    width: 100%;
    min-width: 0; /* 允许文章内容区收缩，标题 nowrap 时触发省略号而不是撑破列 */
  }
  .article-content-box{
    justify-content: space-around;
  }

  .article-category {
    /* 分类角标：主题色药丸（与全局 tag / title-badge 语言统一） */
    display: inline-flex;
    align-items: center;
    padding: 3px 12px;
    border-radius: 30px;
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-primary);
    background: rgba(var(--color-primary-rgb), 0.1);
    border: 1px solid rgba(var(--color-primary-rgb), 0.16);
    cursor: pointer;
    transition: all 0.2s ease;
    position: absolute;
    top: 0;
    right: 0;

    &:hover {
      background: var(--color-primary);
      color: #fff;
    }

    @include respond(md) {
      position: static;
      align-self: flex-start;
      margin-bottom: 8px;
    }
  }
.posts-img {
  width: 240px;
  height: 170px;
  border-radius: 8px;
  overflow: hidden;
   @include respond(md) {
        width: 100%;
        height: 200px;
  }
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

.post-title{
    color: var(--text-title);
    font-size: 1.25rem;
    padding-right: 70px;
    white-space: normal;// 换两行
    overflow: hidden;// 超出部分隐藏，不显示滚动条等
    text-overflow: ellipsis;// 超出部分省略号显示
    @include respond(md) {
      white-space: normal;
      padding-right: 0;
    }
}
.post-summary {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  max-height: 3em;
  word-break: break-word;
  padding-right: 20px;
  @include respond(md) {
    padding-right: 0;
  }
}

.article-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  @include respond(sm) {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}

.meta-stats {
  @include respond(sm) {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
