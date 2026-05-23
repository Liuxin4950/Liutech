<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useHead } from '@vueuse/head'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { PostService } from '@/services/post'
import type { PostDetail } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatDate } from '@/utils/utils'
import CommentSection from '@/components/CommentSection.vue'
import { isLoggedIn } from '../utils/auth'
import LoginModal from '../components/LoginModal.vue'
import { usePostInteractionStore } from '@/stores/postInteraction'
import TableOfContents from '@/components/TableOfContents.vue'
import Icon from '@/components/Icon.vue'

// DOMPurify 安全配置：禁止危险标签和事件属性
const sanitizeConfig = {
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'input', 'textarea', 'select', 'button'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur', 'onsubmit']
}

// 动态加载Prism.js和Prism.css用于代码高亮
const addCopyButtons = () => {
  document.querySelectorAll('.markdown-content pre').forEach(pre => {
    if (pre.querySelector('.copy-btn')) return
    const btn = document.createElement('button')
    btn.className = 'copy-btn'
    btn.textContent = '复制'
    btn.addEventListener('click', () => {
      const code = pre.querySelector('code')
      const text = code?.textContent || ''
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.cssText = 'position:fixed;left:-9999px;opacity:0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      btn.textContent = '已复制'
      setTimeout(() => { btn.textContent = '复制' }, 1500)
    })
    ;(pre as HTMLElement).style.position = 'relative'
    pre.appendChild(btn)
  })
}

let prismLinkEl: HTMLLinkElement | null = null
let prismScriptEl: HTMLScriptElement | null = null
const prismLangScripts: HTMLScriptElement[] = []

const loadPrism = () => {
  // 防止重复注入
  if (prismScriptEl || (window as any).Prism) {
    if ((window as any).Prism) {
      (window as any).Prism.highlightAll()
      addCopyButtons()
    }
    return
  }

  // 直接加载完整版Prism
  const link = document.createElement('link')
  prismLinkEl = link
  link.rel = 'stylesheet'
  link.href = 'https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism.min.css'
  document.head.appendChild(link)

  const script = document.createElement('script')
  prismScriptEl = script
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
          addCopyButtons()
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
              addCopyButtons()
            }
          }
        }
        document.head.appendChild(langScript)
        prismLangScripts.push(langScript)
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

// SEO Meta — 在 setup 阶段调用 useHead，通过响应式 post 驱动更新
useHead(() => {
  const p = post.value
  if (!p) {
    return { title: '加载中... - LiuTech' }
  }
  const postUrl = `${PUBLIC_SITE_URL}/post/${p.id}`
  const imageUrl = normalizePublicUrl(p.coverImage) || `${PUBLIC_SITE_URL}/og-image.svg`
  return {
    title: `${p.title} - LiuTech`,
    meta: [
      { name: 'description', content: p.summary || p.content?.substring(0, 150) || `LiuTech 技术博客 - ${p.title}` },
      { name: 'keywords', content: p.tags?.map((t: any) => t.name).join(', ') || '技术博客, 编程' },
      { property: 'og:title', content: `${p.title} - LiuTech` },
      { property: 'og:description', content: p.summary || p.content?.substring(0, 150) || `LiuTech 技术博客 - ${p.title}` },
      { property: 'og:url', content: postUrl },
      { property: 'og:image', content: imageUrl },
      { property: 'twitter:title', content: `${p.title} - LiuTech` },
      { property: 'twitter:description', content: p.summary || p.content?.substring(0, 150) || `LiuTech 技术博客 - ${p.title}` },
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
          "headline": p.title,
          "description": p.summary || p.content?.substring(0, 150) || `LiuTech 技术博客 - ${p.title}`,
          "image": imageUrl,
          "author": {
            "@type": "Person",
            "name": p.author?.username || "LiuTech",
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
          "datePublished": p.createdAt,
          "dateModified": p.updatedAt || p.createdAt,
          "mainEntityOfPage": {
            "@type": "WebPage",
            "@id": postUrl
          }
        }, null, 2)
      }
    ]
  }
})
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
const renderedContent = computed(() => DOMPurify.sanitize(post.value?.content || '', sanitizeConfig))

