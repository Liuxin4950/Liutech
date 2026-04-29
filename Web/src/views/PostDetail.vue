<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useHead } from '@vueuse/head'
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
import Icon from '@/components/Icon.vue'

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
const PUBLIC_SITE_URL = 'https://liuxin.chat'

const normalizePublicUrl = (url?: string | null) => {
  if (!url) return ''

  return url
    .replace(/^http:\/\/liuxin\.chat/i, PUBLIC_SITE_URL)
    .replace(/^http:\/\/liutech\.chat/i, PUBLIC_SITE_URL)
    .replace(/^https:\/\/liutech\.chat/i, PUBLIC_SITE_URL)
}

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

// 下载状态
const downloadingId = ref<number | null>(null)

// 计算属性：附件分组
const fileAttachments = computed(() => {
  return post.value?.attachments?.filter(a => a.resourceType === 'file') || []
})

const linkAttachments = computed(() => {
  return post.value?.attachments?.filter(a => a.resourceType === 'link') || []
})

// 计算属性：渲染富文本内容
const renderedContent = computed(() => {
  if (!post.value?.content) return ''

  let content = post.value.content

  // 检查是否包含HTML标签
  const hasHtmlTags = /<[^>]*>/g.test(content)

  if (hasHtmlTags) {
    // TinyMCE生成的HTML内容中，可能包含Markdown语法（如 **bold**）
    // 需要将这些Markdown语法转换为HTML
    // 注意：只转换不在HTML标签内的文本
    content = content
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/`(.*?)`/g, '<code>$1</code>')
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

    // 动态更新页面标题与面包屑末项
    if (postData && route.meta) {
      route.meta.title = postData.title
      // 同步更新浏览器标题（路由守卫只会在切换时触发，这里手动更新）
      document.title = `${postData.title} - LiuTech`

    // 设置 SEO Meta 信息
    if (postData) {
      const postUrl = `${PUBLIC_SITE_URL}/post/${postData.id}`
      const imageUrl = normalizePublicUrl(postData.coverImage) || `${PUBLIC_SITE_URL}/og-image.svg`
      
      useHead({
        title: `${postData.title} - LiuTech`,
        meta: [
          { name: 'description', content: postData.summary || postData.content?.substring(0, 150) || `LiuTech 技术博客 - ${postData.title}` },
          { name: 'keywords', content: postData.tags?.map((t: any) => t.name).join(', ') || '技术博客, 编程' },
          { property: 'og:title', content: `${postData.title} - LiuTech` },
          { property: 'og:description', content: postData.summary || postData.content?.substring(0, 150) || `LiuTech 技术博客 - ${postData.title}` },
          { property: 'og:url', content: postUrl },
          { property: 'og:image', content: imageUrl },
          { property: 'twitter:title', content: `${postData.title} - LiuTech` },
          { property: 'twitter:description', content: postData.summary || postData.content?.substring(0, 150) || `LiuTech 技术博客 - ${postData.title}` },
          { property: 'twitter:image', content: imageUrl }
        ],
        link: [
          { rel: 'canonical', href: postUrl }
        ],
        script: [
          {
            type: 'application/ld+json',
            children: JSON.stringify({
              "@context": "https://schema.org",
              "@type": "Article",
              "headline": postData.title,
              "description": postData.summary || postData.content?.substring(0, 150) || `LiuTech 技术博客 - ${postData.title}`,
              "image": imageUrl,
              "author": {
                "@type": "Person",
                "name": postData.author?.username || "LiuTech",
                "url": `${PUBLIC_SITE_URL}/`
              },
              "publisher": {
                "@type": "Organization",
                "name": "LiuTech",
                "logo": {
                  "@type": "ImageObject",
                  "url": `${PUBLIC_SITE_URL}/logo.svg`
                }
              },
              "datePublished": postData.createdAt,
              "dateModified": postData.updatedAt || postData.createdAt,
              "mainEntityOfPage": {
                "@type": "WebPage",
                "@id": `${PUBLIC_SITE_URL}/post/${postData.id}`
              }
            }, null, 2)
          }
        ]
      })
    }
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

// 处理附件下载（通过后端验证）
const handleDownload = async (resourceId: number, fileName: string) => {
  if (!resourceId) return

  // 检查登录状态
  if (!isLoggedIn()) {
    loginMessage.value = '下载资源需要登录后才能进行'
    showLoginModal.value = true
    return
  }

  await handleAsync(async () => {
    downloadingId.value = resourceId
    await PostService.downloadResource(resourceId, fileName)
    showSuccessToast('下载成功！')
  }, {
    onError: (err) => {
      console.error('下载失败:', err)
      // 业务错误已通过拦截器Toast提示，这里不再额外弹模态框
    },
    onFinally: () => {
      downloadingId.value = null
    }
  })
}

// 打开外部链接（在新窗口打开，防止绕过后端验证）
const openExternalLink = (url: string) => {
  if (!url) return

  // 在新窗口打开外部链接
  window.open(url, '_blank', 'noopener,noreferrer')
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

// 跳转到分类详情
const handleCategoryClick = (categoryId: number) => {
  router.push(`/category-detail/${categoryId}`)
}

// 跳转到标签详情
const handleTagClick = (tagId: number) => {
  router.push(`/tags/${tagId}`)
}

const summarizeWithAi = () => {
  if (!post.value) return

  window.dispatchEvent(new CustomEvent('ai-chat-open', {
    detail: {
      prompt: `请结合当前文章内容，帮我做一个结构化总结：先用 3 到 5 条概括核心观点，再补充关键技术点、适用场景和阅读建议。`,
      autoSend: true
    }
  }))
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



// 监听路由参数变化，重新加载文章详情
watch(() => route.params.id, () => {
  loadPostDetail()
})

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

<template>
  <div class="post-detail content">
    <div v-if="loading" class="text-center p-20 text-sm">
      <p>加载中...</p>
    </div>
    <div v-else-if="error" class="text-center p-20 text-sm">
      <p>{{ error }}</p>
      <button @click="loadPostDetail" class="retry-btn bg-primary text-center rounded transition mt-8">重试</button>
    </div>
    <div v-else-if="post" class="card bg-soft">
      <!-- 文章头部信息 -->
      <header class="post-header">
        <h2 class="post-title">{{ post.title }}</h2>

        <!-- 封面图片 -->
        <!-- <div v-if="displayImage" class="post-cover mb-24">
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
            <span v-if="post.category" class="category-badge" @click="handleCategoryClick(post.category.id)">{{ post.category.name }}</span>
            <div class="meta-stat">
              <Icon name="calendar" size="14" />
              {{ formatDate(post.createdAt) }}
            </div>
            <div class="meta-stat">
              <Icon name="eye" size="14" />
              {{ post.viewCount || 0 }}
            </div>
            <div class="meta-stat">
              <Icon name="heart" size="14" />
              {{ post.likeCount || 0 }}
            </div>
            <div class="meta-stat">
              <Icon name="message" size="14" />
              {{ post.commentCount }}
            </div>
          </div>
        </div>
        <div v-if="post.tags && post.tags.length > 0" class="tags-cloud">
          <span v-for="tag in post.tags" :key="tag.id" class="tag" @click="handleTagClick(tag.id)">
            {{ tag.name }}
          </span>
        </div>
      </header>

      <!-- 文章摘要 -->
      <div v-if="post.summary" class="post-summary">
        <p>{{ post.summary }}</p>
      </div>

      <div class="post-ai-actions">
        <button class="ai-summary-btn" @click="summarizeWithAi">
          <Icon name="message" size="16" />
          AI 总结这篇文章
        </button>
      </div>

      <!-- 文章内容 -->
      <article class="">
        <div class="markdown-content" v-html="renderedContent"></div>
      </article>

      <!-- 附件列表 -->
      <section v-if="post.attachments && post.attachments.length" class="attachment-section">

        <!-- 本地文件 -->
        <div v-if="fileAttachments.length" class="attachment-group">
          <div class="attachment-group-title">
            <Icon name="file" size="16" />
            本地文件
            <span class="group-count">{{ fileAttachments.length }}</span>
          </div>
          <div class="attachment-list">
            <div v-for="att in fileAttachments" :key="att.attachmentId" class="attachment-item">
              <div class="item-icon">
                <Icon name="file" size="18" />
              </div>
              <div class="item-info">
                <span class="item-name" :title="att.fileName">{{ att.fileName }}</span>
                <span class="item-sub">{{ formatDate(att.createdTime) }}</span>
              </div>
              <div class="item-status">
                <span v-if="att.purchased" class="status-badge success">已获取</span>
                <span v-else-if="att.pointsNeeded" class="status-badge warning">{{ att.pointsNeeded }}积分</span>
                <span v-else class="status-badge info">免费</span>
              </div>
              <div class="item-action">
                <template v-if="att.purchased">
                  <button
                    class="btn-primary"
                    :disabled="downloadingId === att.resourceId"
                    @click="handleDownload(att.resourceId, att.fileName)"
                  >
                    <Icon v-if="downloadingId === att.resourceId" name="loader" class="animate-spin" size="14" />
                    <Icon v-else name="download" size="14" />
                    {{ downloadingId === att.resourceId ? '下载中...' : '下载' }}
                  </button>
                </template>
                <template v-else>
                  <button
                    class="btn-outline"
                    :disabled="purchasingId === att.resourceId"
                    @click="onPurchase(att.resourceId)"
                  >
                    <Icon v-if="purchasingId === att.resourceId" name="loader" class="animate-spin" size="14" />
                    <Icon v-else name="lock" size="14" />
                    {{ purchasingId === att.resourceId ? '处理中...' : (att.pointsNeeded ? `${att.pointsNeeded}积分` : '免费') }}
                  </button>
                </template>
              </div>
            </div>
          </div>
        </div>

        <!-- 外部链接 -->
        <div v-if="linkAttachments.length" class="attachment-group">
          <div class="attachment-group-title">
            <Icon name="link" size="16" />
            外部链接
            <span class="group-count">{{ linkAttachments.length }}</span>
          </div>
          <div class="attachment-list">
            <div v-for="att in linkAttachments" :key="att.attachmentId" class="attachment-item">
              <div class="item-icon">
                <Icon name="link" size="18" />
              </div>
              <div class="item-info">
                <span class="item-name" :title="att.fileName">{{ att.fileName }}</span>
                <span v-if="att.purchasedNote" class="item-note">密码：{{ att.purchasedNote }}</span>
              </div>
              <div class="item-status">
                <span v-if="att.purchased" class="status-badge success">已获取</span>
                <span v-else-if="att.pointsNeeded" class="status-badge warning">{{ att.pointsNeeded }}积分</span>
                <span v-else class="status-badge info">免费</span>
              </div>
              <div class="item-action">
                <template v-if="att.purchased && att.externalLink">
                  <button class="btn-primary" @click="openExternalLink(att.externalLink)">
                    <Icon name="external" size="14" />
                    访问
                  </button>
                </template>
                <template v-else>
                  <button
                    class="btn-outline"
                    :disabled="purchasingId === att.resourceId"
                    @click="onPurchase(att.resourceId)"
                  >
                    <Icon v-if="purchasingId === att.resourceId" name="loader" class="animate-spin" size="14" />
                    <Icon v-else name="lock" size="14" />
                    {{ purchasingId === att.resourceId ? '处理中...' : (att.pointsNeeded ? `${att.pointsNeeded}积分` : '免费') }}
                  </button>
                </template>
              </div>
            </div>
          </div>
        </div>

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
              <button @click="shareToWeChat" class="share-option wechat" title="分享到微信">
                <Icon name="wechat" size="20" />
                <span>微信</span>
              </button>

              <button @click="shareToQQ" class="share-option qq" title="分享到QQ">
                <Icon name="qq" size="20" />
                <span>QQ</span>
              </button>

              <button @click="copyLink" class="share-option link" title="复制链接">
                <Icon name="link" size="20" />
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
    <!-- <div class="table-of-contents-container">
      <TableOfContents class="table-of-contents" v-if="post && !loading && !error" />
    </div> -->
  </div>
</template>

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
  background: var(--color-primary);
  color: white;
  border: none;
  cursor: pointer;
}

.retry-btn:hover {
  background: var(--color-primary-dark) !important;
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
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(var(--color-primary-rgb), 0.3);
    opacity: 0.9;
  }
}

.post-summary {
  margin: 24px 20px;
  padding: 24px;
  background: linear-gradient(to right, var(--bg-soft), var(--bg-card));
  border-left: 4px solid var(--color-primary);
  border-radius: 8px;
  color: var(--text-subtle);
  font-size: 1.05rem;
  line-height: 1.8;
  position: relative;
  
  &::before {
    content: '"';
    position: absolute;
    top: 10px;
    left: 10px;
    font-size: 40px;
    color: var(--color-primary);
    opacity: 0.1;
    font-family: Georgia, serif;
    line-height: 1;
  }
}

.post-summary p {
  margin: 0;
  position: relative;
  z-index: 1;
}

.post-ai-actions {
  display: flex;
  justify-content: flex-start;
  margin: 16px 20px 0;
}

.ai-summary-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(var(--color-primary-rgb), 0.24);
  background: linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.12), rgba(255, 255, 255, 0.92));
  color: var(--text-title);
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ai-summary-btn:hover {
  transform: translateY(-1px);
  border-color: rgba(var(--color-primary-rgb), 0.4);
  box-shadow: 0 10px 24px rgba(var(--color-primary-rgb), 0.12);
}

/* 标签云样式优化 */
.tags-cloud {
  padding: 0 20px;
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

/* 文章互动功能条样式*/
.post-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding:12px 24px;
  /* 使用负 margin 抵消父元素的 padding */
  margin-left: -20px;
  margin-right: -20px;
  border-top: 2px solid var(--border-light);
  border-bottom: 2px solid var(--border-light);
  background-color: var(--bg-card);
  position: sticky;
  bottom: 0;
  z-index: 0;
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
  top: 52px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  min-width: 160px;
  z-index: 100;
  animation: slideDown 0.2s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.share-option {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 16px;
  background: transparent;
  border: none;
  border-radius: 8px;
  color: var(--text-main);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;

  &:deep(svg) {
    flex-shrink: 0;
  }
}

.share-option:hover {
  background: var(--bg-soft);
  transform: translateX(4px);
}

.share-option.wechat:hover {
  background: linear-gradient(135deg, #07c160, #05a850);
  color: white;

  &:deep(svg) {
    color: white;
  }
}

.share-option.qq:hover {
  background: linear-gradient(135deg, #12b7f5, #0e9dd8);
  color: white;

  &:deep(svg) {
    color: white;
  }
}

.share-option.link:hover {
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  color: white;

  &:deep(svg) {
    color: white;
  }
}

/* 响应式样式 */
@include respond(md) {
  .post-title {
    font-size: 1.8rem;
    padding-bottom: 16px;

    &::after {
      width: 60px;
      height: 3px;
    }
  }

  // 文章元信息 - 移动端适配
  .post-meta-info {
    flex-direction: column;
    gap: 16px;
    padding: 16px;
    margin-bottom: 24px;
  }

  .meta-left-section {
    width: 100%;
    justify-content: flex-start;
  }

  .meta-right-section {
    flex-wrap: wrap;
    gap: 12px;
    justify-content: center;
    width: 100%;
  }

  .meta-stat {
    font-size: 12px;
    gap: 4px;

    svg {
      width: 14px;
      height: 14px;
    }
  }

  .category-badge {
    font-size: 11px;
    padding: 5px 12px;
  }

  // 标签云 - 移动端适配
  .tags-cloud {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 0;
  }

  .tag {
    font-size: 12px;
    padding: 4px 10px;
  }

  // 文章摘要 - 移动端适配
  .post-summary {
    margin: 16px 0;
    padding: 16px;
    font-size: 0.95rem;
  }

  // 隐藏目录导航
  .table-of-contents-container {
    display: none;
  }

  // 附件列表 - 移动端适配
  .action-btn {
    padding: 10px 16px;
    font-size: 13px;
  }

  // 互动功能条 - 移动端适配
  .post-actions {
    flex-direction: column;
    gap: 12px;
    padding: 16px;
    margin-left: 0;
    margin-right: 0;
  }

  .actions-left {
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
    width: 100%;
  }

  .actions-left .action-btn {
    flex: 1;
    min-width: calc(50% - 8px);
    justify-content: center;
    padding: 10px 12px;
    font-size: 12px;

    span:not(.count) {
      display: none;
    }

    .count {
      display: inline;
    }
  }

  .actions-left .action-info {
    flex: 1;
    min-width: calc(50% - 8px);
    justify-content: center;
    padding: 10px 12px;
    font-size: 12px;
  }

  .actions-right {
    width: 100%;
    justify-content: center;
  }

  .share-btn {
    width: 100%;
    justify-content: center;
  }

  .share-options {
    right: auto;
    left: 50%;
    transform: translateX(-50%);
    width: 90%;
    max-width: 280px;
    flex-direction: column;
    animation: slideDownMobile 0.2s ease-out;
  }

  @keyframes slideDownMobile {
    from {
      opacity: 0;
      transform: translateX(-50%) translateY(-8px);
    }
    to {
      opacity: 1;
      transform: translateX(-50%) translateY(0);
    }
  }

  .share-option {
    justify-content: flex-start;
    padding: 14px 16px;
  }
}

/* 附件分组样式 */
.attachment-section {
  margin: 40px 32px;
}

.attachment-group {
  margin-bottom: 28px;

  &:last-child {
    margin-bottom: 0;
  }
}

.attachment-group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-subtle);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-light);
}

.group-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--bg-soft);
  padding: 1px 7px;
  border-radius: 10px;
  margin-left: 2px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--border-light);
  transition: all 0.2s ease;

  &:hover {
    border-color: var(--color-primary);
    background: var(--bg-hover);
  }
}

