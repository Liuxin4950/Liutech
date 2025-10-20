<template>
  <div class="posts-page">
    <div class="flex flex-sb flex-ac mb-16">
      <h1 class="text-lg font-semibold text-primary mb-0">📚 全部文章</h1>
      <button class="bg-primary text-base font-medium p-12 rounded transition hover-lift" @click="router.push('/create')">
        ✍️ 发布文章
      </button>
    </div>

    <!-- 筛选器 -->
    <div class="card bg-card flex flex-fw gap-16 flex-ac mb-16">
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
          class="flex gap-16 p-16 rounded-lg transition link card bg-card"
          @click="goToPost(post.id)"
        >
          <!-- 缩略图容器，统一为首页 posts-img 结构 -->
          <div class="posts-img">
            <img 
              :src="post.coverImage || post.thumbnail || '/src/assets/image/images.jpg'" 
              :alt="post.title" 
              class="fit"
            >
          </div>
          
          <div class="flex flex-col flex-sb flex-1 relative">
            <span v-if="post.category" class="badge">{{ post.category.name }}</span>
            <div class="flex-1 flex flex-col gap-12">
              <h3 class="font-semibold text-primary text-xl">{{ post.title }}</h3>
              <p v-if="post.summary" class="text-subtle text-base text-sm">{{ post.summary }}</p>
            
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
                  style="width: 24px; height: 24px; object-fit: cover;"
                >
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
    </div>

    <!-- 分页器 -->
    <Pagination 
      v-if="!loading && posts.length > 0"
      :current-page="pagination.current"
      :total-pages="pagination.pages"
      @page-change="goToPage"
    />
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
import Pagination from '@/components/Pagination.vue'

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

// 注意：visiblePages 计算属性已移除，现在使用 Pagination 组件内部处理

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

// 跳转到文章详情（带 from=posts，便于面包屑返回“全部文章”）
const goToPost = (postId: number) => {
  router.push(`/post/${postId}?from=posts`)
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

<style scoped lang="scss">
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
.relative > .badge{
  position: absolute;
  top: 0;
  right: 0;
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

/* 统一首页与全部文章列表的图片容器尺寸与样式 */
.posts-img { width: 200px; height: 150px; background-color: white; border-radius: 12px; overflow: hidden; }

@use "@/assets/styles/tokens" as *;

.card.bg-card.flex.flex-fw.gap-16.flex-ac {
  @include respond(md) {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    
    .flex.flex-ac.gap-8 {
      flex-wrap: wrap;
      
      &.flex-1 {
        flex: none;
      }
    }
  }
}

.posts-img {
  @include respond(md) {
    aspect-ratio: 16/9;
  }
  
  @include respond(sm) {
    aspect-ratio: 16/9;
  }
}

article.flex.gap-16 {
  @include respond(md) {
    flex-direction: column;
    gap: 12px;
  }
}
</style>