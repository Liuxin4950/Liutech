<template>
  <div class="post-detail">
    <div v-if="loading" class="loading">
      <p>加载中...</p>
    </div>
    <div v-else-if="error" class="error">
      <p>{{ error }}</p>
      <button @click="loadPostDetail" class="retry-btn">重试</button>
    </div>
    <div v-else-if="post" class="post-content">
      <!-- 文章头部信息 -->
      <header class="post-header">
        <h1 class="post-title">{{ post.title }}</h1>
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
          <div class="post-info">
            <span v-if="post.category" class="post-category">{{ post.category.name }}</span>
            <span class="post-date">{{ formatDate(post.createdAt) }}</span>
            <span class="comment-count">💬 {{ post.commentCount }}</span>
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
      </header>

      <!-- 文章摘要 -->
      <div v-if="post.summary" class="post-summary">
        <p>{{ post.summary }}</p>
      </div>

      <!-- 文章内容 -->
      <article class="post-body">
        <div class="markdown-content" v-html="renderedContent"></div>
      </article>

      <!-- 返回按钮 -->
      <div class="post-actions">
        <button @click="goBack" class="back-btn">返回</button>
      </div>
      
      <!-- 评论模块 -->
      <div class="comment-section-wrapper">
        <CommentSection :post-id="Number(route.params.id)" />
      </div>
    </div>
    <div v-else class="not-found">
      <p>文章不存在</p>
      <button @click="goBack" class="back-btn">返回首页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostDetail } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import CommentSection from '@/components/CommentSection.vue'

const route = useRoute()
const router = useRouter()
const { handleAsync } = useErrorHandler()

// 响应式数据
const post = ref<PostDetail | null>(null)
const loading = ref(false)
const error = ref('')

// 计算属性：渲染Markdown内容
const renderedContent = computed(() => {
  if (!post.value?.content) return ''
  // 简单的Markdown渲染（这里可以后续集成专业的Markdown解析器）
  return post.value.content
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
})

// 加载文章详情
const loadPostDetail = async () => {
  const postId = Number(route.params.id)
  if (!postId) {
    error.value = '无效的文章ID'
    return
  }

  await handleAsync(async () => {
    loading.value = true
    error.value = ''
    
    const postData = await PostService.getPostDetail(postId)
    post.value = postData
  }, {
    onError: (err) => {
      error.value = '加载文章详情失败，请稍后重试'
      console.error('加载文章详情失败:', err)
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 格式化日期
const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 组件挂载时加载数据
onMounted(() => {
  loadPostDetail()
})
</script>

<style scoped>
.post-detail {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.loading, .error, .not-found {
  text-align: center;
  padding: 40px;
  color: #7f8c8d;
}

.retry-btn, .back-btn {
  margin-top: 12px;
  padding: 8px 16px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.retry-btn:hover, .back-btn:hover {
  background: var(--secondary-color);
}

.post-content {
  background: var(--bg-color);
  border-radius: 8px;
  overflow: hidden;
}

.post-header {
  padding: 30px;
  border-bottom: 1px solid var(--border-color);
}

.post-title {
  font-size: 2.2rem;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 20px 0;
  line-height: 1.3;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.author-name {
  color: var(--text-color);
  opacity: 0.8;
  font-weight: 500;
  font-size: 0.95rem;
}

.post-info {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 0.875rem;
  color: var(--text-color);
  opacity: 0.6;
}

.post-category {
  background: var(--primary-color);
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  background: var(--hover-color);
  color: var(--text-color);
  opacity: 0.8;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.post-summary {
  padding: 20px 30px;
  background: var(--hover-color);
  border-left: 4px solid var(--primary-color);
  margin: 0;
}

.post-summary p {
  margin: 0;
  color: var(--text-color);
  opacity: 0.8;
  font-style: italic;
  line-height: 1.6;
}

.post-body {
  padding: 30px;
}

.markdown-content {
  line-height: 1.8;
  color: var(--text-color);
  font-size: 1rem;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 24px 0 16px 0;
  font-weight: 600;
  line-height: 1.4;
}

.markdown-content :deep(h1) { font-size: 1.8rem; }
.markdown-content :deep(h2) { font-size: 1.5rem; }
.markdown-content :deep(h3) { font-size: 1.3rem; }
.markdown-content :deep(h4) { font-size: 1.1rem; }

.markdown-content :deep(p) {
  margin: 16px 0;
}

.markdown-content :deep(code) {
  background: #f1f2f6;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
  color: #e74c3c;
}

.markdown-content :deep(strong) {
  font-weight: 600;
}

.markdown-content :deep(em) {
  font-style: italic;
}

.post-actions {
  padding: 20px 30px;
  border-top: 1px solid var(--border-color);
  text-align: center;
}

.comment-section-wrapper {
  padding: 0 30px 30px 30px;
  background: var(--bg-color);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .post-detail {
    padding: 16px;
  }
  
  .post-header {
    padding: 20px;
  }
  
  .post-title {
    font-size: 1.8rem;
  }
  
  .post-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .post-info {
    gap: 12px;
  }
  
  .post-summary {
    padding: 16px 20px;
  }
  
  .post-body {
    padding: 20px;
  }
  
  .post-actions {
    padding: 16px 20px;
  }
  
  .comment-section-wrapper {
    padding: 0 20px 20px 20px;
  }
}
</style>