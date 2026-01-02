<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useAnnouncementStore } from '../stores/announcement'
import type { Announcement } from '../services/announcement'
import Icon from './Icon.vue'

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
    1: 'badge-gray',
    2: 'badge-blue',
    3: 'badge-orange',
    4: 'badge-red'
  }
  return classMap[priority] || 'badge-gray'
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

<template>
  <div class="card">
    <div class="flex flex-sb">
      <h4 class="card-title mb-0">公告</h4>
      <button
        @click="refreshAnnouncements"
        :disabled="loading"
        class="refresh-btn"
        title="刷新公告"
      >
        <Icon name="refresh" :spin="loading" />
      </button>
    </div>
    <div v-if="loading" class="text-center p-16">
      <span class="text-sm">加载中...</span>
    </div>
    <div v-else-if="announcements.length === 0" class="text-center p-16 flex flex-col flex-ac">
      <span class="text-sm">暂无公告</span>
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
    </div>
    <div v-else class="list">
      <div
        v-for="announcement in announcements"
        :key="announcement.id"
        class="list-item link"
        @click="showAnnouncementDetail(announcement)"
      >
        <div class="flex flex-sb">
          <span class="text-sm font-medium">{{ formatDate(announcement.createdAt) }}</span>
          <div class="flex flex-sb gap-8">
            <span v-if="announcement.isTop" class="badge badge-red">置顶</span>
            <span class="badge badge-blue">{{ announcement.typeName }}</span>
          </div>
        </div>
        <h5 class="text-lg font-medium mb-8">{{ announcement.title }}</h5>
      </div>
    </div>

    <!-- 公告详情弹窗 -->
    <Teleport to="body">
      <div v-if="showDetail && selectedAnnouncement"
           class="modal-overlay"
           :class="{ 'show': showDetail }"
           @click="closeDetail">
        <div class="modal-container" @click.stop>
          <div class="modal-header">
            <h3 class="modal-header-title">公告详情</h3>
            <button @click="closeDetail" class="close-btn" aria-label="关闭弹窗">×</button>
          </div>

          <div class="modal-body">
            <h2 class="modal-title">{{ selectedAnnouncement.title }}</h2>

            <div class="modal-tags">
              <span v-if="selectedAnnouncement.isTop" class="badge badge-red">置顶</span>
              <span class="badge badge-blue">{{ selectedAnnouncement.typeName }}</span>
              <span class="badge" :class="getPriorityClass(selectedAnnouncement.priority)">{{ selectedAnnouncement.priorityName }}</span>
            </div>

            <div class="modal-content-text" v-html="selectedAnnouncement.content"></div>

            <div class="modal-info">
              <div class="info-row">
                <span class="info-label">发布时间：</span>
                <span class="info-value">{{ formatDateTime(selectedAnnouncement.createdAt) }}</span>
              </div>
              <div v-if="selectedAnnouncement.startTime" class="info-row">
                <span class="info-label">开始时间：</span>
                <span class="info-value">{{ formatDateTime(selectedAnnouncement.startTime) }}</span>
              </div>
              <div v-if="selectedAnnouncement.endTime" class="info-row">
                <span class="info-label">结束时间：</span>
                <span class="info-value">{{ formatDateTime(selectedAnnouncement.endTime) }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">浏览量：</span>
                <span class="info-value">{{ selectedAnnouncement.viewCount || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* 弹窗遮罩层 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 24px;
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(2px);
}

.modal-overlay.show {
  opacity: 1;
  visibility: visible;
}

.modal-container {
  background: var(--bg-card, #ffffff);
  border-radius: 12px;
  box-shadow:
    0 20px 25px -5px rgba(0, 0, 0, 0.1),
    0 10px 10px -5px rgba(0, 0, 0, 0.04),
    0 0 0 1px rgba(0, 0, 0, 0.05);
  width: 100%;
  max-width: 720px;
  max-height: min(85vh, 900px);
  overflow: hidden;
  transform: scale(0.95) translateY(20px);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.modal-overlay.show .modal-container {
  transform: scale(1) translateY(0);
}

.modal-header {
  position: relative;
  background: var(--bg-card, #ffffff);
  border-bottom: 1px solid var(--border-soft, rgba(0, 0, 0, 0.08));
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal-header-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-main, #1f2937);
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.75rem;
  color: var(--text-secondary, #9ca3af);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s ease;
  line-height: 1;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: var(--bg-soft, rgba(0, 0, 0, 0.05));
  color: var(--text-main, #4b5563);
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  max-height: calc(85vh - 80px);
}

.modal-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-main, #111827);
  margin: 0 0 16px 0;
  line-height: 1.4;
}

.modal-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  line-height: 1.5;
  white-space: nowrap;
}

.badge-red {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

.badge-blue {
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
}

.badge-orange {
  background: rgba(249, 115, 22, 0.1);
  color: #ea580c;
}

.badge-gray {
  background: rgba(107, 114, 128, 0.1);
  color: #6b7280;
}

.modal-content-text {
  margin-bottom: 20px;
  color: var(--text-secondary, #4b5563);
  line-height: 1.75;
  font-size: 0.9375rem;
}

.modal-content-text :deep(p) {
  margin: 0 0 1em 0;
}

.modal-content-text :deep(h1),
.modal-content-text :deep(h2),
.modal-content-text :deep(h3),
.modal-content-text :deep(h4),
.modal-content-text :deep(h5),
.modal-content-text :deep(h6) {
  color: var(--text-main, #111827);
  font-weight: 600;
  margin: 1.5em 0 0.5em;
  line-height: 1.3;
}

.modal-content-text :deep(h1) { font-size: 1.875rem; }
.modal-content-text :deep(h2) { font-size: 1.5rem; }
.modal-content-text :deep(h3) { font-size: 1.25rem; }
.modal-content-text :deep(h4) { font-size: 1.125rem; }
.modal-content-text :deep(h5) { font-size: 1rem; }

.modal-content-text :deep(ul),
.modal-content-text :deep(ol) {
  margin: 1em 0;
  padding-left: 1.75em;
}

.modal-content-text :deep(li) {
  margin: 0.5em 0;
}

.modal-content-text :deep(blockquote) {
  border-left: 4px solid var(--border-soft, #e5e7eb);
  padding-left: 1em;
  margin: 1em 0;
  color: var(--text-secondary, #6b7280);
  font-style: italic;
  background: var(--bg-soft, rgba(0, 0, 0, 0.02));
  padding: 12px 16px;
  border-radius: 6px;
}

.modal-content-text :deep(code) {
  background: var(--bg-soft, #f3f4f6);
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 0.875em;
  font-family: 'Courier New', monospace;
  color: #ef4444;
}

.modal-content-text :deep(pre) {
  background: #1f2937;
  color: #f9fafb;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 1em 0;
}

.modal-content-text :deep(pre) code {
  background: transparent;
  color: inherit;
  padding: 0;
}

.modal-content-text :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 1em 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.modal-content-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1em 0;
  font-size: 0.875rem;
}

.modal-content-text :deep(th),
.modal-content-text :deep(td) {
  border: 1px solid var(--border-soft, #e5e7eb);
  padding: 12px;
  text-align: left;
}

.modal-content-text :deep(th) {
  background: var(--bg-soft, #f9fafb);
  font-weight: 600;
  color: var(--text-main, #374151);
}

.modal-content-text :deep(a) {
  color: #2563eb;
  text-decoration: underline;
  transition: color 0.2s;
}

.modal-content-text :deep(a:hover) {
  color: #1d4ed8;
}

.modal-info {
  border-top: 1px solid var(--border-soft, rgba(0, 0, 0, 0.08));
  padding-top: 16px;
  margin-top: 20px;
}

.info-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 0.875rem;
}

.info-row:last-child {
  margin-bottom: 0;
}

.info-label {
  color: var(--text-secondary, #6b7280);
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
}

.info-value {
  color: var(--text-main, #374151);
  flex: 1;
}

/* 刷新按钮 */
.refresh-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  color: var(--text-secondary, #6b7280);
}

.refresh-btn:hover:not(:disabled) {
  background: var(--bg-soft, rgba(0, 0, 0, 0.05));
  color: var(--text-main, #374151);
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
    padding: 16px;
    align-items: flex-end;
  }

  .modal-container {
    max-width: 100%;
    width: 100%;
    max-height: 90vh;
    border-radius: 12px 12px 0 0;
    transform: translateY(100%);
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }

  .modal-overlay.show .modal-container {
    transform: translateY(0);
  }

  .modal-body {
    padding: 20px;
    max-height: calc(90vh - 70px);
  }

  .modal-header {
    padding: 16px 20px;
  }

  .modal-title {
    font-size: 1.25rem;
  }

  .modal-tags {
    gap: 6px;
  }

  .badge {
    font-size: 0.6875rem;
    padding: 3px 10px;
  }

  .info-row {
    flex-direction: column;
    gap: 4px;
  }

  .info-label {
    font-size: 0.8125rem;
  }

  .info-value {
    font-size: 0.875rem;
  }
}

@media (max-width: 480px) {
  .modal-overlay {
    padding: 12px;
  }

  .modal-body {
    padding: 16px;
  }
}
</style>