.item-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  background: var(--bg-soft);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
}

.item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-title);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-sub {
  font-size: 12px;
  color: var(--text-muted);
}

.item-note {
  font-size: 12px;
  color: var(--text-subtle);
  font-style: italic;
}

.item-status {
  flex-shrink: 0;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;

  &.success {
    background: var(--bg-success);
    color: var(--color-success);
  }

  &.warning {
    background: var(--bg-warning);
    color: var(--color-warning);
  }

  &.info {
    background: var(--bg-info);
    color: var(--color-info);
  }
}

.item-action {
  flex-shrink: 0;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: var(--color-primary);
  color: white;
  border: 1px solid var(--color-primary);
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: var(--color-primary-dark);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--border-base);
  color: var(--text-main);
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: var(--color-primary);
    color: var(--color-primary);
    background: var(--bg-soft);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@include respond(md) {
  .attachment-section {
    margin: 24px 16px;
  }

  .attachment-item {
    padding: 10px 12px;
  }

  .item-sub {
    display: none;
  }

  .item-status {
    display: none;
  }
}

// 超小屏幕（小于 480px）
@include respond(sm) {
  .post-title {
    font-size: 1.5rem;
  }

  .author-avatar {
    width: 32px;
    height: 32px;
  }

  .author-name {
    font-size: 14px;
  }

  .markdown-content {
    padding: 12px;
    font-size: 14px;

    & > p:first-of-type::first-letter {
      font-size: 2em;
    }
  }

  .markdown-content :deep(h1) { font-size: 1.6em; }
  .markdown-content :deep(h2) { font-size: 1.4em; }
  .markdown-content :deep(h3) { font-size: 1.2em; }
  .markdown-content :deep(h4) { font-size: 1.1em; }

  .markdown-content :deep(pre) {
    padding: 12px;
    font-size: 12px;
    overflow-x: auto;
  }

  .markdown-content :deep(img) {
    max-width: 100%;
    height: auto;
    border-radius: 6px;
  }

  .markdown-content :deep(table) {
    font-size: 12px;
    display: block;
    overflow-x: auto;
    white-space: nowrap;
  }

  .markdown-content :deep(th),
  .markdown-content :deep(td) {
    padding: 8px 10px;
  }
}
</style>
