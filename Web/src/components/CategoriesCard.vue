<template>
  <div class="card">
    <h4 class="card-title">📂 文章分类</h4>
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="categories.length === 0" class="empty-text">暂无分类</div>
    <div v-else class="list gap-8">
      <div 
        v-for="category in categories" 
        :key="category.id" 
        class="flex flex-sb flex-ac p-12 bg-hover rounded link transition-slow border-l-3 hover-transform"
        @click="handleCategoryClick(category.id)"
      >
        <span class="font-medium">{{ category.name }}</span>
        <span class="badge">{{ category.postCount || 0 }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">


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

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

// 定义事件
const emit = defineEmits<{
  categoryClick: [categoryId: number]
}>()

// 处理分类点击
const handleCategoryClick = (categoryId: number) => {
  emit('categoryClick', categoryId)
}
</script>

<style scoped>
/* 使用全局样式，这里只定义组件特有的样式 */
</style>