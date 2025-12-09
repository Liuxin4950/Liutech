<template>
  <div class="category-posts content">
    <!-- 页面头部 -->
    <div class="card bg-soft mb-16">
      <div class="flex flex-col gap-16">
        <div class="flex flex-col gap-12">
          <h1 class="text-xl font-semibold text-primary mb-0 flex flex-ac gap-8">
            <span class="text-2xl">📂</span> {{ category?.name || '分类文章' }}
          </h1>
          <p v-if="category?.description" class="text-subtle text-base mb-0">
            {{ category.description }}
          </p>
          <div class="flex flex-ac gap-8">
            <span class="badge">共 {{ totalPosts }} 篇文章</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 文章列表：统一为首页样式 -->
    <div>
      <div v-if="loading" class="loading-text">加载中...</div>
      <div v-else-if="error" class="loading-text">
        <p>{{ error }}</p>
        <button @click="loadPosts" class="bg-primary text-sm font-medium p-8 rounded transition mt-8">重试</button>
      </div>
      <div v-else-if="posts.length === 0" class="text-center p-20">
        <div class="text-lg mb-8">📝</div>
        <h3 class="text-base font-semibold mb-8">暂无文章</h3>
        <p class="text-muted text-sm mb-0">该分类下还没有文章</p>
      </div>

      <div v-else class="list gap-16">
        <article
          v-for="post in posts"
          :key="post.id"
          class="flex gap-16 p-16 rounded-lg transition link card bg-card "
          @click="goToPost(post.id)"
        >
          <!-- 缩略图与尺寸统一 -->
          <div class="posts-img">
            <img :src="getPostImage(post)" :alt="post.title" class="fit" @error="handleImageError" />
          </div>

          <!-- 文章内容，结构与首页一致 -->
          <div class="flex flex-col flex-sb flex-1 relative">
            <span v-if="post.category" class="badge">{{ post.category.name }}</span>
            <div class="flex-1 flex flex-col gap-12">
              <h3 class="font-semibold text-primary text-xl">{{ post.title }}</h3>
              <p v-if="post.summary" class="text-subtle text-base post-summary">
                {{ post.summary }}
              </p>
              <div v-if="post.tags && post.tags.length > 0" class="tags-cloud">
                <span v-for="tag in post.tags" :key="tag.id" class="tag" @click.stop="goToTag(tag.id)">
                  {{ tag.name }}
                </span>
              </div>
            </div>

            <div class="flex flex-sb flex-ac mt-8">
              <div class="flex flex-ac gap-8 text-subtle">
                <img v-if="post.author?.avatarUrl" :src="post.author.avatarUrl" :alt="post.author.username" class="rounded" style="width: 24px; height: 24px; object-fit: cover;" />
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
        :current-page="currentPage"
        :total-pages="totalPages"
        :show-page-numbers="false"
        @page-change="changePage"
      />
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
import { formatDate } from '@/utils/uitls'
import Pagination from '@/components/Pagination.vue'

// 修改人：刘鑫；修改时间：2025-08-26；说明：统一分类详情列表为首页样式，简化局部样式，保留公共样式与颜色

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

    // 动态更新路由meta信息：仅保留标题
    if (categoryData && route.meta) {
      route.meta.title = `${categoryData.name} - 分类文章`
    }
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
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

// 跳转到文章详情
const goToPost = (postId: number) => {
  const id = categoryId.value
  const name = category.value?.name
  const query = new URLSearchParams({ from: 'categories' })
  if (id) query.set('categoryId', String(id))
  if (name) query.set('categoryName', name)
  router.push(`/post/${postId}?${query.toString()}`)
}

// 跳转到标签页面
const goToTag = (tagId: number) => {
  router.push(`/tags/${tagId}`)
}

// 获取文章图片
const getPostImage = (post: PostListItem) => {
  return post.coverImage || post.thumbnail || '/src/assets/image/images.jpg'
}

// 处理图片加载错误
const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.src = '/src/assets/image/images.jpg'
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
/* 修改人：刘鑫；修改时间：2025-08-26；仅保留必要样式，复用首页图片尺寸与卡片结构 */
.category-posts { padding: 20px; }

.posts-img { width: 200px; height: 150px; background-color: white; border-radius: 12px; overflow: hidden; }

.loading-text { text-align: center; padding: 40px 20px; color: var(--text-muted); }

/* 文章摘要省略号样式 */
.post-summary {
  display: -webkit-box;
  -webkit-line-clamp: 2; /* 限制显示2行 */
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  max-height: 3em; /* 2行的高度 (1.5 * 2) */
  word-break: break-word;
}

.relative > .badge{
  position: absolute;
  top: 0;
  right: 0;
  opacity: 0;
  transition: .5s;
}
.relative:hover .badge{
  opacity: 1;
}

@media (max-width: 768px) {
  .category-posts { padding: 15px; }
  article { flex-direction: column !important; }
  .posts-img { width: 100%; height: auto; }
}
</style>