<template>
  <div class="card bg-card">
    <div class="flex flex-sb">
      <h4 class="card-title mb-0">公告</h4>
      <button 
        @click="refreshAnnouncements" 
        :disabled="loading"
        class="refresh-btn"
        title="刷新公告"
      >
        <span v-if="loading" class="loading-spinner"></span>
        <span v-else>🔄</span>
      </button>
    </div>
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
        <div  class=" flex-sb">
          <span class="text-sm text-primary font-medium">{{ formatDate(announcement.createdAt) }}</span>
          <div class="flex-sb items-center">
            <span v-if="announcement.isTop" class="text-xs bg-red-100 text-red-600 px-2 py-1 rounded">置顶</span>
            <span class="text-xs bg-blue-100 text-blue-600 px-2 py-1 rounded">{{ announcement.typeName }}</span>
          </div>
        </div>
        <h5 class="text-lg font-medium text-dark mb-1">{{ announcement.title }}</h5>
        <!-- <p class="text-sm text-light mb-0 line-clamp-2">{{ announcement.content }}</p> -->
      </div>
    </div>
    
    <!-- 公告详情弹窗 -->
    <div v-if="showDetail && selectedAnnouncement" 
         class="modal-overlay" 
         :class="{ 'show': showDetail }" 
         @click="closeDetail">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>公告详情</h3>
          <button @click="closeDetail" class="close-btn">×</button>
        </div>
        
        <div class="modal-body">
          <h2 class="modal-title">{{ selectedAnnouncement.title }}</h2>
          
          <div class="modal-tags">
            <span v-if="selectedAnnouncement.isTop" class="tag tag-red">置顶</span>
            <span class="tag tag-blue">{{ selectedAnnouncement.typeName }}</span>
            <span class="tag" :class="getPriorityClass(selectedAnnouncement.priority)">{{ selectedAnnouncement.priorityName }}</span>
          </div>
          
          <div class="modal-content-text" v-html="selectedAnnouncement.content"></div>
          
          <div class="modal-info">
            <div class="info-item">
              <span class="label">发布时间：</span>
              <span>{{ formatDateTime(selectedAnnouncement.createdAt) }}</span>
            </div>
            <div v-if="selectedAnnouncement.startTime" class="info-item">
              <span class="label">开始时间：</span>
              <span>{{ formatDateTime(selectedAnnouncement.startTime) }}</span>
            </div>
            <div v-if="selectedAnnouncement.endTime" class="info-item">
              <span class="label">结束时间：</span>
              <span>{{ formatDateTime(selectedAnnouncement.endTime) }}</span>
            </div>
            <div class="info-item">
              <span class="label">浏览量：</span>
              <span>{{ selectedAnnouncement.viewCount || 0 }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useAnnouncementStore } from '../stores/announcement'
import type { Announcement } from '../services/announcement'

// 使用公告 store
const announcementStore = useAnnouncementStore()

// 响应式数据
const showDetail = ref(false)
const selectedAnnouncement = ref<Announcement | null>(null)

// 定义事件
defineEmits<{
  viewMore: []
}>()

// 计算属性
const loading = computed(() => announcementStore.isLatestLoading)
const announcements = computed(() => announcementStore.latestAnnouncements)

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

// 关闭详情弹窗
const closeDetail = () => {
  showDetail.value = false
  selectedAnnouncement.value = null
}

// 获取优先级样式类
const getPriorityClass = (priority: number) => {
  const classMap: Record<number, string> = {
    1: 'bg-gray-100 text-gray-800',
    2: 'bg-blue-100 text-blue-800', 
    3: 'bg-orange-100 text-orange-800',
    4: 'bg-red-100 text-red-800'
  }
  return classMap[priority] || 'bg-gray-100 text-gray-800'
}

// 格式化日期时间
const formatDateTime = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取最新公告
const fetchAnnouncements = async () => {
  try {
    console.log('获取公告数据...')
    const data = await announcementStore.fetchLatestAnnouncements(5)
    console.log('获取到的公告数据:', data)
  } catch (error) {
    console.error('获取公告失败:', error)
  }
}

// 刷新公告数据
const refreshAnnouncements = async () => {
  try {
    console.log('刷新公告数据...')
    const data = await announcementStore.refreshLatestAnnouncements(5)
    console.log('刷新后的公告数据:', data)
  } catch (error) {
    console.error('刷新公告失败:', error)
  }
}

