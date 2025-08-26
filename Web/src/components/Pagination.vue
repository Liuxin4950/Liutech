<template>
  <!-- 分页器组件 -->
  <div v-if="showPagination" class="pagination-container card flex flex-jc flex-ac gap-16 mt-16">
    <!-- 上一页按钮 -->
    <button 
      @click="goToPage(currentPage - 1)" 
      :disabled="currentPage <= 1"
      class="pagination-btn bg-primary text-sm font-medium p-8 rounded transition text-main"
      :class="{ 'btn-disabled opacity-50 cursor-not-allowed': currentPage <= 1 }"
    >
      <span v-if="showArrows"></span>
      上一页
    </button>

    <!-- 页码区域 -->
    <div class="flex flex-ac gap-16">
      <!-- 页码按钮 -->
      <span v-if="showPageNumbers" class="flex gap-8">
        <button 
          v-for="page in visiblePages" 
          :key="page"
          @click="page !== -1 && goToPage(page)"
          :disabled="page === -1"
          :class="[
            'text-sm p-8 rounded transition text-main',
            {
              'bg-primary text-white': page === currentPage,
              'bg-hover text-main hover-lift': page !== currentPage && page !== -1,
              'cursor-default': page === -1
            }
          ]"
        >
          {{ page === -1 ? '...' : page }}
        </button>
      </span>

      <!-- 页码信息 -->
      <span class="text-sm text-muted px-12 py-6 rounded">
        第 {{ currentPage }} 页，共 {{ totalPages }} 页
      </span>
    </div>

    <!-- 下一页按钮 -->
    <button 
      @click="goToPage(currentPage + 1)" 
      :disabled="currentPage >= totalPages"
      class="pagination-btn bg-primary text-sm font-medium p-8 rounded transition text-main"
      :class="{ 'btn-disabled opacity-50 cursor-not-allowed': currentPage >= totalPages }"
    >
      下一页
      <span v-if="showArrows"></span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

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
}

const props = withDefaults(defineProps<PaginationProps>(), {
  showArrows: true,
  showPageNumbers: true,
  maxVisiblePages: 7
})

// Emits 定义
const emit = defineEmits<{
  pageChange: [page: number]
}>()

// 计算是否显示分页器
const showPagination = computed(() => {
  return props.totalPages > 1
})

// 计算可见的页码
const visiblePages = computed(() => {
  const current = props.currentPage
  const total = props.totalPages
  const maxVisible = props.maxVisiblePages
  const pages: number[] = []

  if (total <= maxVisible) {
    // 总页数小于等于最大可见数，显示全部页码
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
  } else {
    // 总页数大于最大可见数，显示部分页码
    const halfVisible = Math.floor(maxVisible / 2)
    
    if (current <= halfVisible + 1) {
      // 当前页在前半部分
      for (let i = 1; i <= maxVisible - 2; i++) {
        pages.push(i)
      }
      pages.push(-1) // 省略号
      pages.push(total)
    } else if (current >= total - halfVisible) {
      // 当前页在后半部分
      pages.push(1)
      pages.push(-1) // 省略号
      for (let i = total - (maxVisible - 3); i <= total; i++) {
        pages.push(i)
      }
    } else {
      // 当前页在中间
      pages.push(1)
      pages.push(-1) // 省略号
      for (let i = current - 1; i <= current + 1; i++) {
        pages.push(i)
      }
      pages.push(-1) // 省略号
      pages.push(total)
    }
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

<style scoped>
/**
 * 分页器样式
 * 作者：刘鑫
 * 时间：2025-08-26
 * 说明：使用项目通用样式，保持一致性
 */

.pagination-container {
  /* 使用通用卡片样式和布局类 */
}

.pagination-btn {
  /* 按钮基础样式已通过类名定义 */
  min-width: 80px;
}

.pagination-btn:hover:not(.btn-disabled) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.btn-disabled {
  pointer-events: none;
}

.hover-lift:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .pagination-container {
    flex-wrap: wrap;
    gap: 12px;
  }
  
  .pagination-btn {
    min-width: 60px;
    padding: 6px;
    font-size: 0.75rem;
  }
  
  .flex.gap-8 {
    gap: 4px;
  }
  
  .text-sm {
    font-size: 0.7rem;
  }
}
</style>