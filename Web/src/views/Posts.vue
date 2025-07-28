<template>
  <div class="posts-page">
    <div class="page-header">
      <h1>📚 全部文章</h1>
      <div class="header-actions">
        <button class="create-btn" @click="router.push('/create')">
          ✍️ 发布文章
        </button>
      </div>
    </div>

    <!-- 筛选器 -->
    <div class="filters">
      <div class="filter-group">
        <label>分类：</label>
        <select v-model="filters.categoryId" @change="handleFilterChange">
          <option value="">全部分类</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">
            {{ category.name }}
          </option>
        </select>
      </div>
      
      <div class="filter-group">
        <label>标签：</label>
        <select v-model="filters.tagId" @change="handleFilterChange">
          <option value="">全部标签</option>
          <option v-for="tag in tags" :key="tag.id" :value="tag.id">
            {{ tag.name }}
          </option>
        </select>
      </div>
      
      <div class="filter-group">
        <label>排序：</label>
        <select v-model="filters.sortBy" @change="handleFilterChange">
          <option value="latest">最新发布</option>
          <option value="popular">最受欢迎</option>
        </select>
      </div>
      
      <div class="search-group">
        <input 
          v-model="searchKeyword" 
          type="text" 
          placeholder="搜索文章标题或内容..."
          @keyup.enter="handleSearch"
          class="search-input"
        >
        <button @click="handleSearch" class="search-btn">🔍</button>
      </div>
    </div>

    <!-- 文章列表 -->
    <div class="posts-container">
      <div v-if="loading" class="loading">
        <p>加载中...</p>
      </div>
      
      <div v-else-if="error" class="error">
        <p>{{ error }}</p>
        <button @click="loadPosts()" class="retry-btn">重试</button>
      </div>
      
      <div v-else-if="posts.length === 0" class="empty">
        <p>暂无文章</p>
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
            
            <div class="post-tags" v-if="post.tags && post.tags.length > 0">
              <span v-for="tag in post.tags" :key="tag.id" class="tag">
                {{ tag.name }}
              </span>
            </div>
            
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
          </div>
        </article>
      </div>
    </div>

    <!-- 分页器 -->
    <div v-if="!loading && posts.length > 0" class="pagination">
      <button 
        @click="goToPage(pagination.current - 1)" 
        :disabled="pagination.current <= 1"
        class="page-btn"
      >
        ⬅️ 上一页
      </button>
      
      <div class="page-info">
        <span class="page-numbers">
          <button 
            v-for="page in visiblePages" 
            :key="page"
            @click="goToPage(page)"
            :class="['page-number', { active: page === pagination.current }]"
          >
            {{ page }}
          </button>
        </span>
        
        <span class="page-text">
          第 {{ pagination.current }} 页，共 {{ pagination.pages }} 页
        </span>
      </div>
      
      <button 
        @click="goToPage(pagination.current + 1)" 
        :disabled="pagination.current >= pagination.pages"
        class="page-btn"
      >
        下一页 ➡️
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostListItem, PostQueryParams } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'

const router = useRouter()
const route = useRoute()
const { handleAsync } = useErrorHandler()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()

// 响应式数据
const posts = ref<PostListItem[]>([])
const loading = ref(false)
const error = ref('')
const searchKeyword = ref('')

// 筛选条件
const filters = ref({
  categoryId: '',
  tagId: '',
  sortBy: 'latest'
})

// 分页信息
const pagination = ref({
  current: 1,
  size: 10,
  total: 0,
  pages: 0
})

// 从store获取数据
const categories = computed(() => categoryStore.categories)
const tags = computed(() => tagStore.tags)

// 计算可见的页码
const visiblePages = computed(() => {
  const current = pagination.value.current
  const total = pagination.value.pages
  const pages: number[] = []
  
  if (total <= 7) {
    // 总页数小于等于7，显示全部页码
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
  } else {
    // 总页数大于7，显示部分页码
    if (current <= 4) {
      // 当前页在前4页
      for (let i = 1; i <= 5; i++) {
        pages.push(i)
      }
      pages.push(-1) // 省略号
      pages.push(total)
    } else if (current >= total - 3) {
      // 当前页在后4页
      pages.push(1)
      pages.push(-1) // 省略号
      for (let i = total - 4; i <= total; i++) {
        pages.push(i)
      }
    } else {
      // 当前页在中间
      pages.push(1)
      pages.push(-1) // 省略号
      for (let i = current - 1; i <= current + 1; i++) {
        pages.push(i)
      }
      pages.push(-1) // 省略号
      pages.push(total)
    }
  }
  
  return pages
})

