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
              <input v-model="searchKeyword" type="text" placeholder="搜索文章..." class="search-input"
                @keyup.enter="searchFavorites" />
              <span class="search-icon">🔍</span>
            </div>
          </div>

          <!-- 文章列表 -->
          <div>
            <div v-if="postsLoading" class="loading-text">加载中...</div>

            <div v-else-if="postsError" class="loading-text text-primary">
              <p>{{ postsError }}</p>
              <button @click="loadFavoritePosts()" class="retry-btn">重试</button>
            </div>

            <div v-else-if="favoritePosts.length === 0" class="empty-text">
              <div class="empty-icon">💔</div>
              <p>{{ searchKeyword ? '没有找到相关的收藏文章' : '您还没有收藏任何文章' }}</p>
              <router-link to="/" class="link-btn">去首页看看</router-link>
            </div>

            <div v-else class="list gap-16">
              <article v-for="post in favoritePosts" :key="post.id"
                class="flex gap-16 p-16 rounded-lg transition link card bg-card" @click="goToPost(post.id)">
                <!-- 缩略图 -->
                <div class="posts-img">
                  <img :src="post.coverImage || post.thumbnail || '/src/assets/image/images.jpg'" :alt="post.title"
                    class="fit">
                </div>

                <div class="flex flex-col flex-sb flex-1 relative">
                  <span v-if="post.category" class="badge" @click.stop="goToCategory(post.category.id)">{{ post.category.name }}</span>
                  <div class="flex-1 flex flex-col gap-12">
                    <h3 class="font-semibold text-primary text-xl" style="padding-right: 70px">{{ post.title }}</h3>

                    <p v-if="post.summary" class="text-subtle text-base text-sm post-summary">{{ post.summary }}</p>
                    <div class="tags-cloud" v-if="post.tags && post.tags.length > 0">
                      <span @click.stop="goToTag(tag.id)" v-for="tag in post.tags" :key="tag.id" class="tag">
                        {{ tag.name }}
                      </span>
                    </div>
                  </div>

                  <div class="flex flex-sb flex-ac mt-8">
                    <div class="flex flex-ac gap-8 text-subtle">
                      <img v-if="post.author?.avatarUrl" :src="post.author.avatarUrl" :alt="post.author.username"
                        class="rounded" style="width: 24px; height: 24px; object-fit: cover;">
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
          </div>

          <!-- 分页器 -->
          <Pagination
            v-if="!postsLoading && favoritePosts.length > 0"
            :current-page="postsPagination.current"
            :total-pages="postsPagination.pages"
            @page-change="goToPostsPage"
          />
        </div>
      </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PostService from '@/services/post'
import type { PostListItem, PostQueryParams } from '@/services/post'
import { formatDate } from '@/utils/uitls'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import Pagination from '@/components/Pagination.vue'

const router = useRouter()
const { handleAsync } = useErrorHandler()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()

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

// 跳转到分类详情
const goToCategory = (categoryId: number) => {
  router.push(`/category-detail/${categoryId}`)
}

// 跳转到标签页面
const goToTag = (tagId: number) => {
  router.push(`/tags/${tagId}`)
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
    console.log('收藏文章响应:', response)

    favoritePosts.value = response.records

    postsPagination.value = {
      current: response.current,
      size: response.size,
      total: response.total,
      pages: response.pages
    }
  }, {
    onError: (err) => {
      postsError.value = '加载收藏文章失败，请稍后重试'
      console.error('加载收藏文章失败:', err)
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

// 清除搜索
const clearSearch = () => {
  searchKeyword.value = ''
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

// 加载分类
const loadCategories = async () => {
  await categoryStore.fetchCategories()
}

// 加载热门标签
const loadHotTags = async () => {
  await tagStore.fetchHotTags(10)
}

// 页面挂载时加载数据
onMounted(async () => {
  await Promise.all([
    loadFavoritePosts(),
    loadCategories(),
    loadHotTags()
  ])
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
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.section-subtitle {
  color: var(--color-text-subtle);
  font-size: 1rem;
}

.empty-text {
  text-align: center;
  padding: 60px 20px;
  color: var(--color-text-subtle);
}

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

.search-box {
  position: relative;
  flex: 1;
  max-width: 400px;
}

.search-input {
  width: 100%;
  padding: 12px 40px 12px 16px;
  border: 2px solid var(--border-soft);
  border-radius: 25px;
  font-size: 14px;
  background-color: var(--bg-soft);
  color: var(--text-main);
  transition: border-color 0.3s;
}

.search-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.search-icon {
  position: absolute;
  right: 15px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-main);
  opacity: 0.6;
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
  background: var(--color-primary-hover);
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
  background: var(--color-primary-hover);
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
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
}

// 响应式样式
@include respond(md) {
  .actions-container {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }

  .search-box {
    max-width: none;
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