// 监听内容变化，触发代码高亮
watch(() => renderedContent.value, () => {
  nextTick(() => {
    if ((window as any).Prism) {
      (window as any).Prism.highlightAll()
      addCopyButtons()
    }
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
      document.title = `${postData.title} - LiuTech`
    }

    // 初始化点赞和收藏状态
    currentLikeCount.value = postData.likeCount || 0
    currentFavoriteCount.value = postData.favoriteCount || 0
    isLiked.value = postData.likeStatus === 1  // 1表示已点赞
    isFavorited.value = postData.favoriteStatus === 1  // 1表示已收藏
  }, {
    onError: () => {
      error.value = '加载文章详情失败，请稍后重试'
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
    onError: () => {
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
    onError: () => {
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
    onError: () => {
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
    onError: () => {
      showError('操作失败，请稍后重试')
    },
    onFinally: () => {
      favoriting.value = false
    }
  })
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

// 复制链接
const copyLink = async () => {
  try {
    await navigator.clipboard.writeText(window.location.href)
    showSuccessToast('链接已复制到剪贴板！')
  } catch {
    // 备用方案
    try {
      const textArea = document.createElement('textarea')
      textArea.value = window.location.href
      document.body.appendChild(textArea)
      textArea.select()
      document.execCommand('copy')
      document.body.removeChild(textArea)
      showSuccessToast('链接已复制到剪贴板！')
    } catch {
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

// 组件卸载时清理事件监听器和注入的 Prism.js 资源
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  if (prismLinkEl?.parentNode) {
    prismLinkEl.parentNode.removeChild(prismLinkEl)
    prismLinkEl = null
  }
  if (prismScriptEl?.parentNode) {
    prismScriptEl.parentNode.removeChild(prismScriptEl)
    prismScriptEl = null
  }
  prismLangScripts.forEach(el => {
    if (el.parentNode) el.parentNode.removeChild(el)
  })
  prismLangScripts.length = 0
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
    <div v-else-if="post" class="post-reading-layout">
      <aside class="article-toc-panel" aria-label="文章目录">
        <TableOfContents class="article-toc" :collapsed-below="1680" />
      </aside>

      <div class="post-card card bg-soft">
      <!-- 文章头部信息 -->
      <header class="post-header">
        <h1 class="post-title">{{ post.title }}</h1>
        <!-- 文章摘要 -->
        <div v-if="post.summary" class="post-excerpt">
          {{ post.summary }}
        </div>

        <div class="post-meta-info">
          <div class="meta-left-section">
            <div class="author-info">
              <img v-if="post.author?.avatarUrl" :src="post.author.avatarUrl" :alt="post.author.username"
                class="author-avatar">
              <span v-else class="author-avatar-fallback">{{ (post.author?.username || 'L').slice(0, 1).toUpperCase() }}</span>
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
              {{ currentLikeCount || 0 }}
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

    
      <!-- 文章内容 -->
      <article class="post-article">
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
            <Icon name="heart" size="16" />
            <span>{{ isLiked ? '已点赞' : '点赞' }}</span>
            <span class="count">({{ currentLikeCount }})</span>
          </button>

          <!-- 收藏按钮 -->
          <button @click="handleFavorite" :class="['action-btn', { 'favorited': isFavorited }]" :disabled="favoriting">
            <Icon name="star" size="16" />
            <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
            <span class="count">({{ currentFavoriteCount }})</span>
          </button>

          <!-- 评论数 -->
          <div class="action-info">
            <Icon name="message" size="16" />
            <span>评论 ({{ post?.commentCount || 0 }})</span>
          </div>

          <!-- 阅读数 -->
          <div class="action-info">
            <Icon name="eye" size="16" />
            <span>阅读 ({{ post?.viewCount || 0 }})</span>
          </div>
        </div>

        <div class="actions-right">
          <button class="action-btn ai-action-btn" @click="summarizeWithAi">
            <Icon name="bot" size="16" />
            <span>AI 总结</span>
          </button>

          <!-- 分享按钮 -->
          <div class="share-group">
            <button @click="toggleShare" class="action-btn share-btn">
              <Icon name="share" size="16" />
              <span>分享</span>
            </button>

            <!-- 分享选项 -->
            <div v-if="showShare" class="share-options">
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
    </div>
    <div v-else class="text-center p-20 ">
      <p>文章不存在</p>
      <button @click="goBack" class="bg-primary text-center rounded transition mt-8">返回首页</button>
    </div>

    <!-- 登录弹窗 -->
    <LoginModal v-model:visible="showLoginModal" :message="loginMessage" />

  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
.post-detail {
  position: relative;
  margin: 0 auto;
  overflow: visible;
}

.post-reading-layout {
  width: 100%;
  margin: 0 auto;
  position: relative;
}

.post-card {
  min-width: 0;
  padding: 36px 40px 0;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 18px;
  box-shadow: var(--shadow-xl);
}

.article-toc-panel {
  position: sticky;
  top: 96px;
  width: 220px;
  height: 0;
  margin-left: -244px;
  max-height: calc(100vh - 130px);
  z-index: 12;
}

:deep(.article-toc) {
  position: static;
  width: 100%;
  max-height: calc(100vh - 130px);
  border-radius: 14px;
  border: 1px solid var(--border-soft);
  background: var(--surface-glass);
  box-shadow: var(--shadow-xl);
  backdrop-filter: blur(14px);
}

:deep(.article-toc .toc-header) {
  background: linear-gradient(180deg, var(--state-primary-bg), transparent);
  border-bottom-color: var(--border-soft);
}

:deep(.article-toc .toc-link) {
  font-size: 12.5px;
}

.post-header {
  margin-bottom: 32px;
}

.post-title {
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 750;
  color: var(--text-title);
  line-height: 1.16;
  letter-spacing: 0;
  position: relative;
}

.post-cover {
  overflow: hidden;
}

.post-excerpt {
  color: var(--text-subtle);
  font-size: 1rem;
  line-height: 1.7;
  margin: 16px 0 24px;
  padding: 0;
  opacity: 0.85;
}

.cover-image {
  width: 100%;
  height: auto;
  max-height: 400px;
  object-fit: cover;
  display: block;
  border-radius: 8px;
}

.author-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid rgba(var(--color-primary-rgb), 0.35);
}

.author-avatar-fallback {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.12), rgba(var(--color-primary-rgb), 0.04));
  color: var(--color-primary);
  border: 1px solid rgba(var(--color-primary-rgb), 0.18);
  font-size: 14px;
  font-weight: 700;
}

.post-meta-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 22px;
  padding: 14px 0;
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  background: transparent;
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
  font-size: 14px;
}

.meta-right-section {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.meta-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-subtle);
  font-size: 13px;
}

.category-badge {
  background: rgba(var(--color-primary-rgb), 0.1);
  color: var(--color-primary);
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background: var(--color-primary);
    color: white;
  }
}


/* 标签云样式优化 */
.tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--bg-soft);
  border: 1px solid transparent;
  color: var(--text-subtle);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;

  &::before {
    content: '#';
    color: var(--color-primary);
    font-weight: 700;
  }

  &:hover {
    border-color: rgba(var(--color-primary-rgb), 0.22);
    color: var(--color-primary);
    background: rgba(var(--color-primary-rgb), 0.08);
  }
}



