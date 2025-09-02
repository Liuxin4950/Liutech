<template>
  <div class="content">
    <div class="home-layout">
      <!-- 左侧边栏 -->
      <main class="main-content">
        <!-- 全部文章展示 -->
        <div class="posts-section">
          <!-- 文章列表 -->
          <div>
            <div v-if="postsLoading" class="loading-text">加载中...</div>

            <div v-else-if="postsError" class="loading-text text-primary">
              <p>{{ postsError }}</p>
              <button @click="loadAllPosts()" class="retry-btn">重试</button>
            </div>

            <div v-else-if="allPosts.length === 0" class="empty-text">暂无文章</div>

            <div v-else class="list gap-16">
              <article v-for="post in allPosts" :key="post.id"
                class="flex gap-16 p-16 rounded-lg transition link card bg-card" @click="goToPost(post.id)">
                <!-- 缩略图 -->
                <div class="posts-img">
                  <img :src="post.thumbnail || post.coverImage || '/src/assets/image/images.jpg'" :alt="post.title"
                    class="fit">
                </div>

                <div class="flex flex-col flex-sb flex-1 relative">
                  <span v-if="post.category" class="badge" @click.stop="goToCategory(post.category.id)">{{ post.category.name }}</span>
                  <div class="flex-1 flex flex-col gap-12">
                    <h3 class="font-semibold text-primary text-xl">{{ post.title }}</h3>
   
                    <p v-if="post.summary" class="text-subtle text-base text-sm">{{ post.summary }}</p>
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
            v-if="!postsLoading && allPosts.length > 0"
            :current-page="postsPagination.current"
            :total-pages="postsPagination.pages"
            @page-change="goToPostsPage"
          />
        </div>
      </main>
      <!-- 右侧主内容区 -->
      <aside class="sidebar">
        <!-- 搜索框 -->
        <!-- <SearchBox /> -->

        <!-- 个人信息卡片 -->
        <ProfileCard :name="profileInfo.name" :title="profileInfo.title" :avatar="profileInfo.avatar"
          :bio="profileInfo.bio" :stats="profileInfo.stats" />

        <!-- 公告栏 -->
        <AnnouncementCard @view-more="goToAnnouncements" />

        <!-- 分类展示 -->
        <CategoriesCard :categories="categories" :loading="categoriesLoading" />

        <!-- 热门标签 -->
        <HotTags :tags="hotTags" :loading="tagsLoading" @tag-click="goToTag" />

        <!-- 推荐文章 -->
        <RecommendedPosts :posts="recommendedPosts" :loading="recommendedLoading" @post-click="goToPost" />

        <!-- 友情链接 -->
        <FriendLinks :links="friendLinks" />
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostListItem, PostQueryParams } from '@/services/post'
import { formatDate } from '@/utils/uitls'

import { UserService } from '@/services/user'
import type { ProfileInfo } from '@/services/user'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import ProfileCard from '@/components/ProfileCard.vue'
import AnnouncementCard from '@/components/AnnouncementCard.vue'
import CategoriesCard from '@/components/CategoriesCard.vue'
import HotTags from '@/components/HotTags.vue'
import RecommendedPosts from '@/components/RecommendedPosts.vue'
import FriendLinks from '@/components/FriendLinks.vue'
import Pagination from '@/components/Pagination.vue'

const router = useRouter()
const { handleAsync } = useErrorHandler()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()

// 响应式数据
const recommendedPosts = ref<PostListItem[]>([])
const recommendedLoading = ref(false)

// 文章列表数据
const allPosts = ref<PostListItem[]>([])
const postsLoading = ref(false)
const postsError = ref('')

// 分页信息
const postsPagination = ref({
  current: 1,
  size: 10,
  total: 0,
  pages: 0
})

// 个人资料数据
const profileInfo = ref<ProfileInfo>({
  name: '刘鑫',
  title: '全栈工程师',
  avatar: '/default-avatar.svg',
  bio: '专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。',
  stats: {
    posts: 0,
    comments: 0,
    views: 0
  }
})
const profileLoading = ref(false)
// 公告相关数据已移至AnnouncementCard组件内部处理

// 友情链接数据
const friendLinks = ref([
  {
    id: 1,
    url: 'https://github.com',
    icon: '🐙',
    text: 'GitHub'
  },
  {
    id: 2,
    url: 'https://vue.js.org',
    icon: '💚',
    text: 'Vue.js'
  },
  {
    id: 3,
    url: 'https://spring.io',
    icon: '🍃',
    text: 'Spring'
  }
])

