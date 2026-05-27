<template>
  <div v-if="headings.length > 0" class="table-of-contents" :class="{ 'visible': isVisible }">
    <div class="toc-header" @click="toggleVisibility">
      <h4>目录</h4>
      <button @click.stop="toggleVisibility" class="toggle-btn" :aria-expanded="isVisible" aria-label="切换目录">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M6 9l6 6 6-6"/>
        </svg>
      </button>
    </div>
    <nav class="toc-nav" v-show="isVisible">
      <ul class="toc-list">
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
    </nav>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

const props = withDefaults(defineProps<{
  collapsedBelow?: number
}>(), {
  collapsedBelow: 0
})

interface Heading {
  id: string
  text: string
  level: number
  element: HTMLElement
}

const headings = ref<Heading[]>([])
const activeId = ref<string>('')
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
  const contentElement = document.querySelector('.markdown-content')
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

    smoothScrollTo(offsetPosition)
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
  const contentElement = document.querySelector('.markdown-content')
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
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  box-shadow: var(--shadow-md);
  overflow: hidden;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;

  &:not(.visible) {
    width: 44px;
    height: auto;
    border-radius: 12px;

    .toc-header {
      padding: 12px 0;
      justify-content: center;
      border-bottom: none;

      h4 {
        display: none;
      }

      .toggle-btn {
        transform: rotate(-90deg);
      }
    }

    .toc-nav {
      display: none;
    }
  }
}

.toc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 16px 12px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  user-select: none;

  h4 {
    margin: 0;
    font-size: 12px;
    font-weight: 700;
    color: var(--text-title);
    letter-spacing: 1px;
    text-transform: uppercase;
  }

  .toggle-btn {
    background: none;
    border: none;
    cursor: pointer;
    width: 20px;
    height: 20px;
    padding: 0;
    border-radius: 6px;
    color: var(--text-muted);
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      background: var(--bg-hover);
      color: var(--text-main);
    }

    svg {
      transition: transform 0.2s ease;
    }
  }
}

.toc-nav {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  padding: 8px 0;
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0 8px;
}

.toc-item {
  margin: 1px 0;
  position: relative;

  &.active {
    .toc-link {
      color: var(--color-primary);
      font-weight: 500;
    }
  }

  &.toc-level-1 .toc-link { padding-left: 16px; font-weight: 600; }
  &.toc-level-2 .toc-link { padding-left: 16px; }
  &.toc-level-3 .toc-link { padding-left: 28px; font-size: 12px; }
  &.toc-level-4 .toc-link,
  &.toc-level-5 .toc-link,
  &.toc-level-6 .toc-link {
    padding-left: 40px;
    font-size: 11px;
  }
}

.toc-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  color: var(--text-subtle);
  text-decoration: none;
  font-size: 13px;
  line-height: 1.4;
  border-radius: 8px;
  transition: color 0.15s ease;

  &::before {
    content: "";
    flex-shrink: 0;
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--border-soft);
    transition: background 0.15s ease;
  }

  &:hover {
    color: var(--color-primary);

    &::before {
      background: var(--color-primary);
    }
  }
}

// 激活状态
.toc-item.active .toc-link::before {
  background: var(--color-primary);
}

// 响应式样式
@include respond(lg) {
  .table-of-contents {
    width: 220px;
    right: 12px;
  }
}

@include respond(md) {
  .table-of-contents {
    position: fixed;
    top: auto;
    bottom: 20px;
    right: 20px;
    left: 20px;
    width: auto;
    max-height: 50vh;
  }

  .toc-header {
    &:hover {
      background: var(--bg-hover);
    }
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
