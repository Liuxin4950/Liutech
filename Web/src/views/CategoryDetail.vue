<template>
  <div class="category-posts content">
    <!-- 页面头部 -->
    <div class="card bg-soft mb-16">
      <div class="flex flex-col gap-16">
        <!-- <button class="bg-hover p-8 rounded text-sm font-medium link transition self-start hover:bg-primary hover:text-white" @click="goBack">
          ← 返回
        </button> -->
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

    <!-- 文章列表 -->
    <div class="">
      <div v-if="loading" class="loading-text">加载中...</div>
      <div v-else-if="error" class="loading-text">
        <p>{{ error }}</p>
        <button @click="loadPosts"
          class="bg-primary text-sm font-medium p-8 rounded transitionmt-8">重试</button>
      </div>
      <div v-else-if="posts.length === 0" class="text-center p-20">
        <div class="text-lg mb-8">📝</div>
        <h3 class="text-base font-semibold mb-8">暂无文章</h3>
        <p class="text-muted text-sm mb-0">该分类下还没有文章</p>
      </div>
      <div v-else class="list gap-16">
        <article v-for="post in posts" :key="post.id" class="card bg-soft flex gap-16 p-16 rounded-lg transition link "
          :style="{ borderLeftColor: `var(--color-primary)` }" @click="goToPost(post.id)">
          <!-- 文章图片 -->
          <div class="post-image rounded-lg">
            <img :src="getPostImage(post)" :alt="post.title" class="rounded-lg"
              style="width: 200px; height: 150px; object-fit: cover;" @error="handleImageError">
          </div>

          <!-- 文章内容 -->
          <div class="flex flex-col gap-12 flex-1">
            <div class="flex flex-sb flex-ac gap-12">
              <h3 class="text-xl text-primary">{{ post.title }}</h3>
              <span v-if="post.category" class="badge ">{{ post.category.name }}</span>
            </div>
            <p v-if="post.summary" class="text-muted text-base mb-0"
              style="display: -webkit-box; -webkit-box-orient: vertical; overflow: hidden;">{{ post.summary }}</p>
            <div v-if="post.tags && post.tags.length > 0" class="tags-cloud">
              <span v-for="tag in post.tags" :key="tag.id" class="tag" @click.stop="goToTag(tag.id)">
                {{ tag.name }}
              </span>
            </div>
            <div class="flex flex-sb flex-ac">
              <div class="flex flex-ac gap-8">
                <img v-if="post.author?.avatarUrl" :src="post.author.avatarUrl" :alt="post.author.username"
                  class="rounded" style="width: 24px; height: 24px; object-fit: cover;">
                <span class="text-sm font-medium">{{ post.author?.username || '匿名用户' }}</span>
              </div>
              <div class="flex gap-16 text-sm text-muted flex-wrap">
                <span class="flex flex-ac gap-4">👁️ {{ post.viewCount || 0 }}</span>
                <span class="flex flex-ac gap-4">❤️ {{ post.likeCount || 0 }}</span>
                <span class="flex flex-ac gap-4">💬 {{ post.commentCount }}</span>
                <span class="flex flex-ac gap-4">📅 {{ formatDate(post.createdAt) }}</span>
              </div>
            </div>

          </div>
        </article>
      </div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="card flex flex-jc flex-ac gap-16 mt-16">
        <button class="bg-primary text-sm font-medium px-16 py-8 rounded transition hover-lift shadow-sm"
          :class="{ 'opacity-50 cursor-not-allowed': currentPage <= 1 }" :disabled="currentPage <= 1"
          @click="changePage(currentPage - 1)">
          ← 上一页
        </button>
        <span class="text-sm text-muted bg-hover px-12 py-6 rounded">
          第 {{ currentPage }} 页，共 {{ totalPages }} 页
        </span>
        <button class="bg-primary text-sm font-medium px-16 py-8 rounded transition hover-lift shadow-sm"
          :class="{ 'opacity-50 cursor-not-allowed': currentPage >= totalPages }" :disabled="currentPage >= totalPages"
          @click="changePage(currentPage + 1)">
          下一页 →
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
import { formatDate } from '@/utils/uitls'

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

    // 动态更新路由meta信息，用于面包屑导航
    if (categoryData && route.meta) {
      route.meta.categoryName = categoryData.name
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
/* 仅保留必要的自定义样式 */
.category-posts {
  margin: 0 auto;
  padding: 20px;
}
.post-image{
  overflow: hidden;
}
.post-image img {
  transition: transform 0.3s ease;
}

.post-image img:hover {
  transform: scale(1.05);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .category-posts {
    padding: 15px;
  }

  .post-image {
    width: 100%;
    height: auto;
  }

  .post-image img {
    width: 100%;
    height: 100%;
  }

  /* 移动端文章卡片调整为垂直布局 */
  article {
    flex-direction: column !important;
  }

}
</style>