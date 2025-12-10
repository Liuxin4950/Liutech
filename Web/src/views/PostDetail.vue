<template>
  <div class="post-detail content">
    <div v-if="loading" class="text-center p-20 ">
      <p>加载中...</p>
    </div>
    <div v-else-if="error" class="text-center p-20 ">
      <p>{{ error }}</p>
      <button @click="loadPostDetail" class="retry-btn bg-primary text-center rounded transition mt-8">重试</button>
    </div>
    <div v-else-if="post" class="card bg-soft">
      <!-- 文章头部信息 -->
      <header class="post-header">
        <h2 class="post-title">{{ post.title }}</h2>

        <!-- 封面图片 -->
        <!-- <div class="post-cover mb-16">
          <img :src="displayImage" :alt="post.title" class="cover-image" :class="{ 'loading': imageLoading }">
        </div> -->

        <div class="post-meta-info">
          <div class="meta-left-section">
            <div class="author-info">
              <img v-if="post.author?.avatarUrl" :src="post.author.avatarUrl" :alt="post.author.username"
                class="author-avatar">
              <span class="author-name">{{ post.author?.username || '匿名用户' }}</span>
            </div>
          </div>
          <div class="meta-right-section">
            <span v-if="post.category" class="category-badge">{{ post.category.name }}</span>
            <div class="meta-stat">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8-4 8-11-8-11 8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
              {{ formatDate(post.createdAt) }}
            </div>
            <div class="meta-stat">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8-4 8-11-8-11 8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
              {{ post.viewCount || 0 }}
            </div>
            <div class="meta-stat">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3c2.08 0 3.5 2.42 3.5 4.5 0 2.78-2.42 4.5-3.5 4.5-.05 0-.1-.01-.15-.02l1.65 1.5c.05.04.1.06.16.06.11 0 .21-.08.39-.27.69-.27.28-.11.48-.21.69-.27.28-.11.21-.27.28-.69.27-.05.01-.1.02-.15.02zm1.39-1.81c.44-.25.79-.74.79-1.33 0-.88-.65-1.62-1.52-1.85l1.42-1.3c.37.36.59.92.59 1.52 0 1.11-.7 1.87-1.77 1.87H9c-.88 0-1.63-.39-2.12-.96l1.42 1.3c.19-.17.43-.27.7-.27.88 0 .59.35 1.08.79 1.33l1.42-1.3c-.49-.57-.79-1.3-.79-2.12z"/>
              </svg>
              {{ post.likeCount || 0 }}
            </div>
            <div class="meta-stat">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
              {{ post.commentCount }}
            </div>
          </div>
        </div>
        <div v-if="post.tags && post.tags.length > 0" class="tags-cloud">
          <span v-for="tag in post.tags" :key="tag.id" class="tag">
            {{ tag.name }}
          </span>
        </div>
      </header>

      <!-- 文章摘要 -->
      <div v-if="post.summary" class="post-summary bg-hover p-20">
        <p class="">{{ post.summary }}</p>
      </div>

      <!-- 文章内容 -->
      <article class="">
        <div class="markdown-content" v-html="renderedContent"></div>
      </article>

      <!-- 附件列表 -->
      <section v-if="post.attachments && post.attachments.length" class="mt-16">
        <h3 class="mb-12">附件</h3>
        <ul class="list-unstyled flex flex-col gap-8">
          <li v-for="att in post.attachments" :key="att.attachmentId"
            class="flex flex-sb flex-ac bg-hover p-12 rounded">
            <div class="flex flex-col">
              <template v-if="att.purchased && att.fileUrl">
                <a class="link" :href="att.fileUrl" target="_blank" rel="noopener" :title="att.fileName">📎 {{
                  att.fileName }}</a>
              </template>
              <template v-else>
                <span class="text-muted">📎 {{ att.fileName }}</span>
              </template>
              <div class="text-sm text-muted flex gap-12 mt-4">
                <span v-if="att.pointsNeeded && !att.purchased">需要积分：{{ att.pointsNeeded }}</span>
                <span>上传时间：{{ formatDate(att.createdTime) }}</span>
              </div>
            </div>
            <div class="flex gap-8">
              <a v-if="att.purchased && att.fileUrl" class="action-btn" :href="att.fileUrl" target="_blank"
                rel="noopener">下载/查看</a>
              <button v-else-if="!att.purchased && att.pointsNeeded" class="action-btn"
                :disabled="purchasingId === att.resourceId" @click="onPurchase(att.resourceId)">
                {{ purchasingId === att.resourceId ? '购买中...' : (att.pointsNeeded ? `购买（${att.pointsNeeded} 积分）` : '购买')
                }}
              </button>
            </div>
          </li>
        </ul>
      </section>

      <!-- 文章交互 -->
      <div class="post-actions">
        <div class="actions-left">
          <!-- 点赞按钮 -->
          <button @click="handleLike" :class="['action-btn', { 'liked': isLiked }]" :disabled="liking">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path
                d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
            </svg>
            <span>{{ isLiked ? '已点赞' : '点赞' }}</span>
            <span class="count">({{ currentLikeCount }})</span>
          </button>

          <!-- 收藏按钮 -->
          <button @click="handleFavorite" :class="['action-btn', { 'favorited': isFavorited }]" :disabled="favoriting">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z" />
            </svg>
            <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
            <span class="count">({{ currentFavoriteCount }})</span>
          </button>

          <!-- 评论数 -->
          <div class="action-info">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            <span>评论 ({{ post?.commentCount || 0 }})</span>
          </div>

          <!-- 阅读数 -->
          <div class="action-info">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            <span>阅读 ({{ post?.viewCount || 0 }})</span>
          </div>
        </div>

        <div class="actions-right">
          <!-- 分享按钮 -->
          <div class="share-group">
            <button @click="toggleShare" class="action-btn share-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="18" cy="5" r="3" />
                <circle cx="6" cy="12" r="3" />
                <circle cx="18" cy="19" r="3" />
                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
              </svg>
              <span>分享</span>
            </button>

            <!-- 分享选项 -->
            <div v-if="showShare" class="share-options">
              <button @click="shareToWeChat" class="share-option wechat">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path
                    d="M8.5 12c-.83 0-1.5-.67-1.5-1.5S7.67 9 8.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm7 0c-.83 0-1.5-.67-1.5-1.5S14.67 9 15.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" />
                  <path
                    d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8 0-1.85.63-3.55 1.69-4.9L16.9 18.31C15.55 19.37 13.85 20 12 20z" />
                </svg>
                <span>微信</span>
              </button>

              <button @click="shareToQQ" class="share-option qq">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path
                    d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" />
                </svg>
                <span>QQ</span>
              </button>

              <button @click="copyLink" class="share-option link">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                  <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
                </svg>
                <span>复制链接</span>
              </button>
            </div>
          </div>
        </div>
      </div>
      <!-- 评论模块 -->
      <div class="">
        <CommentSection :post-id="Number(route.params.id)" />
      </div>
    </div>
    <div v-else class="text-center p-20 ">
      <p>文章不存在</p>
      <button @click="goBack" class="bg-primary text-center rounded transition mt-8">返回首页</button>
    </div>

    <!-- 登录弹窗 -->
    <LoginModal v-model:visible="showLoginModal" :message="loginMessage" />

    <!-- 目录导航 -->
    <div class="table-of-contents-container">
      <TableOfContents class="table-of-contents" v-if="post && !loading && !error" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostDetail } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatDate } from '@/utils/uitls'
