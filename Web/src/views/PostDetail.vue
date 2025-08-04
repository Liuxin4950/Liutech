<template>
  <div class="post-detail">
    <div v-if="loading" class="text-center p-20 text-muted">
      <p>加载中...</p>
    </div>
    <div v-else-if="error" class="text-center p-20 text-muted">
      <p>{{ error }}</p>
      <button @click="loadPostDetail" class="retry-btn bg-primary text-center rounded transition mt-8">重试</button>
    </div>
    <div v-else-if="post" class="card">
      <!-- 文章头部信息 -->
      <header class="post-header">
        <!-- 返回按钮 -->
        <button @click="goBack" class="back-btn-top">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <span>返回</span>
        </button>
        
        <h1 class="post-title">{{ post.title }}</h1>
        
        <!-- 封面图片 -->
        <div class="post-cover rounded-lg mb-16">
          <img 
            :src="post.coverImage || post.thumbnail || '/src/assets/image/images.jpg'" 
            :alt="post.title" 
            class="cover-image"
          >
        </div>
        
        <div class="flex flex-sb flex-ac mb-16 flex-fw gap-12">
          <div class="flex flex-ac gap-8">
            <img 
              v-if="post.author?.avatarUrl" 
              :src="post.author.avatarUrl" 
              :alt="post.author.username"
              class="author-avatar"
            >
            <span class="text-muted font-medium">{{ post.author?.username || '匿名用户' }}</span>
          </div>
          <div class="flex gap-16 flex-ac text-sm text-muted">
            <span v-if="post.category" class="badge">{{ post.category.name }}</span>
            <span>{{ formatDate(post.createdAt) }}</span>
            <span>👁️ {{ post.viewCount || 0 }}</span>
            <span>❤️ {{ post.likeCount || 0 }}</span>
            <span>💬 {{ post.commentCount }}</span>
          </div>
        </div>
        <div v-if="post.tags && post.tags.length > 0" class="tags-cloud">
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
      <div v-if="post.summary" class="post-summary bg-hover border-l-3 p-20">
        <p class="text-muted">{{ post.summary }}</p>
      </div>

      <!-- 文章内容 -->
      <article class="p-20">
        <div class="markdown-content" v-html="renderedContent"></div>
      </article>
      
      <!-- 评论模块 -->
      <div class="p-20">
        <CommentSection :post-id="Number(route.params.id)" />
      </div>
    </div>
    <div v-else class="text-center p-20 text-muted">
      <p>文章不存在</p>
      <button @click="goBack" class="bg-primary text-center rounded transition mt-8">返回首页</button>
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

.retry-btn {
  padding: 8px 16px;
  color: white;
  border: none;
  cursor: pointer;
}

.retry-btn:hover {
  background: var(--secondary-color) !important;
}

.post-header {
  position: relative;
  padding: 60px 30px 30px 30px;
  border-bottom: 1px solid var(--border-color);
}

.post-title {
  font-size: 2.2rem;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 20px 0;
  line-height: 1.3;
}

.post-cover {
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.cover-image {
  width: 100%;
  height: auto;
  max-height: 400px;
  object-fit: cover;
  display: block;
}

.author-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.post-summary {
  margin: 0;
  border-left-color: var(--primary-color);
}

.post-summary p {
  margin: 0;
  font-style: italic;
  line-height: 1.6;
}

/* Markdown 内容样式 */
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

.markdown-content :deep(p) { margin: 16px 0; }
.markdown-content :deep(strong) { font-weight: 600; }
.markdown-content :deep(em) { font-style: italic; }

.markdown-content :deep(code) {
  background: #f1f2f6;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
  color: #e74c3c;
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

/* 返回按钮 */
.back-btn-top {
  position: absolute;
  top: 20px;
  left: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color);
  transition: all 0.3s ease;
  z-index: 10;
}

.back-btn-top:hover {
  background: rgba(255, 255, 255, 1);
  transform: translateX(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.back-btn-top svg {
  transition: transform 0.3s ease;
}

.back-btn-top:hover svg {
  transform: translateX(-2px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .post-detail {
    padding: 16px;
  }
  
  .post-header {
    padding: 50px 20px 20px 20px;
  }
  
  .back-btn-top {
    top: 16px;
    left: 16px;
    padding: 6px 12px;
    font-size: 13px;
  }
  
  .back-btn-top svg {
    width: 16px;
    height: 16px;
  }
  
  .post-title {
    font-size: 1.8rem;
  }
}
</style>