// 加载文章列表
const loadPosts = async (page: number = 1) => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''

    const params: PostQueryParams = {
      page,
      size: pagination.value.size,
      sortBy: filters.value.sortBy as 'latest' | 'popular'
    }

    // 添加筛选条件
    if (filters.value.categoryId) {
      params.categoryId = Number(filters.value.categoryId)
    }
    if (filters.value.tagId) {
      params.tagId = Number(filters.value.tagId)
    }
    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }

    const response = await PostService.getPosts(params)
    posts.value = response.records
    pagination.value = {
      current: response.current,
      size: response.size,
      total: response.total,
      pages: response.pages
    }
  }, {
    onError: (err) => {
      error.value = '加载文章列表失败，请稍后重试'
      console.error('加载文章列表失败:', err)
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

// 跳转到指定页面
const goToPage = (page: number) => {
  if (page < 1 || page > pagination.value.pages || page === pagination.value.current) {
    return
  }
  
  // 更新URL参数
  const query = { ...route.query, page: page.toString() }
  router.push({ query })
  
  loadPosts(page)
}

// 处理筛选条件变化
const handleFilterChange = () => {
  // 重置到第一页
  const query = { ...route.query, page: '1' }
  router.push({ query })
  
  loadPosts(1)
}

// 处理搜索
const handleSearch = () => {
  // 重置到第一页
  const query = { ...route.query, page: '1' }
  router.push({ query })
  
  loadPosts(1)
}

// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/post/${postId}`)
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

// 监听路由变化
watch(() => route.query.page, (newPage) => {
  const page = Number(newPage) || 1
  if (page !== pagination.value.current) {
    loadPosts(page)
  }
})

// 组件挂载时加载数据
onMounted(async () => {
  // 从URL获取初始页码
  const page = Number(route.query.page) || 1
  
  // 加载分类和标签数据
  await Promise.all([
    categoryStore.fetchCategories(),
    tagStore.fetchTags()
  ])
  
  // 加载文章列表
  await loadPosts(page)
})
</script>

<style scoped>
.posts-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid var(--border-color);
}

.page-header h1 {
  font-size: 2rem;
  color: var(--text-color);
  margin: 0;
}

.create-btn {
  padding: 10px 20px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.3s;
}

.create-btn:hover {
  background: var(--secondary-color);
}

/* 筛选器样式 */
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  background: var(--bg-color);
  padding: 20px;
  border-radius: 12px;
  margin-bottom: 24px;
  border: 1px solid var(--border-color);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-group label {
  font-weight: 500;
  color: var(--text-color);
  white-space: nowrap;
}

.filter-group select {
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-color);
  color: var(--text-color);
  font-size: 0.9rem;
  min-width: 120px;
}

.search-group {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.search-input {
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-color);
  color: var(--text-color);
  font-size: 0.9rem;
  width: 250px;
}

.search-btn {
  padding: 8px 12px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.search-btn:hover {
  background: var(--secondary-color);
}

/* 文章容器 */
.posts-container {
  background: var(--bg-color);
  border-radius: 12px;
  padding: 24px;
  border: 1px solid var(--border-color);
  margin-bottom: 24px;
}

.loading, .error, .empty {
  text-align: center;
  padding: 60px;
  color: var(--text-color);
  opacity: 0.7;
}

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.retry-btn:hover {
  background: var(--secondary-color);
}

/* 文章列表 */
.posts-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.post-item {
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.post-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.1);
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
}

.post-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
  line-height: 1.4;
  flex: 1;
  margin-right: 12px;
}

.post-category {
  background: var(--primary-color);
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.post-summary {
  color: var(--text-color);
  opacity: 0.7;
  line-height: 1.6;
  margin: 12px 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 0.95rem;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 12px 0;
}

.tag {
  background: var(--hover-color);
  color: var(--text-color);
  opacity: 0.8;
  padding: 3px 8px;
  border-radius: 8px;
  font-size: 0.75rem;
  font-weight: 500;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  font-size: 0.85rem;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}

.author-name {
  color: var(--text-color);
  opacity: 0.8;
  font-weight: 500;
}

.post-stats {
  display: flex;
  gap: 12px;
  color: var(--text-color);
  opacity: 0.6;
}

/* 分页器样式 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
}

.page-btn {
  padding: 8px 16px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 0.9rem;
}

.page-btn:hover:not(:disabled) {
  background: var(--secondary-color);
}

.page-btn:disabled {
  background: var(--border-color);
  cursor: not-allowed;
  opacity: 0.5;
}

.page-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.page-number {
  padding: 6px 10px;
  background: transparent;
  color: var(--text-color);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 0.9rem;
  min-width: 36px;
}

.page-number:hover {
  background: var(--hover-color);
}

.page-number.active {
  background: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
}

.page-text {
  color: var(--text-color);
  opacity: 0.7;
  font-size: 0.9rem;
  white-space: nowrap;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .posts-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .page-header h1 {
    font-size: 1.6rem;
  }

  .filters {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .search-group {
    margin-left: 0;
  }

  .search-input {
    width: 100%;
  }

  .posts-container {
    padding: 16px;
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

  .pagination {
    flex-direction: column;
    gap: 12px;
  }

  .page-info {
    flex-direction: column;
    gap: 8px;
  }

  .page-numbers {
    flex-wrap: wrap;
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .posts-page {
    padding: 12px;
  }

  .page-header h1 {
    font-size: 1.4rem;
  }

  .post-title {
    font-size: 1.1rem;
  }
}
</style>