import CommentSection from '@/components/CommentSection.vue'
import { isLoggedIn } from '../utils/auth'
import LoginModal from '../components/LoginModal.vue'
import { usePostInteractionStore } from '@/stores/postInteraction'
import TableOfContents from '@/components/TableOfContents.vue'

// 动态加载Prism.js和Prism.css用于代码高亮
const loadPrism = () => {
  // 直接加载完整版Prism
  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = 'https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism.min.css'
  document.head.appendChild(link)

  const script = document.createElement('script')
  script.src = 'https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js'
  script.onload = () => {
    // 自动检测页面中有什么语言，只加载需要的
    setTimeout(() => {
      const codeBlocks = document.querySelectorAll('pre code[class*="language-"]')
      const languages = new Set<string>()
      
      codeBlocks.forEach(block => {
        const match = block.className.match(/language-(\w+)/)
        if (match) {
          languages.add(match[1])
        }
      })
      
      // 加载需要的语言
      let loadedCount = 0
      const totalLanguages = languages.size
      
      if (totalLanguages === 0) {
        // 没有特殊语言，直接高亮
        if ((window as any).Prism) {
          (window as any).Prism.highlightAll()
        }
        return
      }
      
      languages.forEach(lang => {
        const langScript = document.createElement('script')
        langScript.src = `https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-${lang}.min.js`
        langScript.onload = () => {
          loadedCount++
          if (loadedCount === totalLanguages) {
            // 所有语言加载完成，执行高亮
            if ((window as any).Prism) {
              (window as any).Prism.highlightAll()
            }
          }
        }
        document.head.appendChild(langScript)
      })
    }, 100)
  }
  document.head.appendChild(script)
}