/* 富文本内容样式 - 重新设计 */
.markdown-content {
  line-height: 1.85;
  padding: 10px 0 20px;
  color: var(--text-main);
  font-size: 16.5px;
  word-wrap: break-word;
  background: transparent;
  border-radius: 0;
  margin: 0;
}

/* 基础元素样式 */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  color: var(--text-title);
  font-weight: 720;
  margin: 34px 0 16px;
  line-height: 1.35;
  scroll-margin-top: 96px;
}

.markdown-content :deep(h1) { font-size: 2em; }
.markdown-content :deep(h2) { font-size: 1.58em; }
.markdown-content :deep(h3) { font-size: 1.28em; }
.markdown-content :deep(h4) { font-size: 1.2em; }
.markdown-content :deep(h5) { font-size: 1.1em; }
.markdown-content :deep(h6) { font-size: 1em; }

.markdown-content :deep(p) {
  margin: 15px 0;
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
  margin: 28px 0;
  padding: 18px 22px 18px 28px;
  border: 1px solid var(--border-soft);
  background:
    linear-gradient(135deg, var(--surface-glass-muted), transparent),
    var(--bg-card);
  color: var(--text-main);
  font-style: normal;
  border-radius: 12px;
  position: relative;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.28);
  
  &::before {
    content: "";
    position: absolute;
    left: 13px;
    top: 20px;
    width: 5px;
    height: 5px;
    border-radius: 999px;
    background: var(--color-accent);
    box-shadow: 0 0 0 4px rgba(240, 184, 192, 0.16);
  }
}

