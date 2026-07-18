<template>
  <div class="series-posts content">
    <!-- 系列头部 -->
    <div v-if="series" class="card bg-soft mb-16">
      <div v-if="series.coverImage" class="series-hero">
        <img :src="series.coverImage" :alt="series.name" />
      </div>
      <div class="flex flex-col gap-12">
        <h1 class="text-xl font-semibold text-primary mb-0 flex flex-ac gap-8">
          <Icon name="book" size="20" /> {{ series.name }}
        </h1>
        <p v-if="series.description" class="text-subtle text-base mb-0">{{ series.description }}</p>
        <div class="flex flex-ac gap-8">
          <span class="badge">共 {{ totalPosts }} 篇文章</span>
        </div>
      </div>
    </div>

    <!-- 空/错误状态 -->
    <div v-if="seriesError || (!loading && posts.length === 0)" class="empty-text flex flex-col flex-ac text-sm">
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
      <h3 class="font-semibold mb-12">{{ seriesError ? '页面未找到' : '暂无相关文章' }}</h3>
      <p class="mb-20">{{ seriesError || '该系列下还没有发布任何文章' }}</p>
      <div class="flex gap-12">
        <button v-if="seriesError" @click="router.back()" class="create-btn">返回上页</button>
        <router-link to="/series" class="create-btn" :class="{ outline: seriesError }">返回系列列表</router-link>
      </div>
    </div>

    <!-- 文章列表 -->
    <ArticleList
      v-if="series && (!loading || posts.length > 0)"
      :posts="posts"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      @post-click="goToPost"
      @page-change="changePage"
      @retry="loadPosts"
    >
      <template #empty><span></span></template>
    </ArticleList>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { PostService, type PostListItem } from '@/services/post'
import { SeriesService, type PostSeries } from '@/services/series'
import { useErrorHandler } from '@/composables/useErrorHandler'
import Icon from '@/components/Icon.vue'
import ArticleList from '@/components/ArticleList.vue'

const router = useRouter()
const route = useRoute()
const { handleAsync } = useErrorHandler()

const posts = ref<PostListItem[]>([])
const series = ref<PostSeries | null>(null)
const loading = ref(false)
const error = ref('')
const seriesError = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const totalPosts = ref(0)

const totalPages = computed(() => Math.ceil(totalPosts.value / pageSize.value))

const pagination = computed(() => ({
  current: currentPage.value,
  size: pageSize.value,
  total: totalPosts.value,
  pages: totalPages.value
}))

const seriesId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? parseInt(id[0]) : parseInt(id as string)
})

const loadSeries = async () => {
  await handleAsync(async () => {
    const data = await SeriesService.getSeriesById(seriesId.value)
    if (!data || !data.id) {
      seriesError.value = '系列不存在'
      return
    }
    series.value = data
    if (route.meta) route.meta.title = `${data.name} - 系列`
  }, {
    onError: () => { seriesError.value = '系列不存在' }
  })
}

const loadPosts = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''
    const response = await PostService.getPostList({
      page: currentPage.value,
      size: pageSize.value,
      seriesId: seriesId.value
    })
    posts.value = response.records
    totalPosts.value = response.total
  }, {
    onError: () => { error.value = '加载文章失败，请稍后重试' },
    onFinally: () => { loading.value = false }
  })
}

const changePage = (page: number) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    loadPosts()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

const goToPost = (postId: number) => {
  router.push(`/post/${postId}?from=series&seriesId=${seriesId.value}`)
}

onMounted(() => {
  Promise.all([loadSeries(), loadPosts()])
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.series-posts { padding: 20px; }
.loading-text { text-align: center; padding: 40px 20px; color: var(--text-muted); }

.series-hero {
  height: 200px;
  overflow: hidden;
  border-radius: 8px;
  margin-bottom: 16px;
  img { width: 100%; height: 100%; object-fit: cover; }
}

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
  .series-posts { padding: 15px; }
}
</style>
