<template>
  <div class="content max-w-1200 mx-auto p-20">
    <!-- 标签头部 -->
    <div v-if="tagInfo" class="tag-header card bg-soft p-20 rounded-lg mb-20 text-center">
      <div class="flex flex-col gap-16">
        <div class="flex flex-col gap-12">
          <h1 class="text-2xl font-bold text-primary mb-0 flex flex-ac gap-8">
            <span class="text-3xl">🏷️</span> 标签云
          </h1>
          <p style="text-align: left;" class="text-muted text-base ">
            探索不同主题的文章标签
          </p>
          <div class="flex flex-ac gap-8">
            <span class="badge"> {{ tagInfo.name }}</span>
            <span class="badge">{{ tagInfo.postCount || 0 }} 篇文章</span>
          </div>
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

      <!-- 文章列表：统一首页样式 -->
      <div v-else-if="posts.length > 0" class="list gap-16">
        <article v-for="post in posts" :key="post.id" class="flex gap-16 p-16 rounded-lg transition link card bg-card"
          @click="goToPost(post.id)">
          <!-- 缩略图统一 -->
          <div class="posts-img">
            <img :src="post.coverImage || post.thumbnail || '/src/assets/image/images.jpg'" :alt="post.title"
              class="fit" />
          </div>

          <!-- 内容区统一 -->
          <div class="flex flex-col flex-sb flex-1">
            <div class="flex-1 flex flex-col gap-12">
              <h3 class="font-semibold text-primary text-xl">{{ post.title }}</h3>
              <p v-if="post.summary" class="text-subtle text-base">{{ post.summary }}</p>
              <div v-if="post.tags && post.tags.length > 0" class="tags-cloud">
                <router-link v-for="tag in post.tags" :key="tag.id" :to="`/tags/${tag.id}`" class="tag" @click.stop>
                  {{ tag.name }}
                </router-link>
              </div>
            </div>
            <div class="flex flex-sb flex-ac mt-8">
              <div class="flex flex-ac gap-8 text-subtle">
                <span class="text-sm">{{ post.author?.username || '匿名用户' }}</span>
              </div>
              <div class="flex gap-12 text-sm text-subtle">
                <span>👁️ {{ post.viewCount || 0 }}</span>
                <span>❤️ {{ post.likeCount || 0 }}</span>
                <span>💬 {{ post.commentCount || 0 }}</span>
                <span>{{ formatDate(post.createdAt) }}</span>
              </div>
            </div>
          </div>
        </article>
      </div>

      <!-- 空状态 -->
      <div v-else class="text-center p-20">
        <div class="empty-icon mb-20">📝</div>
        <h3 class="text-xl font-semibold mb-12">暂无相关文章</h3>
        <p class="text-base mb-20">该标签下还没有发布任何文章</p>
        <router-link to="/"
          class="create-btn inline-block p-12 px-24 rounded text-white font-medium no-underline transition">返回首页</router-link>
      </div>

      <!-- 分页组件 -->
      <div v-if="pagination.total > pagination.size" class="flex flex-jc gap-12 mt-20">
        <button class="page-btn p-8 px-16 border rounded cursor-pointer transition" :disabled="pagination.current <= 1"
          @click="changePage(pagination.current - 1)">
          上一页
        </button>

        <button v-for="page in getPageNumbers()" :key="page"
          :class="['page-btn p-8 px-16 border rounded cursor-pointer transition', { active: page === pagination.current }]"
          @click="changePage(page)">
          {{ page }}
        </button>

        <button class="page-btn p-8 px-16 border rounded cursor-pointer transition"
          :disabled="pagination.current >= pagination.pages" @click="changePage(pagination.current + 1)">
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
import { useRoute, useRouter } from 'vue-router'
import { TagService, type Tag } from '@/services/tag'
import { PostService, type PostListItem, type PageResponse } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatDate } from '@/utils/uitls'

// 路由相关
const route = useRoute()
const router = useRouter()
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

    // 更新页面标题
    route.meta.title = `${tagInfo.value.name} - 标签文章`

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
  window.scrollTo({ top: 0, behavior: 'smooth' })
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

/**
 * 跳转到文章详情
 */
const goToPost = (postId: number) => {
  const name = tagInfo.value?.name
  const query = new URLSearchParams({ from: 'tags', tagId: String(tagId.value) })
  if (name) query.set('tagName', name)
  router.push(`/post/${postId}?${query.toString()}`)
}

onMounted(() => {
  loadTagInfo()
})
</script>

<style scoped>
/* 修改人：刘鑫；修改时间：2025-08-26；统一列表为首页样式，仅保留必要差异化样式 */
.tag-header {
  background: linear-gradient(135deg, var(--color-primary), var(--secondary-color));
  color: var(--text-main);
}

.section-title {
  color: var(--text-main);
}

.sort-select {
  background: var(--bg-color);
  color: var(--text-main);
  outline: none;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--border-color);
  border-top: 4px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

/* 统一图片尺寸与卡片结构 */
.posts-img {
  width: 200px;
  height: 150px;
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
}

/* 分页与按钮保留既有样式变量 */
.page-btn {
  background: var(--bg-color);
  color: var(--text-main);
}

.page-btn:hover {
  background: var(--hover-color);
  border-color: var(--color-primary);
}

.page-btn.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

@media (max-width: 768px) {

  .post-cover,
  .posts-img {
    width: 100%;
    height: auto;
  }
}
</style>