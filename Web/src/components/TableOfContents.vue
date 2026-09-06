<template>
  <div
    v-if="headings.length > 0 || $slots.series"
    class="table-of-contents"
    :class="{
      visible: isVisible,
      'table-of-contents--embedded': embedded
    }"
  >
    <div class="toc-header" @click="toggleVisibility">
      <h4>阅读导航</h4>
      <button @click.stop="toggleVisibility" class="toggle-btn" :aria-expanded="isVisible" aria-label="切换目录">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M6 9l6 6 6-6"/>
        </svg>
      </button>
    </div>
    <div v-if="isVisible && $slots.series && headings.length" class="toc-tabs">
      <button :aria-pressed="activePanel === 'article'" @click="activePanel = 'article'">本文目录</button>
      <button :aria-pressed="activePanel === 'series'" @click="activePanel = 'series'">所属系列</button>
    </div>
    <!-- :key 强制重建：折叠（display:none）时初始化的嵌套 Lenis 尺寸为 0，展开后需重建才能滚动 -->
    <nav ref="tocNavRef" class="toc-nav" v-show="isVisible" :key="isVisible ? 'open' : 'closed'" data-lenis-prevent>
      <ul class="toc-list" :class="{ 'toc-panel-hidden': activePanel !== 'article' && !!$slots.series }">
        <li 
          v-for="heading in headings" 
          :key="heading.id"
          :class="['toc-item', `toc-level-${heading.level}`, { 'active': activeId === heading.id }]"
        >
          <a 
            :href="`#${heading.id}`" 
            @click.prevent="scrollToHeading(heading.id)"
            class="toc-link"
          >
            {{ heading.text }}
          </a>
        </li>
      </ul>
      <div :class="{ 'toc-panel-hidden': activePanel !== 'series' && headings.length > 0 }"><slot name="series" /></div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useNestedLenis, getLenis } from '@/composables/useLenis'

const props = withDefaults(defineProps<{
  collapsedBelow?: number
  embedded?: boolean
}>(), {
  collapsedBelow: 0,
  embedded: false
})

interface Heading {
  id: string
  text: string
  level: number
  element: HTMLElement
}

const tocNavRef = ref<HTMLElement | null>(null)

const headings = ref<Heading[]>([])
const activeId = ref<string>('')
const activePanel = ref<'article' | 'series'>('article')
const isVisible = ref(true)
const isScrolling = ref(false)
let userToggledVisibility = false

const normalizeHeadingText = (text: string) => text.replace(/\s+/g, ' ').trim()

const buildHeadingId = (text: string, index: number) => {
  const safeText = text
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+|-+$/g, '')

  return safeText || `heading-${index}`
}

const shouldIncludeHeading = (element: Element, text: string, index: number) => {
  const postTitle = normalizeHeadingText(document.querySelector('.post-title')?.textContent || '')
  const normalizedText = normalizeHeadingText(text)

  if (!normalizedText) return false
  if (index === 0 && postTitle && normalizedText === postTitle) return false
  if (element.closest('pre, code, table, blockquote')) return false

  const rect = element.getBoundingClientRect()
  return rect.width > 0 && rect.height > 0
}

const smoothScrollTo = (targetY: number) => {
  const startY = window.pageYOffset || document.documentElement.scrollTop
  const distance = targetY - startY
  const duration = Math.min(760, Math.max(420, Math.abs(distance) * 0.35))
  const startTime = performance.now()
  const easeInOutCubic = (t: number) => t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2

  isScrolling.value = true

  const animate = (now: number) => {
    const progress = Math.min((now - startTime) / duration, 1)
    window.scrollTo(0, startY + distance * easeInOutCubic(progress))

    if (progress < 1) {
      requestAnimationFrame(animate)
    } else {
      isScrolling.value = false
      // 动画完成后更新高亮状态
      handleScroll()
    }
  }

  requestAnimationFrame(animate)
}

// 提取标题
const extractHeadings = () => {
  const contentElement = document.querySelector('.rich-content')
  if (!contentElement) return

  const headingElements = contentElement.querySelectorAll('h1, h2, h3, h4, h5, h6')
  const extractedHeadings: Heading[] = []

  headingElements.forEach((element, index) => {
    const tagName = element.tagName.toLowerCase()
    const level = parseInt(tagName.charAt(1))
    const text = normalizeHeadingText(element.textContent || '')
    
    if (shouldIncludeHeading(element, text, index)) {
      // 生成唯一ID
      let id = element.id || buildHeadingId(text, index)
      
      // 确保ID唯一
      let counter = 1
      let originalId = id
      while (document.getElementById(id) && document.getElementById(id) !== element) {
        id = `${originalId}-${counter}`
        counter++
      }
      
      // 设置元素ID
      element.id = id
      
      extractedHeadings.push({
        id,
        text,
        level,
        element: element as HTMLElement
      })
    }
  })

  // 动态归一化级别：以文章中实际出现的最小标题级别作为顶级（level 1），
  // 避免没有 h1 的文章在目录顶部留出过多空白缩进
  const minLevel = extractedHeadings.reduce((min, h) => Math.min(min, h.level), 6)
  if (minLevel > 1) {
    const offset = minLevel - 1
    extractedHeadings.forEach(h => {
      h.level = h.level - offset
    })
  }

  headings.value = extractedHeadings
}

