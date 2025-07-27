<template>
  <div class="home">
    <div class="hero-section">
      <h1>我的博客</h1>
      <p>欢迎来到我的个人博客！这里分享技术文章、生活感悟和学习心得。</p>
    </div>
    
    <div class="hot-posts-section">
      <h2>🔥 热门文章</h2>
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
      <div v-else class="posts-grid">
        <article 
          v-for="post in hotPosts" 
          :key="post.id" 
          class="post-card"
          @click="goToPost(post.id)"
        >
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
          <div v-if="post.tags && post.tags.length > 0" class="post-tags">
            <span 
              v-for="tag in post.tags" 
              :key="tag.id" 
              class="tag"
            >
              {{ tag.name }}
            </span>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostListItem } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'

const router = useRouter()
const { handleAsync } = useErrorHandler()

// 响应式数据
const hotPosts = ref<PostListItem[]>([])
const loading = ref(false)
const error = ref('')

// 加载热门文章
const loadHotPosts = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''
    
    const posts = await PostService.getHotPosts(6) // 获取6篇热门文章
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
  loadHotPosts()
})
</script>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.hero-section {
  text-align: center;
  margin-bottom: 40px;
  padding: 40px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
}

.hero-section h1 {
  font-size: 2.5rem;
  margin-bottom: 16px;
  font-weight: 700;
}

.hero-section p {
  font-size: 1.1rem;
  opacity: 0.9;
  line-height: 1.6;
}

.hot-posts-section {
  margin-top: 40px;
}

.hot-posts-section h2 {
  font-size: 1.8rem;
  color: var(--text-color);
  margin-bottom: 24px;
  text-align: center;
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

.posts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: 24px;
  margin-top: 24px;
}

.post-card {
  background: var(--bg-color);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  cursor: pointer;
  border: 1px solid var(--border-color);
}

.post-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.post-title {
  font-size: 1.2rem;
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
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0;
  font-size: 0.875rem;
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
@media (max-width: 768px) {
  .home {
    padding: 16px;
  }
  
  .hero-section {
    padding: 24px 16px;
  }
  
  .hero-section h1 {
    font-size: 2rem;
  }
  
  .posts-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .post-card {
    padding: 20px;
  }
  
  .post-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .post-category {
    align-self: flex-start;
  }
}
</style>