// ESC键关闭弹窗
const handleEscKey = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && showDetail.value) {
    closeDetail()
  }
}

// 组件挂载时获取数据
onMounted(async () => {
  // 初始化公告数据（会从缓存恢复或从服务器获取）
  await announcementStore.initAnnouncements()
  
  // 如果没有数据，则主动获取
  if (announcements.value.length === 0) {
    await fetchAnnouncements()
  }
  
  // 添加ESC键监听
  document.addEventListener('keydown', handleEscKey)
})

// 组件卸载时清理
onUnmounted(() => {
  document.removeEventListener('keydown', handleEscKey)
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

/* 弹窗遮罩层 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s ease;
}

.modal-overlay.show {
  opacity: 1;
  visibility: visible;
}

.modal-content {
  background: white;
  border-radius: 0.5rem;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  width: 80vw;
  min-height: 20rem;
  max-height: 80vh;
  overflow-y: auto;
}

.modal-header {
  position: sticky;
  top: 0;
  background: white;
  border-bottom: 1px solid #e5e7eb;
  padding: 1.5rem;
  border-radius: 0.5rem 0.5rem 0 0;
  display: flex;
  align-items: center;
  justify-content: between;
}

.modal-header h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #111827;
  flex: 1;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #9ca3af;
  cursor: pointer;
  padding: 0.25rem;
  transition: color 0.2s;
}

.close-btn:hover {
  color: #4b5563;
}

.modal-body {
  padding: 1.5rem;
}

.modal-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 1rem;
  color: #111827;
}

.modal-tags {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.tag {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.625rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 500;
}

.tag-red {
  background: #fef2f2;
  color: #dc2626;
}

.tag-blue {
  background: #eff6ff;
  color: #2563eb;
}

.modal-content-text {
  margin-bottom: 1.5rem;
  color: #374151;
  line-height: 1.7;
}

.modal-info {
  border-top: 1px solid #e5e7eb;
  padding-top: 1rem;
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.info-item {
  font-size: 0.875rem;
  color: #6b7280;
}

.label {
  font-weight: 500;
}

/* 富文本内容样式 */
.modal-content-text h1,
.modal-content-text h2,
.modal-content-text h3,
.modal-content-text h4,
.modal-content-text h5,
.modal-content-text h6 {
  color: #111827;
  font-weight: 600;
  margin: 1.5em 0 0.5em;
}

.modal-content-text h1 { font-size: 2.25em; }
.modal-content-text h2 { font-size: 1.875em; }
.modal-content-text h3 { font-size: 1.5em; }
.modal-content-text h4 { font-size: 1.25em; }
.modal-content-text h5 { font-size: 1.125em; }

.modal-content-text p {
  margin: 1em 0;
}

.modal-content-text ul,
.modal-content-text ol {
  margin: 1em 0;
  padding-left: 2em;
}

.modal-content-text blockquote {
  border-left: 4px solid #e5e7eb;
  padding-left: 1em;
  margin: 1em 0;
  color: #6b7280;
  font-style: italic;
}

.modal-content-text code {
  background: #f3f4f6;
  border-radius: 0.25rem;
  padding: 0.125rem 0.25rem;
  font-size: 0.875em;
  color: #ef4444;
}

.modal-content-text pre {
  background: #1f2937;
  color: #f9fafb;
  border-radius: 0.5rem;
  padding: 1rem;
  overflow-x: auto;
}

.modal-content-text pre code {
  background: transparent;
  color: inherit;
  padding: 0;
}

.modal-content-text img {
  max-width: 100%;
  height: auto;
  border-radius: 0.5rem;
  margin: 1em 0;
}

.modal-content-text table {
  width: 100%;
  border-collapse: collapse;
  margin: 1em 0;
}

.modal-content-text th,
.modal-content-text td {
  border: 1px solid #e5e7eb;
  padding: 0.75rem;
  text-align: left;
}

.modal-content-text th {
  background: #f9fafb;
  font-weight: 600;
}

/* 刷新按钮 */
.refresh-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
}

.refresh-btn:hover:not(:disabled) {
  background-color: var(--bg-soft);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid transparent;
  border-top: 2px solid currentColor;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .modal-overlay {
    padding: 10px;
  }
  
  .modal-content {
    max-width: 95vw;
    min-width: 90vw;
  }
  
  .modal-info {
    grid-template-columns: 1fr;
  }
}
</style>