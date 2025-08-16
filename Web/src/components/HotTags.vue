<template>
  <div class="card bg-card ">
    <h4 class="card-title bg-hover ">热门标签</h4>
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="tags.length === 0" class="empty-text">暂无标签</div>
    <div v-else class="tags-cloud">
      <span 
        v-for="tag in tags" 
        :key="tag.id" 
        class="tag"
        @click="handleTagClick(tag.id)"
      >
        {{ tag.name }}
        <small v-if="tag.postCount">({{ tag.postCount }})</small>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">


// 定义props
interface Tag {
  id: number
  name: string
  postCount?: number
}

interface Props {
  tags: Tag[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

// 定义事件
const emit = defineEmits<{
  tagClick: [tagId: number]
}>()

// 处理标签点击
const handleTagClick = (tagId: number) => {
  emit('tagClick', tagId)
}
</script>

<style scoped>
/* 使用全局样式，这里只定义组件特有的样式 */
</style>