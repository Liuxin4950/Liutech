<template>
  <div class="category-posts content">
    <!-- 页面头部 -->
    <div class="card bg-soft mb-16">
      <div class="flex flex-col gap-16">
        <div class="flex flex-col gap-12">
          <h1 class="text-xl font-semibold text-primary mb-0 flex flex-ac gap-8">
            <Icon name="folder" size="20" /> {{ category?.name || '分类文章' }}
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
    <ArticleList
      :posts="posts"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      @post-click="goToPost"
      @page-change="changePage"
      @retry="loadPosts"
    >
      <template #empty>
        <div class="mb-8"><Icon name="edit" size="32" /></div>
        <h3 class="font-semibold mb-8">暂无文章</h3>
        <p class="text-muted mb-0">该分类下还没有文章</p>
      </template>
    </ArticleList>
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

@include respond(md) {
  .category-posts { padding: 15px; }
}
</style>