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
            <span class="view-count">👁️ {{ post.viewCount || 0 }}</span>
            <span class="like-count">❤️ {{ post.likeCount || 0 }}</span>
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

      <!-- 文章操作按钮 -->
      <div class="post-actions">
        <button @click="handleLike" class="like-btn" :class="{ 'liked': isLiked }" :disabled="liking">
          <span class="like-icon">{{ isLiked ? '❤️' : '🤍' }}</span>
          <span class="like-text">{{ isLiked ? '已喜欢' : '喜欢' }}</span>
          <span class="like-count">({{ currentLikeCount }})</span>
        </button>
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

// 喜欢按钮相关状态
const isLiked = ref(false)
const liking = ref(false)
const currentLikeCount = ref(0)

// 计算属性：渲染富文本内容
const renderedContent = computed(() => {
  if (!post.value?.content) return ''
  // TinyMCE生成的内容已经是HTML格式，直接返回
  // 如果内容是纯文本，则进行简单的换行处理
  const content = post.value.content
  
  // 检查是否包含HTML标签
  const hasHtmlTags = /<[^>]*>/g.test(content)
  
  if (hasHtmlTags) {
    // 已经是HTML格式，直接返回
    return content
  } else {
    // 纯文本内容，进行简单的格式化
    return content
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/`(.*?)`/g, '<code>$1</code>')
  }
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
    
    // 初始化喜欢数量
    currentLikeCount.value = postData.likeCount || 0
    
    // 动态更新路由meta信息，用于面包屑导航
    if (postData && route.meta) {
      route.meta.title = postData.title
      if (postData.category) {
        route.meta.category = postData.category.name
        route.meta.categoryId = postData.category.id
      }
    }
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

// 处理点赞
const handleLike = async () => {
  if (!post.value || liking.value) return
  
  await handleAsync(async () => {
    liking.value = true
    
    await PostService.likePost(post.value!.id)
    
    // 更新本地状态
    isLiked.value = true
    currentLikeCount.value += 1
    
    // 显示成功提示
    console.log('点赞成功！')
  }, {
    onError: (err) => {
      console.error('点赞失败:', err)
      // 这里可以添加错误提示
    },
    onFinally: () => {
      liking.value = false
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
  max-width: 1200px;
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

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 16px 0;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin: 8px 0;
  line-height: 1.6;
}

.markdown-content :deep(a) {
  color: var(--primary-color);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.3s;
}

.markdown-content :deep(a:hover) {
  border-bottom-color: var(--primary-color);
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 16px 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.markdown-content :deep(blockquote) {
  margin: 16px 0;
  padding: 16px 20px;
  background: var(--hover-color);
  border-left: 4px solid var(--primary-color);
  border-radius: 0 4px 4px 0;
}

.markdown-content :deep(blockquote p) {
  margin: 0;
  font-style: italic;
  opacity: 0.9;
}

.markdown-content :deep(pre) {
  background: #f8f9fa;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 16px;
  overflow-x: auto;
  margin: 16px 0;
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
  font-size: 0.875rem;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  overflow: hidden;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
}

.markdown-content :deep(th) {
  background: var(--hover-color);
  font-weight: 600;
}

.markdown-content :deep(tr:last-child td) {
  border-bottom: none;
}

.markdown-content :deep(hr) {
  border: none;
  height: 1px;
  background: var(--border-color);
  margin: 24px 0;
}

.post-actions {
  padding: 20px 30px;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: center;
  gap: 15px;
  align-items: center;
}

.like-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: linear-gradient(135deg, #ff6b6b, #ff8e8e);
  color: white;
  border: none;
  border-radius: 25px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(255, 107, 107, 0.3);
}

.like-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 107, 0.4);
}

.like-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.like-btn.liked {
  background: linear-gradient(135deg, #e74c3c, #c0392b);
  animation: likeAnimation 0.6s ease;
}

@keyframes likeAnimation {
  0% { transform: scale(1); }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); }
}

.like-icon {
  font-size: 16px;
  transition: transform 0.3s ease;
}

.like-btn:hover .like-icon {
  transform: scale(1.2);
}

.like-text {
  font-weight: 600;
}

.like-count {
  font-size: 12px;
  opacity: 0.9;
}

.back-btn {
  padding: 12px 24px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 25px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.back-btn:hover {
  background: var(--primary-hover-color);
  transform: translateY(-2px);
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