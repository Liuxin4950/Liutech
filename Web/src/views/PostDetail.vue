<template>
  <div class="post-detail content">
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
            :src="displayImage" 
            :alt="post.title" 
            class="cover-image"
            :class="{ 'loading': imageLoading }"
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
        <!-- 文章互动功能条 -->
        <div class="post-actions">
          <div class="actions-left">
            <!-- 点赞按钮 -->
            <button 
              @click="handleLike" 
              :class="['action-btn', { 'liked': isLiked }]"
              :disabled="liking"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
              </svg>
              <span>{{ isLiked ? '已点赞' : '点赞' }}</span>
              <span class="count">({{ currentLikeCount }})</span>
            </button>
            
            <!-- 评论数 -->
            <div class="action-info">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
              <span>评论 ({{ post?.commentCount || 0 }})</span>
            </div>
            
            <!-- 阅读数 -->
            <div class="action-info">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
              <span>阅读 ({{ post?.viewCount || 0 }})</span>
            </div>
          </div>
          
          <div class="actions-right">
            <!-- 分享按钮 -->
            <div class="share-group">
              <button @click="toggleShare" class="action-btn share-btn">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="18" cy="5" r="3"/>
                  <circle cx="6" cy="12" r="3"/>
                  <circle cx="18" cy="19" r="3"/>
                  <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                  <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
                </svg>
                <span>分享</span>
              </button>
              
              <!-- 分享选项 -->
              <div v-if="showShare" class="share-options">
                <button @click="shareToWeChat" class="share-option wechat">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M8.5 12c-.83 0-1.5-.67-1.5-1.5S7.67 9 8.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm7 0c-.83 0-1.5-.67-1.5-1.5S14.67 9 15.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"/>
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8 0-1.85.63-3.55 1.69-4.9L16.9 18.31C15.55 19.37 13.85 20 12 20z"/>
                  </svg>
                  <span>微信</span>
                </button>
                
                <button @click="shareToQQ" class="share-option qq">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/>
                  </svg>
                  <span>QQ</span>
                </button>
                
                <button @click="copyLink" class="share-option link">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                    <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
                  </svg>
                  <span>复制链接</span>
                </button>
              </div>
            </div>
          </div>
        </div>
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
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostDetail } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { showError, showSuccessToast } from '@/utils/errorHandler'
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

// 分享功能相关状态
const showShare = ref(false)

// 图片预加载相关状态
const imageLoading = ref(true)
const displayImage = ref('/src/assets/image/images.jpg') // 默认图片

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

// 点击外部区域关闭分享选项
const handleClickOutside = (event: Event) => {
  const target = event.target as HTMLElement
  const shareGroup = target.closest('.share-group')
  if (!shareGroup && showShare.value) {
    showShare.value = false
  }
}

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
    
    // 预加载封面图片
    preloadCoverImage(postData)
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
    showSuccessToast('点赞成功！')
  }, {
    onError: (err) => {
      console.error('点赞失败:', err)
      showError('点赞失败，请稍后重试')
    },
    onFinally: () => {
      liking.value = false
    }
  })
}

