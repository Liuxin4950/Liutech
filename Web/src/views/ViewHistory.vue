<template>
  <div class="content">
      <main class="main-content">
        <!-- 浏览历史展示 -->
        <div class="posts-section">
          <!-- 操作栏 -->
          <div class="actions-container">
            <button class="clear-btn" @click="clearHistory">
              <Icon name="trash" size="16" />
              清空历史
            </button>
          </div>

          <!-- 文章列表 -->
          <ArticleList
            :posts="historyPosts"
            :loading="postsLoading"
            :error="postsError"
            :pagination="postsPagination"
            show-viewed-at
            @post-click="goToPost"
            @page-change="goToPostsPage"
            @retry="loadHistory"
          >
            <template #empty>
            <div class="empty-text flex flex-col flex-ac text-sm">
              <p>您还没有浏览过任何文章</p>
              <router-link to="/" class="link-btn">去首页逛逛</router-link>
            </div>
          </template>
          </ArticleList>
        </div>
      </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PostService from '@/services/post'
import type { PostListItem, PostQueryParams } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBannerStore } from '@/stores/banner'
import bannerFallback from '@/assets/image/banner/banner0.png'
import Icon from '@/components/Icon.vue'
import ArticleList from '@/components/ArticleList.vue'

const router = useRouter()
const { handleAsync, showToastSuccess, showToastError, confirm } = useErrorHandler()
const bannerStore = useBannerStore()

// 页面标题由 banner 承载（hero 大横幅）
bannerStore.setBanner({
  slides: [{
    title: '浏览',
    description: '这里是您最近浏览过的文章',
    imageUrl: bannerFallback,
    sortOrder: 0,
    status: 1
  }],
  badgeText: 'View History',
  titleAs: 'h1',
  titleHighlight: '历史',
  mode: 'hero'
})

// 响应式数据
const historyPosts = ref<PostListItem[]>([])
const postsLoading = ref(false)
const postsError = ref('')

// 分页信息
const postsPagination = ref({
  current: 1,
  size: 10,
  total: 0,
  pages: 0
})

// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/post/${postId}?from=view-history`)
}

// 加载浏览历史列表
const loadHistory = async (page: number = 1) => {
  await handleAsync(async () => {
    postsLoading.value = true
    postsError.value = ''

    const params: PostQueryParams = {
      page,
      size: postsPagination.value.size
    }

    const response = await PostService.getViewHistory(params)

    historyPosts.value = response.records

    postsPagination.value = {
      current: response.current,
      size: response.size,
      total: response.total,
      pages: response.pages
    }
  }, {
    onError: () => {
      postsError.value = '加载浏览历史失败，请稍后重试'
    },
    onFinally: () => {
      postsLoading.value = false
    }
  })
}

// 跳转到指定页面
const goToPostsPage = (page: number) => {
  if (page < 1 || page > postsPagination.value.pages || page === postsPagination.value.current) {
    return
  }

  loadHistory(page)
}

// 清空浏览历史（不可恢复）
const clearHistory = async () => {
  const confirmed = await confirm('确定要清空全部浏览历史吗？此操作不可恢复。')
  if (!confirmed) {
    return
  }

  await handleAsync(async () => {
    await PostService.clearViewHistory()
    historyPosts.value = []
    postsPagination.value = {
      current: 1,
      size: postsPagination.value.size,
      total: 0,
      pages: 0
    }
    showToastSuccess('浏览历史已清空')
  }, {
    onError: () => {
      showToastError('清空失败，请稍后重试')
    }
  })
}

// 页面挂载时加载数据
onMounted(async () => {
  await loadHistory()
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.actions-container {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-bottom: 30px;
  gap: 20px;
}

/* 清空历史按钮 */
.clear-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--bg-error, #ffebee);
  color: var(--color-error, #d32f2f);
  border: none;
  border-radius: 25px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;
}

.clear-btn:hover {
  background: var(--color-error, #d32f2f);
  color: #fff;
  transform: translateY(-2px);
}

.link-btn {
  display: inline-block;
  margin-top: 16px;
  padding: 12px 24px;
  background: var(--color-primary);
  color: white;
  text-decoration: none;
  border-radius: 8px;
  transition: background 0.2s;
}

.link-btn:hover {
  background: var(--color-primary-dark);
}

// 响应式样式
@include respond(md) {
  .actions-container {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
}
</style>
