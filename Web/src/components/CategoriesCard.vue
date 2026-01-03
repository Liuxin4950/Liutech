<template>
  <div class="card bg-card ">
    <h4 class="card-title">文章分类</h4>
    <div v-if="loading" class="loading-text text-sm">加载中...</div>
    <div v-else-if="categories.length === 0" class="empty-text flex flex-col flex-ac text-sm">
      <p>暂无分类</p>
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
    </div>
    <div v-else class="list gap-8">
      <div 
        v-for="category in categories" 
        :key="category.id" 
        class="flex flex-sb flex-ac p-12 rounded link transition bg-soft"
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
  background: var(--bg-element);
  border-radius: 50%;
  color: var(--text-main);
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>