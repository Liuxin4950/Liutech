<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useId } from 'vue'
import DOMPurify from 'dompurify'
import { useAnnouncementStore } from '../stores/announcement'
import { AnnouncementService } from '../services/announcement'
import type { Announcement } from '../services/announcement'
import { useNestedLenis } from '@/composables/useLenis'
import { formatDate, formatDateTime } from '@/utils/utils'
import Icon from './Icon.vue'
import LoadingState from './LoadingState.vue'

const announcementStore = useAnnouncementStore()
const showDetail = ref(false)
const selectedAnnouncement = ref<Announcement | null>(null)
const dialogRef = ref<HTMLElement | null>(null)
const modalBodyRef = ref<HTMLElement | null>(null)
const closeButtonRef = ref<HTMLButtonElement | null>(null)
const dialogId = useId()
const dialogTitleId = useId()
let detailGeneration = 0
let trigger: HTMLElement | null = null

defineEmits<{ viewMore: [] }>()

const loading = computed(() => announcementStore.isLatestLoading)
const announcements = computed(() => announcementStore.latestAnnouncements)
const safeContent = computed(() => DOMPurify.sanitize(selectedAnnouncement.value?.content || ''))
useNestedLenis(modalBodyRef)

// 列表内容先呈现；真实详情请求仍为每次打开记录浏览量。
const showAnnouncementDetail = async (announcement: Announcement) => {
  const generation = ++detailGeneration
  trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  selectedAnnouncement.value = announcement
  showDetail.value = true
  void nextTick(() => {
    if (showDetail.value && generation === detailGeneration) closeButtonRef.value?.focus()
  })
  try {
    const fresh = await AnnouncementService.getAnnouncementById(announcement.id)
    if (fresh && showDetail.value && generation === detailGeneration) {
      selectedAnnouncement.value = fresh
    }
  } catch {
    // 请求失败时保留列表内容，避免中断阅读。
  }
}

const closeDetail = () => {
  detailGeneration++
  showDetail.value = false
  selectedAnnouncement.value = null
  const opener = trigger
  trigger = null
  if (opener?.isConnected) opener.focus({ preventScroll: true })
}

// 与站内搜索弹窗一致：ESC 关闭，Tab 在弹窗内循环，关闭后恢复触发元素焦点。
const handleModalKeydown = (event: KeyboardEvent) => {
  if (!showDetail.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    closeDetail()
    return
  }
  if (event.key !== 'Tab') return
  const items = Array.from(dialogRef.value?.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]'
  ) || []).filter(element => element.getClientRects().length)
  const first = items[0]
  const last = items[items.length - 1]
  if (!first) {
    event.preventDefault()
    dialogRef.value?.focus()
  } else if (!dialogRef.value?.contains(document.activeElement)) {
    event.preventDefault()
    first.focus()
  } else if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

const fetchAnnouncements = async () => {
  try {
    await announcementStore.initAnnouncements()
    if (announcements.value.length === 0) await announcementStore.fetchLatestAnnouncements(5)
  } catch {
    // 加载失败保留空状态或已有内容。
  }
}

let refreshTimer: number | null = null
const refreshAnnouncements = () => {
  if (refreshTimer !== null) clearTimeout(refreshTimer)
  refreshTimer = window.setTimeout(async () => {
    refreshTimer = null
    try {
      await announcementStore.refreshLatestAnnouncements(5)
    } catch {
      // 刷新失败时保留已有列表。
    }
  }, 300)
}

onMounted(() => {
  document.addEventListener('keydown', handleModalKeydown)
  void fetchAnnouncements()
})

onUnmounted(() => {
  detailGeneration++
  document.removeEventListener('keydown', handleModalKeydown)
  if (refreshTimer !== null) clearTimeout(refreshTimer)
})
</script>

