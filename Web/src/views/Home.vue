<template>
  <div class="home">

    <div class="home-layout">
      <!-- 左侧边栏 -->
      <aside class="sidebar">
        <!-- 个人信息卡片 -->
        <div class="profile-card">
          <div class="profile-header">
            <img src="/default-avatar.svg" alt="头像" class="profile-avatar">
            <div class="profile-info">
              <h3 class="profile-name">刘鑫</h3>
              <p class="profile-title">全栈工程师</p>
            </div>
          </div>
          <div class="profile-stats">
            <div class="stat-item">
              <span class="stat-number">{{ hotPosts.length }}</span>
              <span class="stat-label">文章</span>
            </div>
            <div class="stat-item">
              <span class="stat-number">{{ totalComments }}</span>
              <span class="stat-label">评论</span>
            </div>
            <div class="stat-item">
              <span class="stat-number">{{ totalViews }}</span>
              <span class="stat-label">访问</span>
            </div>
          </div>
          <p class="profile-bio">
            专注于前端开发、后端架构和技术分享。
            热爱编程，喜欢探索新技术。
          </p>
        </div>

        <!-- 公告栏 -->
        <div class="announcement-card">
          <h4 class="card-title">📢 公告</h4>
          <div class="announcement-list">
            <div class="announcement-item">
              <span class="announcement-date">2025-01-27</span>
              <p class="announcement-text">博客系统正式上线，欢迎大家访问！</p>
            </div>
            <div class="announcement-item">
              <span class="announcement-date">2025-01-26</span>
              <p class="announcement-text">新增夜间模式功能，提升阅读体验。</p>
            </div>
            <div class="announcement-item">
              <span class="announcement-date">2025-01-25</span>
              <p class="announcement-text">评论系统优化完成，支持实时回复。</p>
            </div>
          </div>
        </div>

        <!-- 分类展示 -->
        <div class="categories-card">
          <h4 class="card-title">📂 文章分类</h4>
          <div v-if="categoriesLoading" class="loading-text">加载中...</div>
          <div v-else-if="categories.length === 0" class="empty-text">暂无分类</div>
          <div v-else class="categories-list">
            <div 
              v-for="category in categories" 
              :key="category.id" 
              class="category-item"
              @click="goToCategory(category.id)"
            >
              <span class="category-name">{{ category.name }}</span>
              <span class="category-count">{{ category.postCount || 0 }}</span>
            </div>
          </div>
        </div>

        <!-- 热门标签 -->
        <div class="tags-card">
          <h4 class="card-title">🏷️ 热门标签</h4>
          <div v-if="tagsLoading" class="loading-text">加载中...</div>
          <div v-else-if="hotTags.length === 0" class="empty-text">暂无标签</div>
          <div v-else class="tags-cloud">
            <span 
              v-for="tag in hotTags" 
              :key="tag.id" 
              class="tag-item"
              @click="goToTag(tag.id)"
            >
              {{ tag.name }}
              <small v-if="tag.postCount">({{ tag.postCount }})</small>
            </span>
          </div>
        </div>

        <!-- 推荐文章 -->
        <div class="recommended-card">
          <h4 class="card-title">📖 推荐阅读</h4>
          <div v-if="recommendedLoading" class="loading-text">加载中...</div>
          <div v-else-if="recommendedPosts.length === 0" class="empty-text">暂无推荐</div>
          <div v-else class="recommended-list">
            <div 
              v-for="post in recommendedPosts" 
              :key="post.id" 
              class="recommended-item"
              @click="goToPost(post.id)"
            >
              <h5 class="recommended-title">{{ post.title }}</h5>
              <div class="recommended-meta">
                <span class="recommended-author">{{ post.author?.username }}</span>
                <span class="recommended-date">{{ formatDate(post.createdAt) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 友情链接 -->
        <div class="links-card">
          <h4 class="card-title">🔗 友情链接</h4>
          <div class="links-list">
            <a href="https://github.com" target="_blank" class="link-item">
              <span class="link-icon">🐙</span>
              <span class="link-text">GitHub</span>
            </a>
            <a href="https://vue.js.org" target="_blank" class="link-item">
              <span class="link-icon">💚</span>
              <span class="link-text">Vue.js</span>
            </a>
            <a href="https://spring.io" target="_blank" class="link-item">
              <span class="link-icon">🍃</span>
              <span class="link-text">Spring</span>
            </a>
          </div>
        </div>
      </aside>

      <!-- 右侧主内容区 -->
      <main class="main-content">
        <!-- 文章列表 -->
        <div class="posts-section">
          <div class="section-header">
            <h2>🔥 热门文章</h2>
            <div class="header-actions">
              <button class="view-all-btn" @click="router.push('/create')">发布文章</button>
              <button class="view-all-btn" @click="router.push('/posts')">查看全部</button>
            </div>
          </div>

          <div v-if="loading" class="loading">
            <p>加载中...</p>
          </div>
          <div v-else-if="error" class="error">
            <p>{{ error }}</p>
            <button @click="loadHotPosts" class="retry-btn">重试</button>
          </div>
          <div v-else-if="hotPosts.length === 0" class="empty">
            <p>暂无热门文章</p>
          </div>
          <div v-else class="posts-list">
            <article
              v-for="post in hotPosts"
              :key="post.id"
              class="post-item"
              @click="goToPost(post.id)"
            >
              <div class="post-content">
                <div class="post-header">
                  <h3 class="post-title">{{ post.title }}</h3>
                  <span v-if="post.category" class="post-category">{{ post.category.name }}</span>
                </div>
                <p v-if="post.summary" class="post-summary">{{ post.summary }}</p>
                <div class="post-meta">
                  <div class="author-info">
                    <img
                      v-if="post.author?.avatarUrl"
                      :src="post.author.avatarUrl"
                      :alt="post.author.username"
                      class="author-avatar"
                    >
                    <span class="author-name">{{ post.author?.username || '匿名用户' }}</span>
                  </div>
                  <div class="post-stats">
                    <span class="comment-count">💬 {{ post.commentCount }}</span>
                    <span class="post-date">{{ formatDate(post.createdAt) }}</span>
                  </div>
                </div>
<!--                <div v-if="post.tags && post.tags.length > 0" class="post-tags">-->
<!--                  <span-->
<!--                    v-for="tag in post.tags"-->
<!--                    :key="tag.id"-->
<!--                    class="tag"-->
<!--                  >-->
<!--                    {{ tag.name }}-->
<!--                  </span>-->
<!--                </div>-->
              </div>
            </article>
          </div>
        </div>
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
.home {
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

/* 卡片通用样式 */
.profile-card,
.announcement-card,
.tags-card,
.links-card {
  background: var(--bg-color);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);
}

/* 个人信息卡片 */
.profile-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.profile-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  object-fit: cover;
}

.profile-info {
  flex: 1;
}

.profile-name {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 4px 0;
}

.profile-title {
  color: var(--text-color);
  opacity: 0.7;
  margin: 0;
  font-size: 0.9rem;
}

.profile-stats {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 12px 0;
  border-top: 1px solid var(--border-color);
  border-bottom: 1px solid var(--border-color);
}

.stat-item {
  text-align: center;
  flex: 1;
}

.stat-number {
  display: block;
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--primary-color);
}

