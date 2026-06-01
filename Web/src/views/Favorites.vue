<template>
  <div class="content">
      <main class="main-content">
        <!-- 收藏文章展示 -->
        <div class="posts-section">
          <div class="section-header">
            <h2 class="section-title">我的收藏</h2>
            <p class="section-subtitle">这里是您收藏的所有文章</p>
          </div>

          <!-- 搜索框 -->
          <div class="actions-container">
            <div class="search-box">
                            <Icon name="search" size="16" class="search-icon" />
              <input v-model="searchKeyword" type="text" placeholder="搜索文章..." class="search-input"
                @keyup.enter="searchFavorites" />
            </div>
          </div>

          <!-- 文章列表 -->
          <ArticleList
            :posts="favoritePosts"
            :loading="postsLoading"
            :error="postsError"
            :pagination="postsPagination"
            @post-click="goToPost"
            @page-change="goToPostsPage"
            @retry="loadFavoritePosts"
          >
            <template #empty>
            <div class="empty-text flex flex-col flex-ac text-sm">
              <!-- <div class="empty-icon">💔</div> -->
              <p>{{ searchKeyword ? '没有找到相关的收藏文章' : '您还没有收藏任何文章' }}</p>
              <img src="@/assets/image/扑到.png" alt="" class="fit-err">
              <router-link to="/" class="link-btn">去首页看看</router-link>
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
import Icon from '@/components/Icon.vue'
import ArticleList from '@/components/ArticleList.vue'

const router = useRouter()
const { handleAsync } = useErrorHandler()

// 响应式数据
const favoritePosts = ref<PostListItem[]>([])
const postsLoading = ref(false)
const postsError = ref('')
const searchKeyword = ref('')

// 分页信息
const postsPagination = ref({
  current: 1,
  size: 10,
  total: 0,
  pages: 0
})

// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/post/${postId}?from=favorites`)
}

// 加载收藏文章列表
const loadFavoritePosts = async (page: number = 1) => {
  await handleAsync(async () => {
    postsLoading.value = true
    postsError.value = ''

    const params: PostQueryParams = {
      page,
      size: postsPagination.value.size,
      keyword: searchKeyword.value || undefined
    }

    const response = await PostService.getFavoritePosts(params)

    favoritePosts.value = response.records

    postsPagination.value = {
      current: response.current,
      size: response.size,
      total: response.total,
      pages: response.pages
    }
  }, {
    onError: () => {
      postsError.value = '加载收藏文章失败，请稍后重试'
    },
    onFinally: () => {
      postsLoading.value = false
    }
  })
}

// 搜索收藏文章
const searchFavorites = () => {
  postsPagination.value.current = 1
  loadFavoritePosts(1)
}

// 跳转到指定页面
const goToPostsPage = (page: number) => {
  if (page < 1 || page > postsPagination.value.pages || page === postsPagination.value.current) {
    return
  }

  loadFavoritePosts(page)
}

// 页面挂载时加载数据
onMounted(async () => {
  await loadFavoritePosts()
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.section-header {
  margin-bottom: 24px;
  text-align: center;
}

.section-title {
  font-size: 2rem;
  font-weight: bold;
  color: var(--text-main);
  margin-bottom: 8px;
}

.section-subtitle {
  color: var(--text-subtle);
  font-size: 1rem;
}

.empty-text {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-subtle);
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 16px;
}

.actions-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  gap: 20px;
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

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.retry-btn:hover {
  background: var(--color-primary-dark);
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
.posts-img {
  width: 200px;
  height: 150px;
  background-color: var(--bg-card);
  border-radius: 12px;
  overflow: hidden;
}

.search-box {
    position: relative;
    display: flex;
    align-items: center;
    flex: 1;
    max-width: 400px;
}

.search-icon {
    position: absolute;
    right: 12px;
    top: 50%;
    transform: translateY(-50%);
    color: var(--text-muted);
    pointer-events: none;
}

.search-input {
    flex: 1;
    padding: 8px 36px 8px 12px;
    min-width: 0;
}

// 响应式样式
@include respond(md) {
  .search-box {
    max-width: none;
  }

  .actions-container {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }

  .list article {
    flex-direction: column;
    gap: 12px;
  }

  .posts-img {
    width: 100%;
    height: 200px;
  }

  .relative h3 {
    padding-right: 0 !important;
  }

  .flex.gap-12.text-sm.text-subtle {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>