.markdown-content :deep(blockquote p) {
  margin: 0;
  color: var(--text-main);
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
  color: inherit !important;
}

.markdown-content :deep(pre) {
  background: var(--bg-code) !important;
  color: var(--text-code) !important;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 12px;
  padding: 24px;
  margin: 24px 0;
  overflow-x: auto;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  position: relative;
}

.markdown-content :deep(pre code) {
  background: none;
  border: none;
  padding: 0;
  font-size: inherit;
  color: inherit !important;
  white-space: pre;
}

.markdown-content :deep(pre code *) {
  color: inherit !important;
}

/* 代码块复制按钮 */
.markdown-content :deep(.copy-btn) {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 4px 12px;
  font-size: 12px;
  color: var(--text-subtle);
  background: var(--bg-hover);
 border: 1px solid var(--border-light);
  border-radius: 6px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 1;
}

.markdown-content :deep(pre:hover .copy-btn) {
  opacity: 1;
}

.markdown-content :deep(.copy-btn:hover) {
  background: var(--bg-active);
  color: var(--color-primary);
  border-color: var(--color-primary);
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



:root.dark .markdown-content :deep(pre) {
  border-color: rgba(148, 163, 184, 0.3);
}

:root.dark .post-card {
  box-shadow: var(--shadow-xl);
}

:root.dark :deep(.article-toc) {
  background: var(--surface-glass);
  border-color: var(--border-soft);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.22);
}