.stat-label {
  font-size: 0.8rem;
  color: var(--text-color);
  opacity: 0.7;
}

.profile-bio {
  color: var(--text-color);
  opacity: 0.8;
  line-height: 1.5;
  margin: 0;
  font-size: 0.9rem;
}

/* 卡片标题 */
.card-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
}

.loading-text {
  text-align: center;
  color: var(--text-color);
  opacity: 0.6;
  font-size: 0.9rem;
  padding: 20px 0;
}

.empty-text {
  text-align: center;
  color: var(--text-color);
  opacity: 0.5;
  font-size: 0.9rem;
  padding: 20px 0;
}

/* 公告栏 */
.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.announcement-item {
  padding: 8px 0;
}

.announcement-date {
  font-size: 0.75rem;
  color: var(--primary-color);
  font-weight: 500;
}

.announcement-text {
  margin: 4px 0 0 0;
  font-size: 0.85rem;
  color: var(--text-color);
  opacity: 0.8;
  line-height: 1.4;
}

/* 分类展示 */
.categories-card {
  background: var(--bg-color);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);
}

.categories-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.category-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: var(--hover-color);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-left: 3px solid var(--border-color);
}

.category-item:hover {
  background: var(--primary-color);
  color: white;
  border-left-color: var(--secondary-color);
  transform: translateX(4px);
}

