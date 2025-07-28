<template>
  <div class="category-posts">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <button class="back-btn" @click="goBack">
          ← 返回
        </button>
        <div class="category-info">
          <h1 class="category-title">
            📂 {{ category?.name || '分类文章' }}
          </h1>
          <p v-if="category?.description" class="category-description">
            {{ category.description }}
          </p>
          <div class="category-stats">
            <span class="post-count">共 {{ totalPosts }} 篇文章</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 文章列表 -->
    <div class="posts-container">
      <div v-if="loading" class="loading">
        <p>加载中...</p>
      </div>
      <div v-else-if="error" class="error">
        <p>{{ error }}</p>
        <button @click="loadPosts" class="retry-btn">重试</button>
      </div>
      <div v-else-if="posts.length === 0" class="empty">
        <div class="empty-content">
          <div class="empty-icon">📝</div>
          <h3>暂无文章</h3>
          <p>该分类下还没有文章</p>
        </div>
      </div>
      <div v-else class="posts-list">
        <article
          v-for="post in posts"
          :key="post.id"
          class="post-item"
          @click="goToPost(post.id)"
        >
          <div class="post-content">
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
                >
                <span class="author-name">{{ post.author?.username || '匿名用户' }}</span>
              </div>
              <div class="post-stats">
                <span class="comment-count">💬 {{ post.commentCount }}</span>
                <span class="post-date">{{ formatDate(post.createdAt) }}</span>
              </div>
            </div>
            <div v-if="post.tags && post.tags.length > 0" class="post-tags">
              <span
                v-for="tag in post.tags"
                :key="tag.id"
                class="tag"
                @click.stop="goToTag(tag.id)"
              >
                {{ tag.name }}
              </span>
            </div>
          </div>
        </article>
      </div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="pagination">
        <button
          class="page-btn"
          :disabled="currentPage <= 1"
          @click="changePage(currentPage - 1)"
        >
          上一页
        </button>
        <span class="page-info">
          第 {{ currentPage }} 页，共 {{ totalPages }} 页
        </span>
        <button
          class="page-btn"
          :disabled="currentPage >= totalPages"
          @click="changePage(currentPage + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { PostService, type PostListItem } from '@/services/post'
import type { Category } from '@/services/category'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCategoryStore } from '@/stores/category'

const router = useRouter()
const route = useRoute()
const { handleAsync } = useErrorHandler()
const categoryStore = useCategoryStore()

// 响应式数据
const posts = ref<PostListItem[]>([])
const category = ref<Category | null>(null)
const loading = ref(false)
const error = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const totalPosts = ref(0)

// 计算属性
const totalPages = computed(() => Math.ceil(totalPosts.value / pageSize.value))

// 获取分类ID
const categoryId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? parseInt(id[0]) : parseInt(id as string)
})

// 加载分类信息
const loadCategory = async () => {
  await handleAsync(async () => {
    const categoryData = await categoryStore.fetchCategoryById(categoryId.value)
    category.value = categoryData
  }, {
    onError: (err) => {
      console.error('加载分类信息失败:', err)
    }
  })
}

// 加载文章列表
const loadPosts = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''

    const response = await PostService.getPostList({
      page: currentPage.value,
      size: pageSize.value,
      categoryId: categoryId.value
    })

    posts.value = response.records
    totalPosts.value = response.total
  }, {
    onError: (err) => {
      error.value = '加载文章失败，请稍后重试'
      console.error('加载文章失败:', err)
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

// 切换页面
const changePage = (page: number) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    loadPosts()
    // 滚动到顶部
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/post/${postId}`)
}

// 跳转到标签页面
const goToTag = (tagId: number) => {
  router.push(`/tag/${tagId}`)
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 格式化日期
const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

// 组件挂载时加载数据
onMounted(() => {
  Promise.all([
    loadCategory(),
    loadPosts()
  ])
})
</script>

<style scoped>
.category-posts {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 2px solid var(--border-color);
}

.header-content {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

.back-btn {
  background: var(--primary-color);
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.back-btn:hover {
  background: var(--primary-hover-color);
  transform: translateY(-1px);
}

.category-info {
  flex: 1;
}

.category-title {
  font-size: 2rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 10px 0;
}

.category-description {
  color: var(--text-color);
  opacity: 0.8;
  margin: 0 0 15px 0;
  line-height: 1.6;
}

.category-stats {
  display: flex;
  gap: 20px;
}

.post-count {
  color: var(--text-color);
  opacity: 0.7;
  font-size: 14px;
}

.posts-container {
  min-height: 400px;
}

.loading, .error, .empty {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-color);
}

.empty-content {
  max-width: 300px;
  margin: 0 auto;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 20px;
}

.empty-content h3 {
  color: var(--text-color);
  margin: 0 0 10px 0;
}

.empty-content p {
  color: var(--text-color);
  opacity: 0.7;
  margin: 0;
}

.retry-btn {
  background: var(--primary-color);
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  margin-top: 10px;
}

.retry-btn:hover {
  background: var(--primary-hover-color);
}

.posts-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.post-item {
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.post-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  border-color: var(--primary-color);
}

.post-content {
  width: 100%;
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
  gap: 16px;
}

.post-title {
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
  line-height: 1.4;
  flex: 1;
}

.post-category {
  background: var(--primary-color);
  color: white;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.post-summary {
  color: var(--text-color);
  opacity: 0.8;
  line-height: 1.6;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
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
  color: var(--text-color);
  font-size: 14px;
  font-weight: 500;
}

.post-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  color: var(--text-color);
  opacity: 0.7;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  background: var(--tag-bg-color, #f0f0f0);
  color: var(--tag-text-color, #666);
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tag:hover {
  background: var(--primary-color);
  color: white;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  margin-top: 40px;
  padding: 20px;
}

.page-btn {
  background: var(--primary-color);
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.page-btn:hover:not(:disabled) {
  background: var(--primary-hover-color);
}

.page-btn:disabled {
  background: var(--border-color);
  cursor: not-allowed;
  opacity: 0.5;
}

.page-info {
  color: var(--text-color);
  font-size: 14px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .category-posts {
    padding: 15px;
  }
  
  .header-content {
    flex-direction: column;
    gap: 15px;
  }
  
  .category-title {
    font-size: 1.5rem;
  }
  
  .post-item {
    padding: 16px;
  }
  
  .post-header {
    flex-direction: column;
    gap: 8px;
  }
  
  .post-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .pagination {
    flex-direction: column;
    gap: 10px;
  }
}
</style>