const route = useRoute()
const router = useRouter()
const { handleAsync, showSuccessToast, showError } = useErrorHandler()

// 已移除：旧的基于 referrer 的导航激活逻辑，改由 Header 基于当前路径自动判定
// 响应式数据
const post = ref<PostDetail | null>(null)
const loading = ref(false)
const error = ref('')

// 喜欢按钮相关状态
const isLiked = ref(false)
const liking = ref(false)
const currentLikeCount = ref(0)

// 收藏按钮相关状态
const isFavorited = ref(false)
const favoriting = ref(false)
const currentFavoriteCount = ref(0)

// 分享功能相关状态
const showShare = ref(false)

// 登录弹窗相关状态
const showLoginModal = ref(false)
const loginMessage = ref('点赞和收藏功能需要登录后才能使用')

// 图片预加载相关状态
const imageLoading = ref(true)
const displayImage = ref('/src/assets/image/images.jpg') // 默认图片

// 购买状态
const purchasingId = ref<number | null>(null)

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

// 监听内容变化，触发代码高亮
watch(() => renderedContent.value, () => {
  nextTick(() => {
    setTimeout(() => {
      if ((window as any).Prism) {
        (window as any).Prism.highlightAll()
      }
    }, 100)
  })
}, { flush: 'post' })

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

  // 说明：面包屑与导航激活均基于路由配置自动判定，无需依赖 referrer

  await handleAsync(async () => {
    loading.value = true
    error.value = ''

    const postData = await PostService.getPostDetail(postId)
    post.value = postData
    console.log('postData', postData);

    // 动态更新页面标题与面包屑末项
    if (postData && route.meta) {
      route.meta.title = postData.title
      // 同步更新浏览器标题（路由守卫只会在切换时触发，这里手动更新）
      document.title = `${postData.title} - MyBlog`
    }

    // 初始化点赞和收藏状态
    currentLikeCount.value = postData.likeCount || 0
    currentFavoriteCount.value = postData.favoriteCount || 0
    isLiked.value = postData.likeStatus === 1  // 1表示已点赞
    isFavorited.value = postData.favoriteStatus === 1  // 1表示已收藏



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

// 购买资源
const onPurchase = async (resourceId: number) => {
  if (!resourceId) return
  if (!isLoggedIn()) {
    loginMessage.value = '购买资源需要登录后才能进行'
    showLoginModal.value = true
    return
  }
  await handleAsync(async () => {
    purchasingId.value = resourceId
    await PostService.purchaseResource(resourceId)
    showSuccessToast('购买成功！')
    await loadPostDetail()
  }, {
    onError: (err) => {
      console.error('购买失败:', err)
      // 业务错误已通过拦截器Toast提示，这里不再额外弹模态框
    },
    onFinally: () => {
      purchasingId.value = null
    }
  })
}

// 处理点赞
const handleLike = async () => {
  if (!post.value || liking.value) return

  // 检查登录状态
  if (!isLoggedIn()) {
    loginMessage.value = '点赞功能需要登录后才能使用'
    showLoginModal.value = true
    return
  }

  await handleAsync(async () => {
    liking.value = true

    await PostService.likePost(post.value!.id)

    // 切换本地状态
    const wasLiked = isLiked.value
    isLiked.value = !wasLiked
    currentLikeCount.value += wasLiked ? -1 : 1

    // 显示成功提示
    showSuccessToast(isLiked.value ? '点赞成功！' : '取消点赞成功！')
  }, {
    onError: (err) => {
      console.error('点赞失败:', err)
      showError('操作失败，请稍后重试')
    },
    onFinally: () => {
      liking.value = false
    }
  })
}

// 处理收藏
const handleFavorite = async () => {
  if (!post.value || favoriting.value) return

  // 检查登录状态
  if (!isLoggedIn()) {
    loginMessage.value = '收藏功能需要登录后才能使用'
    showLoginModal.value = true
    return
  }

  await handleAsync(async () => {
    favoriting.value = true

    await PostService.favoritePost(post.value!.id)

    // 切换本地状态
    const wasFavorited = isFavorited.value
    isFavorited.value = !wasFavorited
    currentFavoriteCount.value += wasFavorited ? -1 : 1

    // 显示成功提示
    showSuccessToast(isFavorited.value ? '收藏成功！' : '取消收藏成功！')
  }, {
    onError: (err) => {
      console.error('收藏失败:', err)
      showError('操作失败，请稍后重试')
    },
    onFinally: () => {
      favoriting.value = false
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



// 组件挂载时加载数据
onMounted(() => {
  loadPostDetail()
  // 加载Prism.js用于代码高亮
  loadPrism()
  // 添加点击外部区域关闭分享选项的事件监听
  document.addEventListener('click', handleClickOutside)
})

// 组件卸载时清理事件监听器
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

const interactionStore = usePostInteractionStore()
// 订阅 AiChat 触发的点赞/收藏事件以同步本地UI状态
watch(() => interactionStore.lastLikeEvent, (ev) => {
  const e = ev as any
  if (!e?.postId || !post.value || post.value.id !== e.postId) return
  const wasLiked = isLiked.value
  isLiked.value = e.isLiked
  if (isLiked.value !== wasLiked) {
    currentLikeCount.value += isLiked.value ? 1 : -1
  }
}, { deep: true })

watch(() => interactionStore.lastFavoriteEvent, (ev) => {
  const e = ev as any
  if (!e?.postId || !post.value || post.value.id !== e.postId) return
  const wasFavorited = isFavorited.value
  isFavorited.value = e.isFavorited
  if (isFavorited.value !== wasFavorited) {
    currentFavoriteCount.value += isFavorited.value ? 1 : -1
  }
}, { deep: true })
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
.table-of-contents-container {
  position: absolute;
  right: -280px;
  top: 20px;
  width: 280px;
  height: 100%;

  .table-of-contents {
    position: sticky;
    right: 20px;
    top: 90px;
    width: 280px;
    overflow-y: auto;
    float: right;
    margin-left: 20px;

  }

}

.post-detail {
  position: relative;
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
}

// 文章标题样式 - 重新设计
.post-title {
  font-size: 2.8rem;
  font-weight: 800;
  color: var(--text-title);
  margin-bottom: 24px;
  line-height: 1.1;
  letter-spacing: -0.02em;
  position: relative;
  padding-bottom: 20px;
  
  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 80px;
    height: 4px;
    background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
    border-radius: 2px;
  }
}

.post-cover {
  overflow: hidden;
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
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

// 文章元信息样式 - 重新设计
.post-meta-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32px;
  padding: 20px;
  background: var(--bg-soft);
}

.meta-left-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-name {
  font-weight: 600;
  color: var(--text-title);
  font-size: 16px;
}

.meta-right-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.meta-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-subtle);
  font-size: 14px;
  font-weight: 500;
  transition: color 0.2s ease;
  
  &:hover {
    color: var(--color-primary);
  }
}

.category-badge {
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  color: white;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.post-summary {
  margin-top: 10px;
}

.post-summary p {
  margin: 0;
  font-style: italic;
  line-height: 1.6;
}

/* 富文本内容样式 - 重新设计 */
.markdown-content {
  line-height: 1.7;
  padding: 32px;
  color: var(--text-main);
  font-size: 16px;
  word-wrap: break-word;
  background: var(--bg-main);
  border-radius: 12px;
  margin: 24px 0;

  /* 首段首字母放大 */
  & > p:first-of-type::first-letter {
    font-size: 3em;
    font-weight: 700;
    float: left;
    line-height: 1;
    margin-right: 8px;
    margin-top: 4px;
    color: var(--color-primary);
  }
}

/* 基础元素样式 */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  color: var(--text-title);
  font-weight: 600;
  margin: 24px 0 16px 0;
  line-height: 1.4;
}

.markdown-content :deep(h1) { font-size: 2em; }
.markdown-content :deep(h2) { font-size: 1.7em; }
.markdown-content :deep(h3) { font-size: 1.4em; }
.markdown-content :deep(h4) { font-size: 1.2em; }
.markdown-content :deep(h5) { font-size: 1.1em; }
.markdown-content :deep(h6) { font-size: 1em; }

.markdown-content :deep(p) {
  margin: 16px 0;
  color: var(--text-main);
}

.markdown-content :deep(a) {
  color: var(--text-link);
  text-decoration: none;
  transition: color 0.2s ease;
}

.markdown-content :deep(a:hover) {
  color: var(--color-primary-dark);
  text-decoration: underline;
}

/* 列表样式 */
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 16px 0;
  padding-left: 24px;
  color: var(--text-main);
}

.markdown-content :deep(li) {
  margin: 8px 0;
  line-height: 1.6;
}

/* 引用块样式 - 重新设计 */
.markdown-content :deep(blockquote) {
  margin: 24px 0;
  padding: 20px 24px;
  border-left: 4px solid var(--color-primary);
  background: linear-gradient(135deg, var(--bg-soft), var(--bg-hover));
  color: var(--text-subtle);
  font-style: italic;
  border-radius: 0 12px 12px 0;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  
  &::before {
    content: '"';
    position: absolute;
    top: -10px;
    left: 16px;
    font-size: 48px;
    color: var(--color-primary);
    opacity: 0.2;
    font-family: Georgia, serif;
  }
  
  p {
    margin: 0;
    position: relative;
    z-index: 1;
  }
}

.markdown-content :deep(blockquote p) {
  margin: 0;
}

/* 代码样式 - 简化版，让Prism处理高亮 */
.markdown-content :deep(code) {
  background-color: var(--bg-element);
  color: var(--text-main);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
}

/* 移除Prism token的所有样式，只保留纯文本 */
.markdown-content :deep(.token) {
  background: none !important;
  text-shadow:none !important;

}

.markdown-content :deep(pre) {
  border-radius: 12px;
  padding: 24px;
  margin: 24px 0;
  overflow-x: auto;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  position: relative;
  
  &::before {
    content: attr(data-language);
    position: absolute;
    top: 12px;
    right: 12px;
    background: var(--color-primary);
    color: white;
    padding: 4px 12px;
    border-radius: 16px;
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
}

.markdown-content :deep(pre code) {
  background: none;
  border: none;
  padding: 0;
  font-size: inherit;
}

/* 表格样式 */
.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 20px 0;
  background-color: var(--bg-card);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid var(--border-base);
  color: var(--text-main);
}

.markdown-content :deep(th) {
  background-color: var(--bg-soft);
  font-weight: 600;
  color: var(--text-title);
  border-bottom: 2px solid var(--color-primary);
}

.markdown-content :deep(tr:last-child td) {
  border-bottom: none;
}

.markdown-content :deep(tr:hover) {
  background-color: var(--bg-hover);
}

/* 分隔线样式 */
.markdown-content :deep(hr) {
  border: none;
  height: 2px;
  background: linear-gradient(to right, transparent, var(--border-base), transparent);
  margin: 32px 0;
}

/* 图片样式 */
.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  box-shadow: var(--shadow-md);
  margin: 16px 0;
  display: block;
  margin-left: auto;
  margin-right: auto;
}

/* 强调文本 */
.markdown-content :deep(strong),
.markdown-content :deep(b) {
  color: var(--text-title);
  font-weight: 600;
}

.markdown-content :deep(em),
.markdown-content :deep(i) {
  color: var(--text-subtle);
  font-style: italic;
}

/* 删除线 */
.markdown-content :deep(del),
.markdown-content :deep(s) {
  color: var(--text-muted);
  text-decoration: line-through;
}

/* 下划线 */
.markdown-content :deep(u) {
  text-decoration: underline;
  color: var(--color-accent);
}

/* 高亮文本 */
.markdown-content :deep(mark) {
  background-color: var(--bg-warning);
  color: var(--text-main);
  padding: 2px 4px;
  border-radius: 3px;
}

/* TinyMCE 特定样式适配 */
.markdown-content :deep(span.td-span) {
  color: var(--text-main);
}

.markdown-content :deep(span.md-plain) {
  color: var(--text-main);
}

.markdown-content :deep(code.box-sizing) {
  background-color: var(--bg-element);
  color: var(--color-error);
}



.dark .markdown-content :deep(pre) {
  background-color: var(--bg-soft);
}

.dark .markdown-content :deep(blockquote) {
  background-color: var(--bg-soft);
  border-left-color: var(--color-primary);
  color: var(--text-subtle);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .markdown-content {
    padding: 16px;
    font-size: 15px;
  }
  
  .markdown-content :deep(th),
  .markdown-content :deep(td) {
    padding: 8px 12px;
  }
  
  .markdown-content :deep(pre) {
    padding: 12px;
  }
}

/* 文章互动功能条样式 - 重新设计 */
.post-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 0;
  margin-top: 40px;
  border-top: 2px solid var(--border-light);
  border-bottom: 2px solid var(--border-light);
  position: sticky;
  bottom: 0;
  z-index: 10;
  background: var(--bg-main);
  backdrop-filter: blur(10px);
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
  gap: 8px;
  padding: 12px 20px;
  background: var(--bg-soft);
  border: 2px solid var(--border-light);
  border-radius: 24px;
  color: var(--text-main);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  
  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }
  
  &:active {
    transform: translateY(0);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  }
}

.action-btn:hover {
  background: var(--bg-hover);
  border-color: var(--border-main);
}

.action-btn.liked,
.action-btn.favorited {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

.action-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
}

/* 分享按钮 */
.share-group {
  position: relative;
}

.share-options {
  position: absolute;
  right: 0;
  top: 40px;
  background: var(--bg-main);
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  gap: 8px;
  padding: 8px;
}

.share-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 16px;
  background: transparent;
  border: none;
  color: var(--text-main);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s ease;
  text-align: left;
}

.share-option:hover {
  background: var(--bg-hover);
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
  background: var(--color-primary);
  color: white;
}

// 响应式样式
@include respond(md) {
  .post-title {
    font-size: 1.8rem;
  }

  .post-actions {
    flex-direction: column;
    gap: 16px;
  }

  .actions-left {
    justify-content: space-around;
    gap: 12px;
  }

  .actions-right {
    justify-content: center;
  }

  .share-options {
    right: auto;
    left: 50%;
    transform: translateX(-50%);
  }
}
</style>