.category-name {
  font-weight: 500;
  color: var(--text-color);
}

.category-count {
  background: var(--primary-color);
  color: white;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

/* 标签云 */
.tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  background: var(--hover-color);
  color: var(--text-color);
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.tag-item:hover {
  background: var(--primary-color);
  color: white;
}

.tag-item small {
  opacity: 0.8;
  margin-left: 4px;
}

/* 推荐文章 */
.recommended-card {
  background: var(--bg-color);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);
}

.recommended-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recommended-item {
  padding: 12px;
  background: var(--hover-color);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-left: 3px solid var(--border-color);
}

.recommended-item:hover {
  background: var(--primary-color);
  color: white;
  border-left-color: var(--secondary-color);
  transform: translateX(4px);
}

.recommended-title {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-color);
  margin: 0 0 8px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.recommended-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.75rem;
  color: var(--text-color);
  opacity: 0.7;
}

.recommended-author {
  font-weight: 500;
}

.recommended-date {
  opacity: 0.8;
}

/* 友情链接 */
.links-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.link-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 8px;
  text-decoration: none;
  color: var(--text-color);
  transition: background-color 0.2s;
}

.link-item:hover {
  background: var(--hover-color);
}

.link-icon {
  font-size: 1.1rem;
}

.link-text {
  font-size: 0.9rem;
  font-weight: 500;
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

/* 文章区域 */
.posts-section {
  background: var(--bg-color);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.section-header h2 {
  font-size: 1.5rem;
  color: var(--text-color);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.view-all-btn {
  padding: 6px 12px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: background-color 0.3s;
}

.view-all-btn:hover {
  background: var(--secondary-color);
}

.loading, .error, .empty {
  text-align: center;
  padding: 40px;
  color: var(--text-color);
  opacity: 0.7;
}

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.retry-btn:hover {
  background: var(--secondary-color);
}

/* 文章列表 */
.posts-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.post-item {
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.post-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  border-color: var(--primary-color);
}

.post-content {
  width: 100%;
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.post-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
  line-height: 1.4;
  flex: 1;
  margin-right: 12px;
}

.post-category {
  background: var(--primary-color);
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.post-summary {
  color: var(--text-color);
  opacity: 0.7;
  line-height: 1.6;
  margin: 12px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 0.9rem;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0;
  font-size: 0.85rem;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.author-name {
  color: var(--text-color);
  opacity: 0.8;
  font-weight: 500;
}

.post-stats {
  display: flex;
  gap: 12px;
  color: var(--text-color);
  opacity: 0.6;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.tag {
  background: var(--hover-color);
  color: var(--text-color);
  opacity: 0.8;
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 0.75rem;
  font-weight: 500;
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

  .profile-card,
  .announcement-card,
  .tags-card,
  .links-card {
    padding: 16px;
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

  .posts-section {
    padding: 20px;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .post-item {
    padding: 16px;
  }

  .post-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .post-category {
    align-self: flex-start;
  }

  .post-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}

@media (max-width: 480px) {
  .home {
    padding: 12px;
  }

  .profile-stats {
    flex-direction: column;
    gap: 8px;
  }

  .stat-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    text-align: left;
  }

  .tags-cloud {
    gap: 6px;
  }

  .tag-item {
    font-size: 0.7rem;
    padding: 3px 6px;
  }
}
</style>
