<template>
  <div class="content max-w-1200 mx-auto p-20">
    <!-- 标签头部 -->
    <div v-if="tagInfo" class="tag-header p-20 rounded-lg mb-20 text-center">
      <h1 class="tag-title font-bold mb-16 flex flex-ac flex-jc gap-16">
        <span class="tag-icon">🏷️</span>
        {{ tagInfo.name }}
      </h1>
      <div class="flex flex-jc gap-30 flex-fw">
        <div class="flex flex-ac gap-8 text-lg font-medium">
          <span class="text-xl">📄</span>
          <span>{{ tagInfo.postCount || 0 }} 篇文章</span>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-else-if="loading" class="text-center p-20">
      <div class="loading-spinner"></div>
      <p class="loading-text">正在加载标签信息...</p>
    </div>

    <!-- 文章列表部分 -->
    <div v-if="tagInfo" class="mb-20">
      <div class="flex flex-sb flex-ac mb-20 flex-fw gap-16">
        <h2 class="section-title text-2xl font-bold m-0">相关文章</h2>
        <div class="flex flex-ac gap-12">
          <select v-model="sortBy" @change="() => loadPosts()" class="sort-select p-8 border rounded text-sm">
            <option value="latest">最新发布</option>
            <option value="popular">最受欢迎</option>
          </select>
        </div>
      </div>

      <!-- 文章加载状态 -->
      <div v-if="postsLoading" class="text-center p-20">
        <div class="loading-spinner"></div>
        <p class="loading-text">正在加载文章列表...</p>
      </div>

      <!-- 文章列表 -->
      <div v-else-if="posts.length > 0" class="flex flex-col gap-20">
        <article v-for="post in posts" :key="post.id" class="post-card card border rounded-lg transition hover-lift">
          <router-link :to="`/posts/${post.id}`" class="flex no-underline text-color h-full">
            <div class="post-cover">
              <img :src="post.coverImage || post.thumbnail || '/src/assets/image/images.jpg'" :alt="post.title" class="w-full h-full object-cover" />
            </div>
            <div class="p-20 flex-1 flex flex-col">
              <h3 class="post-title text-xl font-bold mb-12">{{ post.title }}</h3>
              <p class="post-summary mb-16" v-if="post.summary">{{ post.summary }}</p>
              <div class="flex gap-20 mb-16 flex-fw">
                <div class="meta-item flex flex-ac gap-8 text-sm">
                  <span class="text-base">👤</span>
                  <span>{{ post.author.username }}</span>
                </div>
                <div class="meta-item flex flex-ac gap-8 text-sm">
                  <span class="text-base">📅</span>
                  <span>{{ formatDate(post.createdAt) }}</span>
                </div>
                <div class="meta-item flex flex-ac gap-8 text-sm">
                  <span class="text-base">👁️</span>
                  <span>{{ post.viewCount || 0 }}</span>
                </div>
                <div class="meta-item flex flex-ac gap-8 text-sm">
                  <span class="text-base">💬</span>
                  <span>{{ post.commentCount || 0 }}</span>
                </div>
              </div>
              <div class="flex gap-8 flex-fw" v-if="post.tags && post.tags.length > 0">
                <router-link 
                  v-for="tag in post.tags" 
                  :key="tag.id" 
                  :to="`/tags/${tag.id}`" 
                  class="tag-link tag p-4 rounded-lg text-xs font-medium no-underline transition"
                >
                  {{ tag.name }}
                </router-link>
              </div>
            </div>
          </router-link>
        </article>
      </div>

      <!-- 空状态 -->
      <div v-else class="text-center p-20">
        <div class="empty-icon mb-20">📝</div>
        <h3 class="text-xl font-semibold mb-12">暂无相关文章</h3>
        <p class="text-base mb-20">该标签下还没有发布任何文章</p>
        <router-link to="/" class="create-btn inline-block p-12 px-24 rounded text-white font-medium no-underline transition">返回首页</router-link>
      </div>

      <!-- 分页组件 -->
      <div v-if="pagination.total > pagination.size" class="flex flex-jc gap-12 mt-20">
        <button 
          class="page-btn p-8 px-16 border rounded cursor-pointer transition" 
          :disabled="pagination.current <= 1"
          @click="changePage(pagination.current - 1)"
        >
          上一页
        </button>
        
        <button 
          v-for="page in getPageNumbers()" 
          :key="page"
          :class="['page-btn p-8 px-16 border rounded cursor-pointer transition', { active: page === pagination.current }]"
          @click="changePage(page)"
        >
          {{ page }}
        </button>
        
        <button 
          class="page-btn p-8 px-16 border rounded cursor-pointer transition" 
          :disabled="pagination.current >= pagination.pages"
          @click="changePage(pagination.current + 1)"
        >
          下一页
        </button>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-if="error" class="empty-state">
      <div class="empty-icon">❌</div>
      <h3>加载失败</h3>
      <p>{{ error }}</p>
      <button @click="loadTagInfo" class="create-btn">重新加载</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { TagService, type Tag } from '@/services/tag'