<template>
  <section class="announcement-card card" aria-label="公告栏">
    <div class="announcement-header">
      <h4 class="card-title">
        <span class="card-badge announcement-badge" aria-hidden="true"><Icon name="bell" size="14" /></span>
        <span class="card-title-text">公告<span class="card-highlight">栏</span></span>
      </h4>
      <button type="button" class="refresh-btn" :disabled="loading" title="刷新公告" aria-label="刷新公告" @click="refreshAnnouncements">
        <Icon name="refresh" size="15" :spin="loading" />
      </button>
    </div>

    <LoadingState v-if="announcements.length === 0 && loading" compact label="正在加载公告…" />
    <p v-else-if="announcements.length === 0" class="announcement-empty">暂无公告</p>
    <div v-else class="announcement-list">
      <button
        v-for="announcement in announcements"
        :key="announcement.id"
        type="button"
        class="announcement-item"
        aria-haspopup="dialog"
        :aria-controls="showDetail ? dialogId : undefined"
        :aria-label="'查看公告：' + announcement.title"
        @click="showAnnouncementDetail(announcement)"
      >
        <span class="announcement-title">{{ announcement.title }}</span>
        <span class="announcement-item-meta">
          <time :datetime="announcement.createdAt">{{ formatDate(announcement.createdAt) }}</time>
          <span class="announcement-tags">
            <span v-if="announcement.isTop" class="announcement-tag is-top">置顶</span>
            <span class="announcement-tag is-type">{{ announcement.typeName }}</span>
          </span>
        </span>
      </button>
    </div>

    <Teleport to="body">
      <div v-if="showDetail && selectedAnnouncement" class="modal-overlay" data-lenis-prevent @click.self="closeDetail">
        <div :id="dialogId" ref="dialogRef" class="modal-container" role="dialog" aria-modal="true" :aria-labelledby="dialogTitleId" tabindex="-1">
          <div class="modal-header">
            <div class="modal-heading">
              <span class="modal-eyebrow"><Icon name="bell" size="14" />公告</span>
              <span v-if="selectedAnnouncement.isTop" class="announcement-tag is-top">置顶</span>
              <span class="announcement-tag is-type">{{ selectedAnnouncement.typeName }}</span>
            </div>
            <button ref="closeButtonRef" type="button" class="close-btn" aria-label="关闭公告" @click="closeDetail"><Icon name="close" size="18" /></button>
          </div>
          <div ref="modalBodyRef" class="modal-body">
            <h2 :id="dialogTitleId" class="modal-title">{{ selectedAnnouncement.title }}</h2>
            <div class="modal-content-text rich-content" v-html="safeContent"></div>
            <div class="modal-meta">
              <span v-if="selectedAnnouncement.createdAt">发布于 <time :datetime="selectedAnnouncement.createdAt">{{ formatDateTime(selectedAnnouncement.createdAt) }}</time></span>
              <span class="meta-views"><Icon name="eye" size="14" />{{ selectedAnnouncement.viewCount || 0 }} 次浏览</span>
              <span v-if="selectedAnnouncement.startTime" class="validity-meta">开始 <time :datetime="selectedAnnouncement.startTime">{{ formatDateTime(selectedAnnouncement.startTime) }}</time></span>
              <span v-if="selectedAnnouncement.endTime" class="validity-meta">结束 <time :datetime="selectedAnnouncement.endTime">{{ formatDateTime(selectedAnnouncement.endTime) }}</time></span>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.announcement-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $gap-sm;
  margin-bottom: 8px;
}

.announcement-header .card-title {
  min-width: 0;
  margin: 0;
}

.announcement-badge {
  width: 28px;
  height: 28px;
  justify-content: center;
  flex-shrink: 0;
  padding: 0;
}

.refresh-btn,
.close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-subtle);
  border-radius: 8px;
  transition: color 0.2s ease, background-color 0.2s ease;

  &:hover:not(:disabled) {
    background: var(--bg-hover);
    color: var(--color-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--state-primary-border);
    outline-offset: 2px;
  }
}

.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.announcement-empty {
  padding: 12px 0 4px;
  margin: 0;
  color: var(--text-subtle);
  font-size: 0.8125rem;
}

.announcement-list {
  display: flex;
  flex-direction: column;
}

.announcement-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-width: 0;
  padding: 12px 0;
  text-align: left;
  border-bottom: 1px solid var(--border-light);

  &:last-child { border-bottom: 0; padding-bottom: 2px; }
  &:hover .announcement-title { color: var(--color-primary); }
  &:focus-visible { outline: 2px solid var(--state-primary-border); outline-offset: 3px; border-radius: 4px; }
}

.announcement-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.6;
  color: var(--text-title);
}

.announcement-item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px 8px;
  font-size: 0.6875rem;
  color: var(--text-subtle);
}

.announcement-tags,
.modal-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.announcement-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 500;
  line-height: 1.5;
  white-space: nowrap;

  &.is-top { background: rgba(var(--color-secondary-rgb), 0.1); color: var(--color-secondary); }
  &.is-type { background: var(--state-primary-bg); color: var(--color-primary); }
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--overlay-bg);
  backdrop-filter: blur(3px);
}

.modal-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 600px;
  max-height: min(85dvh, 760px);
  overflow: hidden;
  background: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--border-soft);
  border-radius: $card-radius;
  box-shadow: var(--shadow-modal);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $gap-sm;
  flex-shrink: 0;
  padding: 20px 24px 0;
}

.modal-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 4px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-primary);
}

.modal-body {
  min-height: 0;
  padding: 12px 24px 24px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.modal-title {
  margin: 0 0 16px;
  font-family: inherit;
  font-size: 1.375rem;
  font-weight: 700;
  line-height: 1.5;
  color: var(--text-title);
  overflow-wrap: anywhere;
}

.modal-content-text {
  font-size: 0.9375rem;
  line-height: 1.8;
}

.modal-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid var(--border-light);
  color: var(--text-subtle);
  font-size: 0.75rem;
  line-height: 1.6;
}

.meta-views {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.validity-meta { width: 100%; }

@include respond(sm) {
  .modal-overlay { padding: 12px; }
  .modal-container { max-height: calc(100dvh - 24px); }
  .modal-header { padding: 16px 16px 0; }
  .modal-body { padding: 12px 16px 20px; }
  .modal-title { font-size: 1.1875rem; }
  .meta-views { margin-left: 0; }
}
</style>