// 从store获取数据
const categories = computed(() => categoryStore.categories)
const categoriesLoading = computed(() => categoryStore.isLoading)
const hotTags = computed(() => tagStore.hotTags)
const tagsLoading = computed(() => tagStore.isHotTagsLoading)

// 注意：visiblePostsPages 计算属性已移除，现在使用 Pagination 组件内部处理


// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/post/${postId}?from=home`)
}
// 跳转到分类详情
const goToCategory = (categoryId: number) => {
    router.push(`/category-detail/${categoryId}`)
}

// 跳转到标签页面
const goToTag = (tagId: number) => {
  router.push(`/tags/${tagId}`)
}

// 加载全部文章列表
const loadAllPosts = async (page: number = 1) => {
  await handleAsync(async () => {
    postsLoading.value = true
    postsError.value = ''

    const params: PostQueryParams = {
      page,
      size: postsPagination.value.size,
      sortBy: 'latest' // 按最新排序
    }

    const response = await PostService.getPosts(params)
    console.log('response', response);
    
    allPosts.value = response.records
    
    postsPagination.value = {
      current: response.current,
      size: response.size,
      total: response.total,
      pages: response.pages
    }
  }, {
    onError: (err) => {
      postsError.value = '加载文章列表失败，请稍后重试'
      console.error('加载文章列表失败:', err)
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

  loadAllPosts(page)
}



// 加载分类
const loadCategories = async () => {
  await categoryStore.fetchCategories()
}

// 加载热门标签
const loadHotTags = async () => {
  await tagStore.fetchHotTags(10)
}

// 加载推荐文章
const loadRecommendedPosts = async () => {
  await handleAsync(async () => {
    recommendedLoading.value = true
    const response = await PostService.getLatestPosts(5)
    recommendedPosts.value = response || []
  }, {
    onError: (err) => {
      console.error('加载推荐文章失败:', err)
      recommendedPosts.value = []
    },
    onFinally: () => {
      recommendedLoading.value = false
    }
  })
}

// 跳转到公告页面
const goToAnnouncements = () => {
  // TODO: 实现公告列表页面路由跳转
  console.log('跳转到公告列表页面')
}

// 加载个人资料
const loadProfile = async () => {
  await handleAsync(async () => {
    profileLoading.value = true
    const response = await UserService.getProfile()
    profileInfo.value = response
  }, {
    onError: (err) => {
      console.error('加载个人资料失败:', err)
      // 保持默认值
    },
    onFinally: () => {
      profileLoading.value = false
    }
  })
}
// 组件挂载时加载数据
onMounted(() => {
  Promise.all([
    loadAllPosts(), // 加载全部文章
    loadCategories(),
    loadHotTags(),
    loadRecommendedPosts(),
    loadProfile()
  ])
})
</script>

<style scoped>
.btn-disabled{
  opacity: 50%;
}


.relative > .badge{
  position: absolute;
  top: 0;
  right: 0;
}

.posts-img {
  width: 200px;
  height: 150px;
  background-color: white;
  border-radius: 12px;
  overflow: hidden;
}

.banner {
  height: 500px;
}

.home-layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
  align-items: start;
}

/* 文章列表样式 */
.posts-section {
  width: 100%;
}

.loading-text {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
}

.empty-text {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
}

.retry-btn {
  background: var(--primary-color);
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  margin-top: 8px;
  transition: all 0.2s ease;
}

.retry-btn:hover {
  background: var(--primary-hover);
  transform: translateY(-1px);
}

/* 左侧边栏样式 */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
  /* 当距离顶部70px时固定 */
  position: sticky;
  top: 70px;
}

/* 右侧主内容区 */
.main-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 欢迎横幅 */
.welcome-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 40px;
  text-align: center;
  color: white;
}

.welcome-banner h1 {
  font-size: 2.2rem;
  margin: 0 0 12px 0;
  font-weight: 700;
}

.welcome-banner p {
  font-size: 1.1rem;
  margin: 0;
  opacity: 0.9;
}
.paging-tab button.text-muted{
  color: white;
}


/* 响应式设计 */
@media (max-width: 1024px) {
  .home-layout {
    grid-template-columns: 1fr 260px;
    gap: 20px;
  }

  .sidebar {
    gap: 16px;
  }
}

@media (max-width: 768px) {
  .home {
    padding: 16px;
  }

  .home-layout {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .sidebar {
    position: static;
    order: 2;
  }

  .main-content {
    order: 1;
  }

  .welcome-banner {
    padding: 24px 20px;
  }

  .welcome-banner h1 {
    font-size: 1.8rem;
  }


}

@media (max-width: 480px) {
  .home {
    padding: 12px;
  }
}
</style>
