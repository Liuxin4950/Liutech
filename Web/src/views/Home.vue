<template>
  <div class="content">
    <div class="home-layout">
      <!-- 左侧边栏 -->
      <aside class="sidebar">
        <!-- 个人信息卡片 -->
        <ProfileCard 
          :name="'刘鑫'"
          :title="'全栈工程师'"
          :avatar="'/default-avatar.svg'"
          :bio="'专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。'"
          :stats="{
            posts: hotPosts.length,
            comments: totalComments,
            views: totalViews
          }"
        />

        <!-- 公告栏 -->
        <AnnouncementCard :announcements="announcements" />

        <!-- 分类展示 -->
        <CategoriesCard 
          :categories="categories"
          :loading="categoriesLoading"
          @category-click="goToCategory"
        />

        <!-- 热门标签 -->
        <HotTags 
          :tags="hotTags"
          :loading="tagsLoading"
          @tag-click="goToTag"
        />

        <!-- 推荐文章 -->
        <RecommendedPosts 
          :posts="recommendedPosts"
          :loading="recommendedLoading"
          @post-click="goToPost"
        />

        <!-- 友情链接 -->
        <FriendLinks :links="friendLinks" />
      </aside>

      <!-- 右侧主内容区 -->
      <main class="main-content">
        <!-- 热门文章组件 -->
        <HotPosts 
          :posts="hotPosts"
          :loading="loading"
          :error="error"
          @post-click="goToPost"
          @create-post="router.push('/create')"
          @view-all="router.push('/posts')"
          @retry="loadHotPosts"
        />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostListItem } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import ProfileCard from '@/components/ProfileCard.vue'
import AnnouncementCard from '@/components/AnnouncementCard.vue'
import CategoriesCard from '@/components/CategoriesCard.vue'
import HotTags from '@/components/HotTags.vue'
import RecommendedPosts from '@/components/RecommendedPosts.vue'
import FriendLinks from '@/components/FriendLinks.vue'
import HotPosts from '@/components/HotPosts.vue'

const router = useRouter()
const { handleAsync } = useErrorHandler()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()

// 响应式数据
const hotPosts = ref<PostListItem[]>([])
const loading = ref(false)
const error = ref('')
const totalComments = ref(67)
const totalViews = ref(152)
const recommendedPosts = ref<PostListItem[]>([])
const recommendedLoading = ref(false)

// 公告数据
const announcements = ref([
  {
    id: 1,
    date: '2025-01-27',
    text: '博客系统正式上线，欢迎大家访问！'
  },
  {
    id: 2,
    date: '2025-01-26',
    text: '新增夜间模式功能，提升阅读体验。'
  },
  {
    id: 3,
    date: '2025-01-25',
    text: '评论系统优化完成，支持实时回复。'
  }
])

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

// 加载热门文章
const loadHotPosts = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''

    const posts = await PostService.getHotPosts(10) // 获取10篇热门文章
    hotPosts.value = posts
  }, {
    onError: (err) => {
      error.value = '加载热门文章失败，请稍后重试'
      console.error('加载热门文章失败:', err)
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/post/${postId}`)
}

// 跳转到分类页面
const goToCategory = (categoryId: number) => {
  router.push(`/category/${categoryId}`)
}

// 跳转到标签页面
const goToTag = (tagId: number) => {
  router.push(`/tag/${tagId}`)
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

// 格式化日期
const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

// 组件挂载时加载数据
onMounted(() => {
  Promise.all([
    loadHotPosts(),
    loadCategories(),
    loadHotTags(),
    loadRecommendedPosts()
  ])
})
</script>

<style scoped>
.banner{
  height: 500px;
}
.content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.home-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 30px;
  align-items: start;
}

/* 左侧边栏样式 */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: sticky;
  top: 20px;
}

/* 右侧主内容区 */
.main-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
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



/* 响应式设计 */
@media (max-width: 1024px) {
  .home-layout {
    grid-template-columns: 280px 1fr;
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
