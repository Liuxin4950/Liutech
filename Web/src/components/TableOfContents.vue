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

  const animate = (now: number) => {
    const progress = Math.min((now - startTime) / duration, 1)
    window.scrollTo(0, startY + distance * easeInOutCubic(progress))

    if (progress < 1) {
      requestAnimationFrame(animate)
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

// 滚动到指定标题
const scrollToHeading = (id: string) => {
  const element = document.getElementById(id)
  if (element) {
    const offset = 80 // 考虑固定头部的高度
    const elementPosition = element.getBoundingClientRect().top + window.pageYOffset
    const offsetPosition = elementPosition - offset

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
  if (headings.value.length === 0) return

  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const offset = 100

  // 找到当前可见的标题
  let currentHeading = headings.value[0]
  
  for (const heading of headings.value) {
    const element = heading.element
    const elementTop = element.getBoundingClientRect().top + scrollTop
    
    if (scrollTop + offset >= elementTop) {
      currentHeading = heading
    } else {
      break
    }
  }

  activeId.value = currentHeading.id
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
  width: 280px;
  max-height: calc(100vh - 200px);
  background: var(--surface-glass);
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(14px);
  overflow: hidden;
  transition: all 0.3s ease;

  &:not(.visible) {
    .toc-header .toggle-btn svg {
      transform: rotate(-90deg);
    }
  }
}

.toc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px 10px;
  background: linear-gradient(180deg, var(--state-primary-bg), transparent);
  border-bottom: 1px solid var(--border-soft);
  cursor: pointer;
  user-select: none;

  h4 {
    margin: 0;
    font-size: 13px;
    font-weight: 700;
    color: var(--text-title);
  }

  .toggle-btn {
    background: none;
    border: none;
    cursor: pointer;
    width: 28px;
    height: 28px;
    padding: 0;
    border-radius: 999px;
    color: var(--color-primary);
    transition: all 0.2s ease;

    &:hover {
      background: var(--state-primary-bg-hover);
      color: var(--color-primary);
    }

    svg {
      transition: transform 0.2s ease;
    }
  }
}

.toc-nav {
  max-height: calc(100vh - 280px);
  overflow-y: auto;
  padding: 10px 8px 12px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--border-soft);
    border-radius: 2px;
  }
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.toc-item {
  margin: 2px 0;

  &.active .toc-link {
    color: var(--color-primary);
    background: linear-gradient(90deg, var(--state-primary-bg-active), var(--state-primary-bg));
    font-weight: 700;
  }

  &.active .toc-link::before {
    background: var(--color-primary);
    box-shadow: 0 0 0 4px var(--state-primary-bg-hover);
    transform: translateY(-50%) scale(1.12);
  }

  &.toc-level-1 .toc-link {
    padding-left: 22px;
    font-weight: 700;
  }

  &.toc-level-2 .toc-link {
    padding-left: 32px;
  }

  &.toc-level-3 .toc-link {
    padding-left: 44px;
  }

  &.toc-level-4 .toc-link,
  &.toc-level-5 .toc-link,
  &.toc-level-6 .toc-link {
    padding-left: 54px;
    font-size: 12px;
    color: var(--text-muted);
  }
}

.toc-link {
  position: relative;
  display: block;
  padding: 7px 10px 7px 32px;
  color: var(--text-subtle);
  text-decoration: none;
  font-size: 12.5px;
  line-height: 1.45;
  border-radius: 10px;
  transition: all 0.2s ease;
  word-break: break-word;

  &::before {
    content: "";
    position: absolute;
    top: 50%;
    left: 10px;
    width: 5px;
    height: 5px;
    border-radius: 999px;
    background: var(--state-primary-border);
    transform: translateY(-50%);
    transition: all 0.2s ease;
  }

  &:hover {
    color: var(--text-main);
    background: var(--state-primary-bg);
  }

  &:hover::before {
    background: var(--color-primary);
  }
}

// 响应式样式
@include respond(lg) {
  .table-of-contents {
    width: 240px;
    right: 15px;
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

    &:not(.visible) {
      .toc-nav {
        display: none;
      }
    }
  }

  .toc-header {
    cursor: pointer;
    user-select: none;

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
