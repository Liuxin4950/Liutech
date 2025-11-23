<template>
  <div v-if="headings.length > 0" class="table-of-contents" :class="{ 'visible': isVisible }">
    <div class="toc-header">
      <h4>目录</h4>
      <button @click="toggleVisibility" class="toggle-btn">
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

interface Heading {
  id: string
  text: string
  level: number
  element: HTMLElement
}

const headings = ref<Heading[]>([])
const activeId = ref<string>('')
const isVisible = ref(true)

// 提取标题
const extractHeadings = () => {
  const contentElement = document.querySelector('.markdown-content')
  if (!contentElement) return

  const headingElements = contentElement.querySelectorAll('h1, h2, h3, h4, h5, h6')
  const extractedHeadings: Heading[] = []

  headingElements.forEach((element, index) => {
    const tagName = element.tagName.toLowerCase()
    const level = parseInt(tagName.charAt(1))
    const text = element.textContent?.trim() || ''
    
    if (text) {
      // 生成唯一ID
      let id = element.id || `heading-${index}-${text.replace(/\s+/g, '-').toLowerCase()}`
      
      // 确保ID唯一
      let counter = 1
      let originalId = id
      while (document.getElementById(id)) {
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

    window.scrollTo({
      top: offsetPosition,
      behavior: 'smooth'
    })
  }
}

// 切换目录可见性
const toggleVisibility = () => {
  isVisible.value = !isVisible.value
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
  nextTick(() => {
    delayedExtractHeadings()
    handleScroll()
    contentObserver = observeContentChanges()
  })
  
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
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
  background: var(--bg-soft);
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
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
  padding: 12px 16px;
  background: var(--bg-main);
  border-bottom: 1px solid var(--border-soft);

  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-main);
  }

  .toggle-btn {
    background: none;
    border: none;
    cursor: pointer;
    padding: 4px;
    border-radius: 4px;
    color: var(--text-secondary);
    transition: all 0.2s ease;

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
  max-height: calc(100vh - 280px);
  overflow-y: auto;
  padding: 8px 0;

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
  margin: 0;

  &.toc-level-1 {
    padding-left: 16px;
  }

  &.toc-level-2 {
    padding-left: 28px;
  }

  &.toc-level-3 {
    padding-left: 40px;
  }

  &.toc-level-4 {
    padding-left: 52px;
  }

  &.toc-level-5 {
    padding-left: 64px;
  }

  &.toc-level-6 {
    padding-left: 76px;
  }

  &.active .toc-link {
    color: var(--color-primary);
    background: var(--bg-hover);
    border-left: 3px solid var(--color-primary);
  }
}

.toc-link {
  display: block;
  padding: 6px 16px 6px 0;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 13px;
  line-height: 1.4;
  border-left: 3px solid transparent;
  transition: all 0.2s ease;
  word-break: break-word;

  &:hover {
    color: var(--text-main);
    background: var(--bg-hover);
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