// 预加载封面图片
const preloadCoverImage = (postData: PostDetail) => {
  const imageUrl = postData.coverImage || postData.thumbnail
  
  if (imageUrl) {
    const img = new Image()
    img.onload = () => {
      // 图片加载完成，替换显示的图片
      displayImage.value = imageUrl
      imageLoading.value = false
    }
    img.onerror = () => {
      // 图片加载失败，保持默认图片
      imageLoading.value = false
    }
    img.src = imageUrl
  } else {
    // 没有封面图片，直接使用默认图片
    imageLoading.value = false
  }
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 切换分享选项显示
const toggleShare = () => {
  showShare.value = !showShare.value
}

// 分享到微信
const shareToWeChat = () => {
  const url = window.location.href
  const title = post.value?.title || '分享文章'
  
  // 微信分享通常需要微信JS-SDK，这里提供一个简单的实现
  if (navigator.share) {
    navigator.share({
      title: title,
      text: post.value?.summary || '来看看这篇有趣的文章',
      url: url
    }).catch(err => {
      console.log('分享失败:', err)
      showError('分享失败，请稍后重试')
    })
  } else {
    // 备用方案：复制链接
    copyLink()
  }
  showShare.value = false
}

// 分享到QQ
const shareToQQ = () => {
  const url = encodeURIComponent(window.location.href)
  const title = encodeURIComponent(post.value?.title || '分享文章')
  const summary = encodeURIComponent(post.value?.summary || '来看看这篇有趣的文章')
  
  const qqShareUrl = `https://connect.qq.com/widget/shareqq/index.html?url=${url}&title=${title}&summary=${summary}`
  window.open(qqShareUrl, '_blank', 'width=600,height=400')
  showShare.value = false
}

// 复制链接
const copyLink = async () => {
  try {
    await navigator.clipboard.writeText(window.location.href)
    showSuccessToast('链接已复制到剪贴板！')
  } catch (err) {
    console.error('复制失败:', err)
    // 备用方案
    try {
      const textArea = document.createElement('textarea')
      textArea.value = window.location.href
      document.body.appendChild(textArea)
      textArea.select()
      document.execCommand('copy')
      document.body.removeChild(textArea)
      showSuccessToast('链接已复制到剪贴板！')
    } catch (fallbackErr) {
      console.error('备用复制方案也失败:', fallbackErr)
      showError('复制失败，请手动复制链接')
    }
  }
  showShare.value = false
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
  // 添加点击外部区域关闭分享选项的事件监听
  document.addEventListener('click', handleClickOutside)
})

// 组件卸载时清理事件监听器
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.post-detail {
  margin: 0 auto;

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
  transition: opacity 0.3s ease-in-out;
}

.cover-image.loading {
  opacity: 0.7;
  filter: blur(1px);
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

/* TinyMCE 富文本内容样式适配 */
.markdown-content :deep(*) {
  color: inherit;
}

.markdown-content :deep(div),
.markdown-content :deep(span) {
  color: var(--text-color);
}

.markdown-content :deep([style*="color"]) {
  color: var(--text-color) !important;
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
  background: var(--tag-bg-color);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
  color: var(--primary-color);
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
  background: var(--hover-color);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 16px;
  overflow-x: auto;
  margin: 16px 0;
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
  color: var(--text-color);
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

/* 文章互动功能条样式 */
.post-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 0;
  margin-top: 30px;
  border-top: 1px solid var(--border-color);
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-color);
  position: sticky;
  bottom: 0;
  z-index: 999;
}

.actions-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.actions-right {
  display: flex;
  align-items: center;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  color: var(--text-color);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.action-btn:hover {
  background: var(--hover-color);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.action-btn.liked {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.action-btn.liked svg {
  fill: currentColor;
}

.action-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 14px;
}

.action-info svg {
  opacity: 0.7;
}

.count {
  color: var(--text-muted);
  font-size: 13px;
}

/* 分享功能样式 */
.share-group {
  position: relative;
}

.share-btn {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.share-btn:hover {
  background: var(--hover-color);
  border-color: var(--primary-hover);
  color: #000;
}

.share-options {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  min-width: 120px;
  overflow: hidden;
}

.share-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 16px;
  background: transparent;
  border: none;
  color: var(--text-color);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s ease;
  text-align: left;
}

.share-option:hover {
  background: var(--hover-color);
}

.share-option.wechat:hover {
  background: #07c160;
  color: white;
}

.share-option.qq:hover {
  background: #12b7f5;
  color: white;
}

.share-option.link:hover {
  background: var(--primary-color);
  color: white;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .post-actions {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }
  
  .actions-left {
    justify-content: space-around;
    gap: 12px;
  }
  
  .actions-right {
    justify-content: center;
  }
  
  .action-btn {
    padding: 6px 12px;
    font-size: 13px;
  }
  
  .action-info {
    font-size: 13px;
  }
  
  .share-options {
    right: auto;
    left: 50%;
    transform: translateX(-50%);
  }
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
  background: var(--bg-color);
  backdrop-filter: blur(10px);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color);
}

.back-btn-top:hover {
  background: var(--hover-color);
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