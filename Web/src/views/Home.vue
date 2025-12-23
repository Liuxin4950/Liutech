<template>
  <div class="content">
    <div class="home-layout">
      <!-- 左侧边栏 -->
      <main class="main-content">
        <!-- 全部文章展示 -->
        <div class="posts-section">
          <ArticleList
            :posts="allPosts"
            :loading="postsLoading"
            :error="postsError"
            :pagination="postsPagination"
            @post-click="goToPost"
            @page-change="goToPostsPage"
            @retry="loadAllPosts"
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
import { useHead } from '@vueuse/head'
import { useRouter } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostListItem, PostQueryParams } from '@/services/post'
import { formatDate } from '@/utils/uitls'
import type { ProfileInfo} from '@/services/user'
import { getProfile,getAuthorProfile } from '@/services/user'
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
import ArticleList from '@/components/ArticleList.vue'

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

// 作者的个人资料数据
const profileInfo = ref<ProfileInfo>({
  name: 'LiuTech',
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
const categories = computed(() => categoryStore.categories.slice(0, 10))
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

// 加载作者(开发者)个人资料
const loadProfile = async () => {
  await handleAsync(async () => {

    profileLoading.value = true
    const response = await getAuthorProfile()
    console.log('作者数据', response);
    
    profileInfo.value = response || {}
  }, {
    onError: (err) => {
      console.error('加载个人资料失败:', err)
    },
    onFinally: () => {
      profileLoading.value = false
    }
  })
}
// 组件挂载时加载数据
onMounted(() => {
  // 设置首页 SEO Meta 信息
  useHead({
    title: 'LiuTech - 个人技术博客',
    meta: [
      { name: 'description', content: 'LiuTech 个人技术博客，分享编程技术、全栈开发、AI 应用和软件工程实践经验。包含 Spring Boot、Vue.js、Java、JavaScript 等技术栈的深度文章。' },
      { name: 'keywords', content: 'LiuTech, 技术博客, 全栈开发, Spring Boot, Vue.js, Java, JavaScript, AI, 编程, 软件开发' },
      { property: 'og:title', content: 'LiuTech - 个人技术博客' },
      { property: 'og:description', content: 'LiuTech 个人技术博客，分享编程技术、全栈开发、AI 应用和软件工程实践经验。' },
      { property: 'og:url', content: 'https://liutech.chat/' },
      { property: 'og:image', content: 'https://liutech.chat/og-image.jpg' },
      { property: 'twitter:title', content: 'LiuTech - 个人技术博客' },
      { property: 'twitter:description', content: 'LiuTech 个人技术博客，分享编程技术、全栈开发、AI 应用和软件工程实践经验。' },
      { property: 'twitter:image', content: 'https://liutech.chat/og-image.jpg' }
    ],
    link: [
      { rel: 'canonical', href: 'https://liutech.chat/' }
    ]
  })
  Promise.all([
    loadAllPosts(), // 加载全部文章
    loadCategories(), // 加载分类
    loadHotTags(), // 加载热门标签
    loadRecommendedPosts(), // 加载推荐文章
    loadProfile() // 加载作者个人资料
  ])
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.btn-disabled {
  opacity: 0.5;
}

.relative {
  > .badge {
    position: absolute;
    top: 0;
    right: 0;
    opacity: 0;
    transition: 0.5s;
  }
  &:hover .badge {
    opacity: 1;
  }
}

.posts-img {
  width: 200px;
  height: 150px;
  background-color: white;
  border-radius: $card-radius;
  overflow: hidden;

  .fit {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  @include respond(lg) {
    width: 180px;
    height: 135px;
  }

  @include respond(md) {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
  }
}

.banner {
  height: 500px;

  @include respond(md) {
    height: 320px;
  }

  @include respond(sm) {
    height: 220px;
  }
}

.home-layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: $gap-lg;
  align-items: start;

  @include respond(lg) {
    display: flex;
    flex-direction: column;
    gap: $gap-md;
  }

  @include respond(md) {
    gap: $gap-md;
  }
}

/* 文章列表样式 */
.posts-section {
  width: 100%;
  .list {
    width: 100%;
    article {
      @include respond(md) {
        flex-direction: column;
      }
      @include respond(sm) {
        padding: 10px;
      }
    }
  }
}
.post-summary {
  display: -webkit-box;
  -webkit-line-clamp: 2; /* 限制显示2行 */
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  max-height: 3em; /* 2行的高度 (1.5 * 2) */
  word-break: break-word;
}


.loading-text,
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

  &:hover {
    background: var(--primary-hover);
    transform: translateY(-1px);
  }
}

/* 右侧主内容区 */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: $gap-lg;
  position: sticky;
  top: 70px;

  @include respond(md) {
    position: static;
    order: 2;
    gap: $gap-md;
  }
}

/* 左侧主内容区 */
.main-content {
  width: 100%;
  display: flex;
  flex-direction: column;
}

/* 欢迎横幅 */
.welcome-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: $card-radius;
  padding: 40px;
  text-align: center;
  color: white;

  h1 {
    font-size: 2.2rem;
    margin: 0 0 12px 0;
    font-weight: 700;

    @include respond(md) {
      font-size: 1.8rem;
    }
  }

  p {
    font-size: 1.1rem;
    margin: 0;
    opacity: 0.9;
  }

  @include respond(md) {
    padding: 24px 20px;
  }
}

.paging-tab {
  button.text-muted {
    color: white;
  }
}

/* 额外移动端细节优化 */
@include respond(sm) {
  .loading-text,
  .empty-text {
    padding: 24px 12px;
  }
}

</style>