// 滚动偏移量，与固定头部高度一致
const SCROLL_OFFSET = 80

// 滚动到指定标题
const scrollToHeading = (id: string) => {
  const element = document.getElementById(id)
  if (element) {
    const elementPosition = element.getBoundingClientRect().top + window.pageYOffset
    const offsetPosition = elementPosition - SCROLL_OFFSET

    // 优先用 Lenis 的 scrollTo：与主实例协调、内置缓动曲线；未启用 Lenis 时回退自写动画
    const lenis = getLenis()
    if (lenis) {
      isScrolling.value = true
      lenis.scrollTo(offsetPosition, {
        duration: 0.8,
        easing: (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
        onComplete: () => {
          isScrolling.value = false
          handleScroll()
        }
      })
    } else {
      smoothScrollTo(offsetPosition)
    }
    activeId.value = id
  }
}

// 切换目录可见性
const toggleVisibility = () => {
  userToggledVisibility = true
  isVisible.value = !isVisible.value
}

const syncResponsiveVisibility = () => {
  if (!props.collapsedBelow || userToggledVisibility) return

  isVisible.value = window.innerWidth > props.collapsedBelow
}

// 监听滚动，高亮当前标题
const handleScroll = () => {
  // 滚动动画期间不更新高亮
  if (isScrolling.value) return
  if (headings.value.length === 0) return

  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  let newActiveId = headings.value[0]?.id || ''

  // 找到当前可见的标题：最后一个顶部在视口上方的标题
  for (let i = 0; i < headings.value.length; i++) {
    const heading = headings.value[i]
    const elementTop = heading.element.getBoundingClientRect().top + scrollTop

    // 标题顶部在视口上方（考虑偏移量）
    if (elementTop - SCROLL_OFFSET <= scrollTop) {
      newActiveId = heading.id
    } else {
      // 标题在视口下方，停止查找
      break
    }
  }

  // 只有当找到不同的标题时才更新
  if (activeId.value !== newActiveId) {
    activeId.value = newActiveId
  }
}

// 监听内容变化，重新提取标题
const observeContentChanges = () => {
  const contentElement = document.querySelector('.rich-content')
  if (!contentElement) {
    // 如果内容元素还没有渲染，延迟重试
    setTimeout(() => {
      observeContentChanges()
    }, 500)
    return null
  }

  const observer = new MutationObserver(() => {
    nextTick(() => {
      extractHeadings()
    })
  })

  observer.observe(contentElement, {
    childList: true,
    subtree: true
  })

  return observer
}

// 延迟提取标题，确保内容已渲染
const delayedExtractHeadings = () => {
  setTimeout(() => {
    extractHeadings()
    if (headings.value.length === 0) {
      // 如果还是没有标题，再次尝试
      setTimeout(() => {
        extractHeadings()
      }, 1000)
    }
  }, 300)
}

let contentObserver: MutationObserver | null = null

useNestedLenis(tocNavRef)

onMounted(() => {
  syncResponsiveVisibility()
  nextTick(() => {
    delayedExtractHeadings()
    handleScroll()
    contentObserver = observeContentChanges()
  })
  
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('resize', syncResponsiveVisibility, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', syncResponsiveVisibility)
  if (contentObserver) {
    contentObserver.disconnect()
  }
})

// 暴露方法供父组件调用
defineExpose({
  extractHeadings,
  scrollToHeading
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.table-of-contents {
  position: fixed;
  top: 120px;
  right: 20px;
  width: 260px;
  max-height: calc(100vh - 200px);
  // fallback + 毛玻璃半透明
  background: var(--bg-card);
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  box-shadow: var(--shadow-md);
  overflow: hidden;
  transition: width 0.3s ease, box-shadow 0.3s ease;
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(10px) saturate(140%);
  -webkit-backdrop-filter: blur(10px) saturate(140%);

  &:not(.visible) {
    width: 44px;
    border-radius: 12px;

    .toc-header {
      padding: 12px 0;
      justify-content: center;
      border-bottom: none;

      h4 { display: none; }
    }

    .toc-nav { display: none; }
    .toggle-btn { transform: rotate(-90deg); }
  }
}

.toc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 14px 12px;
  background: transparent;
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;

  h4 {
    margin: 0;
    font-size: 11px;
    font-weight: 700;
    color: var(--text-title);
    letter-spacing: 1.5px;
    text-transform: uppercase;
  }

  .toggle-btn {
    background: none;
    border: none;
    cursor: pointer;
    width: 24px;
    height: 24px;
    padding: 0;
    border-radius: 6px;
    color: var(--text-muted);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s ease, color 0.2s ease;

    &:hover {
      background: var(--bg-hover);
      color: var(--text-main);
    }

    svg { transition: transform 0.2s ease; }
  }
}

.toc-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 0;
  scrollbar-width: thin;
  scrollbar-color: var(--border-soft) transparent;

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb {
    background: var(--border-soft);
    border-radius: 2px;

    &:hover { background: var(--text-muted); }
  }
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0 6px;
}

.toc-item {
  margin: 0;
  position: relative;

  // 左侧激活指示条：scaleY 从中心展开
  &::before {
    content: "";
    position: absolute;
    left: 0;
    top: 8px;
    bottom: 8px;
    width: 2px;
    background: var(--color-primary);
    border-radius: 0 2px 2px 0;
    transform: scaleY(0);
    transform-origin: center;
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    pointer-events: none;
  }

  &.active::before {
    transform: scaleY(1);
  }
}

.toc-link {
  display: block;
  padding: 6px 12px;
  color: var(--text-subtle);
  text-decoration: none;
  font-size: 13px;
  line-height: 1.5;
  border-radius: 6px;
  transition: color 0.2s ease, background 0.2s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:hover {
    color: var(--text-main);
    background: var(--bg-hover);
  }
}

// 缩进与层级字号
.toc-level-1 .toc-link { padding-left: 18px; font-weight: 600; color: var(--text-main); }
.toc-level-2 .toc-link { padding-left: 30px; }
.toc-level-3 .toc-link { padding-left: 42px; font-size: 12px; }
.toc-level-4 .toc-link,
.toc-level-5 .toc-link,
.toc-level-6 .toc-link {
  padding-left: 54px;
  font-size: 11px;
}

// 激活文字
.toc-item.active .toc-link {
  color: var(--color-primary);
  font-weight: 500;
}
.toc-tabs { display: none; flex: 0 0 auto; border-bottom: 1px solid var(--border-light); padding: 6px; gap: 4px; }
.toc-tabs button { flex: 1; border: 0; padding: 8px; border-radius: 6px; color: var(--text-subtle); background: transparent; cursor: pointer; }
.toc-tabs button[aria-pressed="true"] { color: var(--color-primary); background: var(--bg-hover); }
@media (max-width: 1680px) {
  .toc-tabs { display: flex; }
  .toc-panel-hidden { display: none; }
}

/* 文章详情页嵌入模式：组件自己负责内部外观，父页面只负责定位容器。 */
.table-of-contents.table-of-contents--embedded {
  position: static;
  width: 100%;
  max-height: calc(100dvh - 130px);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  box-shadow: var(--shadow-md);

  .toc-header {
    padding: 16px 16px 12px;
    background: var(--bg-card);
    border-bottom: 1px solid var(--border-light);

    h4 {
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 1px;
    }
  }

  .toc-link {
    font-size: 13px;
  }
}

@media (max-width: 1680px) {
  .table-of-contents.table-of-contents--embedded {
    inset: auto;
    width: min(280px, calc(100vw - 32px));
    max-height: min(520px, calc(100dvh - 140px));

    &:not(.visible) {
      width: 44px;
      height: auto;
      border: 0;
      border-radius: 10px;
      background: var(--bg-card);
      box-shadow: 0 2px 8px rgb(0 0 0 / 8%);
      cursor: pointer;

      &:hover {
        box-shadow: 0 4px 16px rgb(0 0 0 / 12%);
      }

      .toc-header {
        justify-content: center;
        padding: 12px 0;
        border-bottom: 0;

        h4 {
          display: none;
        }
      }

      .toggle-btn {
        transform: rotate(-90deg);
      }
    }
  }
}

@media (max-width: 768px) {
  .table-of-contents.table-of-contents--embedded {
    width: min(280px, calc(100vw - 32px));
  }
}

// 响应式
@include respond(lg) {
  .table-of-contents {
    width: 220px;
    right: 12px;
  }
}

@include respond(md) {
  .table-of-contents {
    top: auto;
    bottom: 20px;
    right: 20px;
    left: 20px;
    width: auto;
    max-height: 50vh;
  }

  .toc-header:hover {
    background: var(--bg-hover);
  }
}

@include respond(sm) {
  .table-of-contents {
    bottom: 10px;
    left: 10px;
    right: 10px;
  }
}
</style>
