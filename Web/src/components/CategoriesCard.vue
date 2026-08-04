<template>
  <div class="card bg-card">
    <h4 class="card-title"><span class="card-badge"><Icon name="folder" size="12" /> Categories</span><span class="card-title-text">文章<span class="card-highlight">分类</span></span></h4>
    <div v-if="loading" class="loading-text text-sm">加载中...</div>
    <div v-else-if="categories.length === 0" class="empty-text flex flex-col flex-ac text-sm">
      <p>暂无分类</p>
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
    </div>
    <div v-else class="categories-list list">
      <div
        v-for="category in categories"
        :key="category.id"
        class="list-item flex flex-ac gap-12 link transition"
        @click="handleCategoryClick(category.id)"
      >
        <span class="category-icon"><Icon :name="getCategoryIcon(category.name)" size="16" /></span>
        <span class="text-lg font-medium flex-1">{{ category.name }}</span>
        <span class="categories-count">{{ category.postCount || 0 }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getCategoryIcon } from '@/utils/categoryIcons'
import { useRouter } from 'vue-router'
import Icon from './Icon.vue'

const router = useRouter()

// 定义props
interface Category {
  id: number
  name: string
  postCount?: number
}

interface Props {
  categories: Category[]
  loading?: boolean
}

withDefaults(defineProps<Props>(), {
  loading: false
})

// 处理分类点击 - 直接跳转到分类详情页面
const handleCategoryClick = (categoryId: number) => {
  router.push(`/category-detail/${categoryId}`)
}
</script>

<style scoped>
.category-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(var(--color-primary-rgb), 0.1);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease, color 0.2s ease;
}

.list-item:hover .category-icon {
  background: var(--color-primary);
  color: #fff;
}

.categories-count {
  min-width: 24px;
  padding: 2px 8px;
  border-radius: 12px;
  background: var(--bg-soft);
  color: var(--text-subtle);
  font-size: 0.75rem;
  font-weight: 600;
  text-align: center;
}
</style>