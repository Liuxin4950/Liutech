<template>
  <div class="content">
    <!-- 加载状态 -->
    <div v-if="loading && !tagInfo" class="text-center p-20 text-sm">
      <div class="loading-spinner"></div>
      <p class="loading-text">正在加载标签信息...</p>
    </div>

    <!-- 文章列表部分 -->
    <div v-if="tagInfo" class="mb-20">
      <div class="flex flex-sb flex-ac mb-20 flex-fw gap-16">
        <h2 class="section-title text-2xl font-bold">相关文章</h2>
        <div class="flex flex-ac gap-12">
          <select v-model="sortBy" @change="() => loadPosts()" class="sort-select p-8 rounded text-sm">
            <option value="latest">最新发布</option>
            <option value="popular">最受欢迎</option>
          </select>
        </div>
      </div>

      <!-- 文章列表（空状态由页面级统一展示，隐藏组件默认空态，避免双份空状态叠加） -->
      <ArticleList
        :posts="posts"
        :loading="postsLoading"
        :error="error"
        :pagination="pagination"
        @post-click="goToPost"
        @page-change="changePage"
        @retry="loadPosts"
      >
        <template #empty><span></span></template>
      </ArticleList>
    </div>

    <!-- 空状态 -->
    <div v-if="error || (!postsLoading && posts.length === 0)" class="empty-text flex flex-col flex-ac text-sm">
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
      <h3 class="font-semibold mb-12">{{ error ? '页面未找到' : '暂无相关文章' }}</h3>
      <p class="mb-20">{{ error || '该标签下还没有发布任何文章' }}</p>
      <div class="flex gap-12">
        <button v-if="error" @click="router.back()" class="create-btn">返回上页</button>
        <router-link to="/" class="create-btn" :class="{ outline: error }">返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { TagService, type Tag } from '@/services/tag'
import { PostService, type PostListItem, type PageResponse } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBannerStore } from '@/stores/banner'
import bannerFallback from '@/assets/image/banner/banner0.png'
import ArticleList from '@/components/ArticleList.vue'

// 路由相关
const route = useRoute()
const router = useRouter()
const { showBusinessError } = useErrorHandler()
const bannerStore = useBannerStore()

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

// Banner 页眉：标签名 + 橙色"标签"高亮 + 英文徽标（标签详情页承担页面标题，移除原卡片标题）
watch(tagInfo, () => {
  if (!tagInfo.value) return
  bannerStore.setBanner({
    slides: [{
      title: tagInfo.value.name,
      description: `${tagInfo.value.postCount || 0} 篇文章`,
      imageUrl: bannerFallback,
      sortOrder: 0,
      status: 1
    }],
    badgeText: 'Tag',
    titleAs: 'h1',
    titleHighlight: '标签',
    mode: 'subheader'
  })
})

</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.section-title {
  color: var(--text-main);
  margin: 0;
}

.sort-select {
  background: var(--bg-element);
  color: var(--text-main);
  border: 1px solid var(--border-base);
  outline: none;
  border: 1px solid var(--border-base);
  border-radius: 4px;
  padding: 6px 12px;
}

@include respond(md) {
  .flex.flex-sb.flex-ac.mb-20.flex-fw {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
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
</style>