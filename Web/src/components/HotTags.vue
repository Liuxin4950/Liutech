<template>
  <div class="card bg-card ">
    <h4 class="card-title">热门标签</h4>
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
  display: flex;
  flex-wrap: wrap;
  gap: 12px; /* 增大标签之间的间距，更贴近截图效果 */
  justify-content: flex-start; /* 让标签从左往右排列 */
}

/* 单个标签样式 */
.tag {
  background: var(--bg-tag, #f3f4f6); /* 兜底颜色，防止变量未定义 */
  color: var(--text-title, #333); /* 兜底颜色 */
  padding: 6px 10px; /* 调整内边距，让标签更饱满 */
  border-radius: 16px; /* 增大圆角，更柔和 */
  font-size: 0.8rem; /* 调整字体大小 */
  font-weight: 400; /* 调整字体粗细，贴近截图清淡风格 */
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--border-soft, #e5e7eb); /* 兜底颜色 */
  display: inline-flex;
  align-items: center;
}

.tag:hover {
  background: var(--bg-tag-hover, #e5e7eb); /* 兜底颜色 */
  color: white;
}

.tag small {
  opacity: 0.7; /* 调整透明度，让数字更柔和 */
  margin-left: 6px; /* 增大与标签文字的间距 */
  font-size: 0.75rem; /* 调整小文字大小 */
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
  color: var(--text-secondary, #666); /* 兜底颜色 */
}
</style>