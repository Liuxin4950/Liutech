<template>
  <div class="content">
    <div class="home-layout">
      <!-- 左侧主内容区 -->
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
      <!-- 右侧边栏 -->
      <aside class="sidebar">
        <!-- 搜索框 -->
        <SearchBox />

        <!-- 个人信息卡片 -->
        <ProfileCard :name="profileInfo.name" :title="profileInfo.title" :avatar="profileInfo.avatar"
          :bio="profileInfo.bio" :stats="profileInfo.stats" />

        <!-- 公告栏 -->
        <AnnouncementCard @view-more="goToAnnouncements" />

        <!-- 分类展示 -->
        <CategoriesCard :categories="categories" :loading="categoriesLoading" />

        <!-- 热门标签 -->
        <HotTags :tags="hotTags" :loading="tagsLoading" @tag-click="goToTag" />

        <!-- 推荐文章：优先用推荐接口，取不到时回退到最新文章 -->
        <RecommendedPosts
          :posts="recommendedPosts.length > 0 ? recommendedPosts : featuredPosts"
          :loading="recommendedLoading && recommendedPosts.length === 0 && allPosts.length === 0"
          @post-click="goToPost"
        />

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
import type { ProfileInfo} from '@/services/user'
import { getAuthorProfile, UserService } from '@/services/user'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import { useScrollReveal } from '@/composables/useScrollReveal'
import ProfileCard from '@/components/ProfileCard.vue'
import AnnouncementCard from '@/components/AnnouncementCard.vue'
import CategoriesCard from '@/components/CategoriesCard.vue'
import HotTags from '@/components/HotTags.vue'
import RecommendedPosts from '@/components/RecommendedPosts.vue'
import ArticleList from '@/components/ArticleList.vue'
import SearchBox from '@/components/SearchBox.vue'

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
  avatar: '/洛天依.png',
  bio: '专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。',
  stats: {
    posts: 0,
    comments: 0,
    views: 0
  }
})

const profileLoading = ref(false)

// 从store获取数据
const categories = computed(() => categoryStore.categories.slice(0, 10))
const categoriesLoading = computed(() => categoryStore.isLoading)
const hotTags = computed(() => tagStore.hotTags)
const tagsLoading = computed(() => tagStore.isHotTagsLoading)

// 精选推荐：取最新文章前3篇（保证首页有内容展示）
const featuredPosts = computed(() => allPosts.value.slice(0, 3))

// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/post/${postId}?from=home`)
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

    const response = await PostService.getPostList(params)
    allPosts.value = response.records

    postsPagination.value = {
      current: response.current,
      size: response.size,
      total: response.total,
      pages: response.pages
    }
  }, {
    onError: () => {
      postsError.value = '加载文章列表失败，请稍后重试'
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

// 加载推荐文章：登录用户走基于浏览历史的个性化推荐，未登录或失败回退热门
const loadRecommendedPosts = async () => {
  await handleAsync(async () => {
    recommendedLoading.value = true
    // 登录用户优先用个性化推荐
    if (UserService.isLoggedIn()) {
      // 接口失败（如 token 过期）静默回退，不阻塞首页
      const personalized = await PostService.getRecommendations(5).catch(() => null)
      if (personalized && personalized.length > 0) {
        recommendedPosts.value = personalized
        return
      }
    }
    // 未登录或个性化失败：回退热门文章
    const response = await PostService.getHotPosts(5)
    recommendedPosts.value = response || []
  }, {
    onError: () => {
      recommendedPosts.value = []
    },
    onFinally: () => {
      recommendedLoading.value = false
    }
  })
}

// 跳转到公告页面：滚动到侧边栏公告卡片区域
const goToAnnouncements = () => {
  const el = document.querySelector('.announcement-card')
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

// 加载作者(开发者)个人资料
const loadProfile = async () => {
  await handleAsync(async () => {

    profileLoading.value = true
    const response = await getAuthorProfile()
    // 停止网络请求
    profileInfo.value = response || {}
  }, {
    onError: () => {
      // 加载个人资料失败时静默处理，保留默认值
    },
    onFinally: () => {
      profileLoading.value = false
    }
  })
}
// 设置首页 SEO Meta 信息
useHead({
  title: 'LiuTech - 个人技术博客',
  meta: [
    { name: 'description', content: 'LiuTech 个人技术博客，分享编程技术、全栈开发、AI 应用和软件工程实践经验。包含 Spring Boot、Vue.js、Java、JavaScript 等技术栈的深度文章。' },
    { name: 'keywords', content: 'LiuTech, 技术博客, 全栈开发, Spring Boot, Vue.js, Java, JavaScript, AI, 编程, 软件开发' },
    { property: 'og:title', content: 'LiuTech - 个人技术博客' },
    { property: 'og:description', content: 'LiuTech 个人技术博客，分享编程技术、全栈开发、AI 应用和软件工程实践经验。' },
    { property: 'og:url', content: 'https://liuxin.chat/' },
    { property: 'og:image', content: 'https://liuxin.chat/og-image.svg' },
    { property: 'twitter:title', content: 'LiuTech - 个人技术博客' },
    { property: 'twitter:description', content: 'LiuTech 个人技术博客，分享编程技术、全栈开发、AI 应用和软件工程实践经验。' },
    { property: 'twitter:image', content: 'https://liuxin.chat/og-image.svg' }
  ],
  link: [
    { rel: 'canonical', href: 'https://liuxin.chat/' }
  ]
})

// 组件挂载时加载数据
onMounted(() => {
  Promise.all([
    loadAllPosts(), // 加载全部文章
    loadCategories(), // 加载分类
    loadHotTags(), // 加载热门标签
    loadRecommendedPosts(), // 加载推荐文章
    loadProfile() // 加载作者个人资料
  ])
})

useScrollReveal('.reveal')
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.btn-disabled {
  opacity: 0.5;
}

.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px; /* minmax(0,1fr) 允许主列收缩，防止内容 min-content 撑破布局 */
  gap: $gap-lg;
  align-items: start;

  @include respond(lg) {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: $gap-md;
  }

  @include respond(md) {
    gap: $gap-md;
  }
}

/* 文章列表样式 */
.posts-section {
  width: 100%;

  // 小屏时侧栏排在文章列表之后。预留至少一屏高度，避免请求返回前侧栏先进入
  // 首屏、文章卡片插入后又被整体推走，形成明显布局偏移。
  @include respond(lg) {
    min-height: 100vh;
  }

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

/* 右侧主内容区 */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: $gap-lg;
  position: sticky;
  top: var(--header-height);

  @include respond(lg) {
    position: static;
    order: 2;
    width: 100%;
    gap: $gap-md;
  }
}

/* 左侧主内容区 */
.main-content {
  width: 100%;
  display: flex;
  flex-direction: column;
}
</style>
