<template>
  <!-- 分页器组件 -->
  <div v-if="showPagination" class="pagination-container card flex flex-sb " >
    <!-- 上一页按钮 -->
    <button 
      @click="goToPage(currentPage - 1)" 
      :disabled="currentPage <= 1"
      class="pagination-btn text-sm font-medium p-8 rounded transition"
      :class="{ 
        'bg-primary text-on-primary hover:bg-primary-dark': currentPage > 1,
        'bg-element text-subtle opacity-60 cursor-not-allowed': currentPage <= 1 
      }"
    >
      <span v-if="showArrows"></span>
      上一页
    </button>

    <!-- 页码区域 -->
    <div class="flex flex-ac ">
      <!-- 页码按钮 -->
      <span v-if="showPageNumbers" class="flex gap-8">
        <button 
          v-for="page in visiblePages" 
          :key="page"
          @click="page !== -1 && goToPage(page)"
          :disabled="page === -1"
          :class="[
            'text-sm p-8 rounded transition',
            {
              'bg-primary text-on-primary': page === currentPage,
              'bg-element text-subtle hover-lift hover:bg-hover': page !== currentPage && page !== -1,
              'text-muted cursor-default bg-transparent': page === -1
            }
          ]"
        >
          {{ page === -1 ? '...' : page }}
        </button>
      </span>

      <!-- 页码信息 -->
      <span class="text-sm px-12 py-6 rounded text-subtle">
        第 {{ currentPage }} 页，共 {{ totalPages }} 页
      </span>
    </div>

    <!-- 下一页按钮 -->
    <button 
      @click="goToPage(currentPage + 1)" 
      :disabled="currentPage >= totalPages"
      class="pagination-btn text-sm font-medium p-8 rounded transition"
      :class="{ 
        'bg-primary text-on-primary hover:bg-primary-dark': currentPage < totalPages,
        'bg-element text-subtle opacity-60 cursor-not-allowed': currentPage >= totalPages 
      }"
    >
      下一页
      <span v-if="showArrows"></span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'

/**
 * 通用分页器组件
 * 作者：刘鑫
 * 时间：2025-08-26
 * 说明：统一项目中所有分页器的样式和功能
 */

// Props 定义
interface PaginationProps {
  currentPage: number      // 当前页码
  totalPages: number       // 总页数
  showArrows?: boolean     // 是否显示箭头图标
  showPageNumbers?: boolean // 是否显示页码按钮
  maxVisiblePages?: number  // 最大可见页码数量
  mobileMaxVisible?: number // 移动端最大可见页码数量
}

const props = withDefaults(defineProps<PaginationProps>(), {
  showArrows: true,
  showPageNumbers: true,
  maxVisiblePages: 7,
  mobileMaxVisible: 5
})

// Emits 定义
const emit = defineEmits<{
  pageChange: [page: number]
}>()

// 计算是否显示分页器
const showPagination = computed(() => {
  return props.totalPages > 1
})

// 响应式窗口宽度检测
const windowWidth = ref(768)

const updateWindowWidth = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  updateWindowWidth()
  window.addEventListener('resize', updateWindowWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateWindowWidth)
})

// 响应式最大可见页码数量
const responsiveMaxVisible = computed(() => {
  // 在移动端使用更少的页码数量
  return windowWidth.value <= 768 ? props.mobileMaxVisible : props.maxVisiblePages
})

// 计算可见的页码
const visiblePages = computed(() => {
  const current = props.currentPage
  const total = props.totalPages
  const maxVisible = responsiveMaxVisible.value
  const pages: number[] = []

  // 如果总页数小于等于最大可见数，显示全部页码
  if (total <= maxVisible) {
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
    return pages
  }

  // 总页数大于最大可见数时的智能省略逻辑
  const showEllipsis = total > maxVisible
  const sidePages = Math.floor((maxVisible - 3) / 2) // 当前页两侧显示的页数
  
  if (current <= sidePages + 1) {
    // 当前页在开始部分：1,2,3,4,5,...,total
    for (let i = 1; i <= maxVisible - 1; i++) {
      pages.push(i)
    }
    if (showEllipsis) {
      pages.push(-1) // 省略号
      pages.push(total)
    }
  } else if (current >= total - sidePages) {
    // 当前页在结束部分：1,...,total-4,total-3,total-2,total-1,total
    pages.push(1)
    if (showEllipsis) {
      pages.push(-1) // 省略号
    }
    for (let i = total - (maxVisible - 2); i <= total; i++) {
      pages.push(i)
    }
  } else {
    // 当前页在中间部分：1,...,current-1,current,current+1,...,total
    pages.push(1)
    if (showEllipsis) {
      pages.push(-1) // 省略号
    }
    
    // 计算中间显示的页码范围
    const startPage = Math.max(current - sidePages, 2)
    const endPage = Math.min(current + sidePages, total - 1)
    
    for (let i = startPage; i <= endPage; i++) {
      pages.push(i)
    }
    
    if (showEllipsis && endPage < total - 1) {
      pages.push(-1) // 省略号
    }
    pages.push(total)
  }
  
  return pages
})

// 跳转到指定页面
const goToPage = (page: number) => {
  if (page < 1 || page > props.totalPages || page === props.currentPage) {
    return
  }
  emit('pageChange', page)
}
</script>

<style lang="scss" scoped>
@use "@/assets/styles/tokens" as *;
.pagination-container{
  width: 100%;
  align-items: center;

  @include respond(md) {
    flex-wrap: wrap;
    gap: 8px;
  }

  @include respond(sm) {
    flex-direction: column;
    gap: 12px;
  }
}

.pagination-btn {
  min-width: 60px;
  padding: 6px;
  font-size: 0.75rem;
  border: 1px solid var(--border-light);

  @include respond(md) {
    flex: 1;
    min-width: 80px;
  }

  @include respond(sm) {
    width: 100%;
    min-width: auto;
  }
}

.pagination-btn:not(:disabled) {
  cursor: pointer;
}

.pagination-btn:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary);
}

.hover-lift:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

/* 页码按钮样式优化 */
.pagination-container button:not(.pagination-btn) {
  border: 1px solid var(--border-light);
  min-width: 36px;
  font-weight: 500;

  @include respond(md) {
    min-width: 32px;
    font-size: 0.65rem;
  }

  @include respond(sm) {
    min-width: 28px;
    padding: 4px 6px;
    font-size: 0.6rem;
  }
}

.pagination-container button:not(.pagination-btn):not(:disabled):hover {
  border-color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

/* 省略号样式优化 */
.pagination-container button.bg-transparent {
  border: none;
  cursor: default;
}

.flex.gap-8 {
  gap: 4px;
}

.text-sm {
  font-size: 0.7rem;
}
</style>