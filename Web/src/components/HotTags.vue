<template>
  <div class="card bg-card ">
    <h4 class="card-title"><span class="card-badge"><Icon name="tag" size="12" /> Tag Cloud</span><span class="card-title-text">热门<span class="card-highlight">标签</span></span></h4>
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
import Icon from './Icon.vue'

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
.tag small {
  opacity: 0.7;
  margin-left: 6px;
  font-size: 0.75rem;
}

/* 加载中、暂无数据样式（可按需细化） */
.loading-text,
.empty-text {
  padding: 10px 0;
  color: var(--text-subtle, #666); /* 兜底颜色 */
}
</style>