<template>
  <div class="card bg-card ">
    <h4 class="card-title">文章分类</h4>
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="categories.length === 0" class="empty-text">暂无分类</div>
    <div v-else class="list gap-8">
      <div 
        v-for="category in categories" 
        :key="category.id" 
        class="flex flex-sb flex-ac p-12 rounded link transition bg-hover"
        @click="handleCategoryClick(category.id)"
      >
        <span class="font-medium">{{ category.name }}</span>
        <span class="categories-count">{{ category.postCount || 0 }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

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
/* 使用全局样式，这里只定义组件特有的样式 */
.categories-count{
  width: 20px;
  height: 20px;
  background: var(--bg-color);
  border-radius: 50%;
  color: var(--text-color);
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>