import { PostService, type PostListItem, type PageResponse } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatDate } from '@/utils/uitls'

// 路由相关
const route = useRoute()
const { showBusinessError } = useErrorHandler()

// 响应式数据
const tagInfo = ref<Tag | null>(null)
const posts = ref<PostListItem[]>([])
const loading = ref(false)
const postsLoading = ref(false)
const error = ref('')
const sortBy = ref<'latest' | 'popular'>('latest')

// 分页数据
const pagination = ref({
  current: 1,
  size: 10,
  total: 0,
  pages: 0
})

// 获取标签ID
const tagId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? parseInt(id) : 0
})



/**
 * 加载标签信息
 */
const loadTagInfo = async () => {
  if (!tagId.value) {
    error.value = '无效的标签ID'
    return
  }

  loading.value = true
  error.value = ''
  
  try {
    tagInfo.value = await TagService.getTagById(tagId.value)
    if (!tagInfo.value) {
      error.value = '标签不存在'
      return
    }
    // 加载标签信息成功后，加载文章列表
    await loadPosts()
  } catch (err: any) {
    console.error('加载标签信息失败:', err)
    showBusinessError(err?.response?.data?.message || err?.message || '加载标签信息失败')
  } finally {
    loading.value = false
  }
}

/**
 * 加载文章列表
 */
const loadPosts = async (page: number = 1) => {
  if (!tagId.value) return

  postsLoading.value = true
  
  try {
    const response: PageResponse<PostListItem> = await PostService.getPostList({
      tagId: tagId.value,
      page,
      size: pagination.value.size,
      sortBy: sortBy.value
    })
    
    posts.value = response.records || []
    pagination.value = {
      current: response.current || 1,
      size: response.size || 10,
      total: response.total || 0,
      pages: response.pages || 0
    }
  } catch (err: any) {
    console.error('加载文章列表失败:', err)
    showBusinessError(err?.response?.data?.message || err?.message || '加载文章列表失败')
  } finally {
    postsLoading.value = false
  }
}

/**
 * 切换页码
 */
const changePage = (page: number) => {
  if (page < 1 || page > pagination.value.pages) return
  loadPosts(page)
}

/**
 * 获取页码数组
 */
const getPageNumbers = () => {
  const current = pagination.value.current
  const total = pagination.value.pages
  const pages: number[] = []
  
  // 显示当前页前后各2页
  const start = Math.max(1, current - 2)
  const end = Math.min(total, current + 2)
  
  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  
  return pages
}

// 组件挂载时加载数据
onMounted(() => {
  loadTagInfo()
})
</script>

<style scoped>
/* 使用 styles.css 工具类简化样式 */
.tag-header {
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: var(--text-color);
}

.tag-title {
  font-size: 2.5rem;
}

.tag-icon {
  font-size: 2rem;
}

.section-title {
  color: var(--text-color);
}

.sort-select {
  background: var(--bg-color);
  color: var(--text-color);
  outline: none;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--border-color);
  border-top: 4px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.post-card {
  background: var(--bg-color);
  overflow: hidden;
}

.post-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  border-color: var(--primary-color);
}

.post-cover {
  width: 200px;
  flex-shrink: 0;
}

.post-title {
  color: var(--text-color);
  line-height: 1.4;
}

.post-summary {
  color: var(--text-color);
  opacity: 0.8;
  line-height: 1.6;
  flex: 1;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-item {
  color: var(--text-color);
  opacity: 0.7;
}

.tag-link {
  background: var(--hover-color);
  color: var(--primary-color);
}

.tag-link:hover {
  background: var(--primary-color);
  color: white;
}

.empty-icon {
  font-size: 4rem;
}

.empty-state h3 {
  color: var(--text-color);
}

.empty-state p {
  color: var(--text-color);
  opacity: 0.7;
}

.create-btn {
  background: var(--primary-color);
  color: white;
}

.create-btn:hover {
  background: var(--primary-hover-color);
}

.page-btn {
  background: var(--bg-color);
  color: var(--text-color);
}

.page-btn:hover {
  background: var(--hover-color);
  border-color: var(--primary-color);
}

.page-btn.active {
  background: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .tag-title {
    font-size: 2rem;
  }
  
  .post-cover {
    width: 100%;
    height: 200px;
  }
}
</style>