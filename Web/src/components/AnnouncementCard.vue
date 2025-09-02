<template>
  <div class="card bg-card">
    <h4 class="card-title">公告</h4>
    <div v-if="loading" class="text-center py-4">
      <span class="text-sm text-light">加载中...</span>
    </div>
    <div v-else-if="announcements.length === 0" class="text-center py-4">
      <span class="text-sm text-light">暂无公告</span>
    </div>
    <div v-else class="list">
      <div 
        v-for="announcement in announcements" 
        :key="announcement.id" 
        class="list-item link transition-colors"
        @click="showAnnouncementDetail(announcement)"
      >
        <div class="flex flex-sb">
          <span class="text-sm text-primary font-medium">{{ formatDate(announcement.createdAt) }}</span>
          <div class="flex items-center gap-2">
            <span v-if="announcement.isTop" class="text-xs bg-red-100 text-red-600 px-2 py-1 rounded">置顶</span>
            <span class="text-xs bg-blue-100 text-blue-600 px-2 py-1 rounded">{{ announcement.typeName }}</span>
          </div>
        </div>
        <h5 class="text-lg font-medium text-dark mb-1">{{ announcement.title }}</h5>
        <p class="text-sm text-light mb-0 line-clamp-2">{{ announcement.content }}</p>
      </div>
    </div>
    

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { AnnouncementService, type Announcement } from '../services/announcement'

// 响应式数据
const loading = ref(false)
const announcements = ref<Announcement[]>([])
const showDetail = ref(false)
const selectedAnnouncement = ref<Announcement | null>(null)

// 定义事件
defineEmits<{
  viewMore: []
}>()

// 格式化日期
const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

// 显示公告详情
const showAnnouncementDetail = (announcement: Announcement) => {
  selectedAnnouncement.value = announcement
  showDetail.value = true
}

// 获取最新公告
const fetchAnnouncements = async () => {
  try {
    loading.value = true
    const data = await AnnouncementService.getLatestAnnouncements(5)
    console.log('获取到的公告数据:', data);
    
    announcements.value = data
  } catch (error) {
    console.error('获取公告失败:', error)
    announcements.value = []
  } finally {
    loading.value = false
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchAnnouncements()
})
</script>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.transition-colors {
  transition: background-color 0.2s ease;
}

.hover\:bg-gray-50:hover {
  background-color: #f9fafb;
}

.hover\:text-primary-dark:hover {
  color: var(--color-primary-dark, #1e40af);
}

.hover\:text-gray-600:hover {
  color: #4b5563;
}

/* 弹窗遮罩层样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.max-h-\[80vh\] {
  max-height: 80vh;
}

.min-w-96 {
  min-width: 24rem;
}

.min-h-80 {
  min-height: 20rem;
}

.max-w-4xl {
  max-width: 56rem;
}

.overflow-y-auto {
  overflow-y: auto;
}

.whitespace-pre-wrap {
  white-space: pre-wrap;
}

.shadow-2xl {
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
}
</style>