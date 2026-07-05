<template>
  <div class="card bg-card ">
    <h4 class="card-title">热门标签</h4>
    <div v-if="loading" class="loading-text text-sm">加载中...</div>
    <div v-else-if="tags.length === 0" class="empty-text flex flex-col flex-ac text-sm">
      <p>暂无标签</p>
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
    </div>
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

withDefaults(defineProps<Props>(), {
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
/* 标签云组件外层容器 */
.tags-cloud {
  justify-content: flex-start; /* 让标签从左往右排列 */
}

.tag small {
  opacity: 0.7;
  margin-left: 6px;
  font-size: 0.75rem;
}

/* 标题样式（可根据实际全局样式调整，这里做基础设置） */
.card-title {
  margin-bottom: 12px; /* 与标签区域拉开间距 */
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-title, #333); /* 兜底颜色 */
}

/* 加载中、暂无数据样式（可按需细化） */
.loading-text,
.empty-text {
  padding: 10px 0;
  color: var(--text-subtle, #666); /* 兜底颜色 */
}
</style>