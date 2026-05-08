<template>
  <div class="category-posts content">
    <!-- 页面头部 -->
    <div v-if="category" class="card bg-soft mb-16">
      <div class="flex flex-col gap-16">
        <div class="flex flex-col gap-12">
          <h1 class="text-xl font-semibold text-primary mb-0 flex flex-ac gap-8">
            <Icon name="folder" size="20" /> {{ category.name }}
          </h1>
          <p v-if="category.description" class="text-subtle text-base mb-0">
            {{ category.description }}
          </p>
          <div class="flex flex-ac gap-8">
            <span class="badge">共 {{ totalPosts }} 篇文章</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 空/错误状态 -->
    <div v-if="categoryError || (!loading && posts.length === 0)" class="empty-text flex flex-col flex-ac text-sm">
      <Icon name="file" size="48" class="empty-icon mb-20" />
      <h3 class="font-semibold mb-12">{{ categoryError ? '页面未找到' : '暂无相关文章' }}</h3>
      <p class="mb-20">{{ categoryError || '该分类下还没有发布任何文章' }}</p>
      <div class="flex gap-12">
        <button v-if="categoryError" @click="router.back()" class="create-btn">返回上页</button>
        <router-link to="/" class="create-btn" :class="{ outline: categoryError }">返回首页</router-link>
      </div>
    </div>

    <!-- 文章列表 -->
    <ArticleList
      v-if="category && (!loading || posts.length > 0)"
      :posts="posts"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      @post-click="goToPost"
      @page-change="changePage"
      @retry="loadPosts"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { PostService, type PostListItem } from '@/services/post'
import type { Category } from '@/services/category'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCategoryStore } from '@/stores/category'
import ArticleList from '@/components/ArticleList.vue'

const router = useRouter()
const route = useRoute()
const { handleAsync } = useErrorHandler()
const categoryStore = useCategoryStore()

// 响应式数据
const posts = ref<PostListItem[]>([])
const category = ref<Category | null>(null)
const loading = ref(false)
const error = ref('')
const categoryError = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const totalPosts = ref(0)

// 计算属性
const totalPages = computed(() => Math.ceil(totalPosts.value / pageSize.value))

// 分页数据
const pagination = computed(() => ({
  current: currentPage.value,
  size: pageSize.value,
  total: totalPosts.value,
  pages: totalPages.value
}))

// 获取分类ID
const categoryId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? parseInt(id[0]) : parseInt(id as string)
})

// 加载分类信息
const loadCategory = async () => {
  await handleAsync(async () => {
    const categoryData = await categoryStore.fetchCategoryById(categoryId.value)
    if (!categoryData) {
      categoryError.value = '分类不存在'
      return
    }
    category.value = categoryData

    // 动态更新路由meta信息：仅保留标题
    if (categoryData && route.meta) {
      route.meta.title = `${categoryData.name} - 分类文章`
    }
  }, {
    onError: () => {
      categoryError.value = '分类不存在'
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
    onError: () => {
      error.value = '加载文章失败，请稍后重试'
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

// 组件挂载时加载数据
onMounted(() => {
  Promise.all([
    loadCategory(),
    loadPosts()
  ])
})
</script>

<style scoped>
.category-posts { padding: 20px; }
.loading-text { text-align: center; padding: 40px 20px; color: var(--text-muted); }

.create-btn.outline {
  background: transparent;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
}

.create-btn.outline:hover {
  background: var(--color-primary);
  color: white;
}

@include respond(md) {
  .category-posts { padding: 15px; }
}
</style>