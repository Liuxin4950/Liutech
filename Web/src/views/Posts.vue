<template>
  <div class="posts-page">
    <div class="flex flex-sb flex-ac mb-16">
      <h1 class="text-lg font-semibold text-primary mb-0">📚 全部文章</h1>
      <button class="bg-primary text-base font-medium p-12 rounded transition hover-lift" @click="router.push('/create')">
        ✍️ 发布文章
      </button>
    </div>

    <!-- 筛选器 -->
    <div class="card flex flex-fw gap-16 flex-ac mb-16">
      <div class="flex flex-ac gap-8">
        <label class="font-medium text-muted">分类：</label>
        <select v-model="filters.categoryId" @change="handleFilterChange" class="p-8 rounded border-t text-sm">
          <option value="">全部分类</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">
            {{ category.name }}
          </option>
        </select>
      </div>
      
      <div class="flex flex-ac gap-8">
        <label class="font-medium text-muted">标签：</label>
        <select v-model="filters.tagId" @change="handleFilterChange" class="p-8 rounded border-t text-sm">
          <option value="">全部标签</option>
          <option v-for="tag in tags" :key="tag.id" :value="tag.id">
            {{ tag.name }}
          </option>
        </select>
      </div>
      
      <div class="flex flex-ac gap-8">
        <label class="font-medium text-muted">排序：</label>
        <select v-model="filters.sortBy" @change="handleFilterChange" class="p-8 rounded border-t text-sm">
          <option value="latest">最新发布</option>
          <option value="popular">最受欢迎</option>
        </select>
      </div>
      
      <div class="flex flex-ac gap-8 flex-1">
        <input 
          v-model="searchKeyword" 
          type="text" 
          placeholder="搜索文章标题或内容..."
          class="flex-1 p-8 rounded border-t text-sm"
          @keyup.enter="handleSearch"
        >
        <button @click="handleSearch" class="bg-primary text-sm font-medium p-8 rounded transition hover-lift">
          🔍 搜索
        </button>
      </div>
    </div>

    <!-- 文章列表 -->
    <div class="card">
      <div v-if="loading" class="loading-text">加载中...</div>
      
      <div v-else-if="error" class="loading-text text-primary">
        <p>{{ error }}</p>
        <button @click="loadPosts()" class="retry-btn">重试</button>
      </div>
      
      <div v-else-if="posts.length === 0" class="empty-text">暂无文章</div>
      
      <div v-else class="list gap-16">
        <article
          v-for="post in posts"
          :key="post.id"
          class="flex gap-16 p-16 bg-hover rounded-lg transition hover-lift link border-l-3"
          @click="goToPost(post.id)"
        >
          <!-- 缩略图 -->
          <div class="flex-shrink-0">
            <img 
              :src="post.thumbnail || post.coverImage || '/src/assets/image/images.jpg'" 
              :alt="post.title" 
              class="rounded" 
              style="width: 120px; height: 80px; object-fit: cover;"
            >
          </div>
          
          <div class="flex flex-col gap-8 flex-1">
            <div class="flex flex-sb flex-ac">
              <h3 class="text-lg font-semibold text-primary mb-0">{{ post.title }}</h3>
              <span v-if="post.category" class="badge">{{ post.category.name }}</span>
            </div>
            
            <p v-if="post.summary" class="text-muted text-base mb-0" style="display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">{{ post.summary }}</p>
            
            <div class="tags-cloud" v-if="post.tags && post.tags.length > 0">
              <span v-for="tag in post.tags" :key="tag.id" class="tag">
                {{ tag.name }}
              </span>
            </div>
            
            <div class="flex flex-sb flex-ac mt-8">
              <div class="flex flex-ac gap-8">
                <img
                  v-if="post.author?.avatarUrl"
                  :src="post.author.avatarUrl"
                  :alt="post.author.username"
                  class="rounded"
                  style="width: 24px; height: 24px; object-fit: cover;"
                >
                <span class="text-sm font-medium">{{ post.author?.username || '匿名用户' }}</span>
              </div>
              <div class="flex gap-12 text-sm text-muted">
                <span>👁️ {{ post.viewCount || 0 }}</span>
                <span>❤️ {{ post.likeCount || 0 }}</span>
                <span>💬 {{ post.commentCount }}</span>
                <span>{{ formatDate(post.createdAt) }}</span>
              </div>
            </div>
          </div>
        </article>
      </div>
    </div>

    <!-- 分页器 -->
    <div v-if="!loading && posts.length > 0" class="card flex flex-jc flex-ac gap-16">
      <button 
        @click="goToPage(pagination.current - 1)" 
        :disabled="pagination.current <= 1"
        class="bg-primary text-sm font-medium p-8 rounded transition hover-lift"
        :class="{ 'opacity-50 cursor-not-allowed': pagination.current <= 1 }"
      >
        ⬅️ 上一页
      </button>
      
      <div class="flex flex-ac gap-16">
        <span class="flex gap-4">
          <button 
            v-for="page in visiblePages" 
            :key="page"
            @click="goToPage(page)"
            :class="['text-sm p-8 rounded transition hover-lift', { 'bg-primary text-white': page === pagination.current, 'bg-hover': page !== pagination.current }]"
          >
            {{ page }}
          </button>
        </span>
        
        <span class="text-sm text-muted">
          第 {{ pagination.current }} 页，共 {{ pagination.pages }} 页
        </span>
      </div>
      
      <button 
        @click="goToPage(pagination.current + 1)" 
        :disabled="pagination.current >= pagination.pages"
        class="bg-primary text-sm font-medium p-8 rounded transition hover-lift"
        :class="{ 'opacity-50 cursor-not-allowed': pagination.current >= pagination.pages }"
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
import { formatDate } from '@/utils/uitls'

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
    
    console.log('API返回的完整数据:', response)
    console.log('文章列表数据:', response.records)
    if (response.records && response.records.length > 0) {
      console.log('第一篇文章的数据:', response.records[0])
      console.log('第一篇文章的thumbnail:', response.records[0].thumbnail)
      console.log('第一篇文章的coverImage:', response.records[0].coverImage)
    }
    
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