:root.dark .markdown-content :deep(blockquote) {
  background:
    linear-gradient(135deg, var(--surface-glass-muted), transparent),
    var(--bg-card);
  border-color: var(--border-soft);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

:root.dark .post-actions {
  background: rgba(32, 33, 36, 0.92);
  box-shadow: 0 -14px 38px rgba(0, 0, 0, 0.24);
}

/* 响应式设计 */
@media (max-width: 1680px) {
  .post-reading-layout {
    width: 100%;
  }

  .article-toc-panel {
    position: sticky;
    top: 88px;
    right: auto;
    bottom: auto;
    left: auto;
    transform: none;
    height: 0;
    margin-left: calc((1200px - 100vw) / 2 - 20px);
    width: auto;
    max-width: 100vw;
    max-height: min(520px, calc(100vh - 140px));
    z-index: 30;
  }

  :deep(.article-toc) {
    position: static !important;
    inset: auto !important;
    width: min(280px, 100vw);
    max-height: min(520px, calc(100vh - 140px));
  }

  :deep(.article-toc:not(.visible)) {
    width: 46px;
    height: 104px;
    border-radius: 16px;
    border-color: var(--state-primary-border);
    overflow: hidden;
    background: var(--surface-glass);
    box-shadow: var(--shadow-xl);
    cursor: pointer;
  }

  :deep(.article-toc:not(.visible) .toc-header) {
    width: 46px;
    height: 104px;
    flex-direction: column;
    gap: 8px;
    justify-content: center;
    align-items: center;
    padding: 0;
    border-bottom-color: transparent;
    cursor: pointer;
  }

  :deep(.article-toc:not(.visible) .toc-header h4) {
    display: block;
    writing-mode: vertical-rl;
    font-size: 12px;
    font-weight: 700;
    line-height: 1;
    letter-spacing: 0;
    color: var(--color-primary);
  }

  :deep(.article-toc:not(.visible) .toggle-btn) {
    width: 28px;
    height: 22px;
    padding: 0;
    color: var(--color-primary);
    background: var(--state-primary-bg);
  }

  :deep(.article-toc:not(.visible) .toggle-btn:hover) {
    background: var(--state-primary-bg-hover);
    color: var(--color-primary);
  }
}

@media (max-width: 1200px) {
  .article-toc-panel {
    margin-left: -20px;
  }
}

@media (max-width: 768px) {
  .post-reading-layout {
    width: 100%;
  }

  .post-card {
    padding: 24px 22px 0;
    border-radius: 14px;
  }

  .article-toc-panel {
    position: sticky;
    top: 72px;
    right: auto;
    bottom: auto;
    left: auto;
    transform: none;
    width: auto;
    margin-left: -20px;
    max-width: 100vw;
  }

  :deep(.article-toc) {
    width: min(280px, 100vw);
  }

  .markdown-content {
    padding: 22px 0;
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
  gap: 16px;
  padding: 12px 14px;
  margin: 10px -18px 0;
  border: 1px solid var(--border-light);
  background-color: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  border-radius: 18px 18px 0 0;
  position: sticky;
  bottom: 0;
  z-index: 5;
  box-shadow: 0 -14px 38px rgba(15, 23, 42, 0.08);
}

.actions-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.actions-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 999px;
  color: var(--text-main);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  box-shadow: none;
  
  &:hover {
    background: rgba(var(--color-primary-rgb), 0.08);
    border-color: rgba(var(--color-primary-rgb), 0.28);
    color: var(--color-primary);
  }
  
  &:active {
    transform: translateY(0);
  }
}

.action-btn:hover {
  background: rgba(var(--color-primary-rgb), 0.08);
  border-color: rgba(var(--color-primary-rgb), 0.28);
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
  padding: 9px 8px;
  font-size: 13px;
}

.ai-action-btn {
  background: linear-gradient(135deg, rgba(var(--color-primary-rgb), 0.12), rgba(var(--color-primary-rgb), 0.05));
  border-color: rgba(var(--color-primary-rgb), 0.22);
  color: var(--color-primary);

  &:hover {
    background: var(--color-primary);
    color: white;
    border-color: var(--color-primary);
  }
}

/* 分享按钮 */
.share-group {
  position: relative;
}

.share-options {
  position: absolute;
  right: 0;
  bottom: 56px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  box-shadow: var(--shadow-modal);
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  min-width: 160px;
  z-index: 100;
  animation: slideUp 0.2s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(8px);
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
    gap: 8px 12px;
    justify-content: flex-start;
    width: 100%;
  }

  .meta-stat {
    font-size: 12px;
  }

  .category-badge {
    font-size: 11px;
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
    margin-left: -8px;
    margin-right: -8px;
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
    gap: 8px;
  }

  .share-btn,
  .ai-action-btn {
    flex: 1;
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
    padding: 18px 0;
    font-size: 14px;
  }
}
</style>
