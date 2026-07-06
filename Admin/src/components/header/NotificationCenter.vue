<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { BellOutlined, MessageOutlined, NotificationOutlined, InboxOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { MessagesService } from '@/services/message'
import { AnnouncementsService } from '@/services/announcements'
import { useI18n } from '@/i18n'

const { t } = useI18n()

/**
 * Header 通知中心：合并展示待审核留言 + 最新公告
 * - 未读徽章：仅计待审核留言（status=0）
 * - 30 秒轮询一次，切换 tab 时立即刷新
 */
interface NotifyItem {
  key: string
  type: 'message' | 'announcement'
  title: string
  desc: string
  time: string
  path: string
}

const router = useRouter()
const open = ref(false)
const loading = ref(false)
const activeTab = ref<'all' | 'message' | 'announcement'>('all')

const pendingMessages = ref<any[]>([])
const recentAnnouncements = ref<any[]>([])

/** 未读数 = 待审核留言数 */
const unreadCount = computed(() => pendingMessages.value.length)

const items = computed<NotifyItem[]>(() => {
  const msgs: NotifyItem[] = pendingMessages.value.map((m) => ({
    key: `m-${m.id}`,
    type: 'message',
    title: t('notify.messageTitle', { name: m.nickname }),
    desc: m.content?.slice(0, 60) || '',
    time: m.createdAt || '',
    path: '/messages',
  }))
  const ans: NotifyItem[] = recentAnnouncements.value.map((a) => ({
    key: `a-${a.id}`,
    type: 'announcement',
    title: a.title || '公告',
    desc: (a.content || '').replace(/<[^>]+>/g, '').slice(0, 60),
    time: a.createdAt || '',
    path: '/announcements',
  }))
  const merged = [...msgs, ...ans].sort((a, b) => (a.time < b.time ? 1 : -1))

  if (activeTab.value === 'message') return merged.filter((it) => it.type === 'message')
  if (activeTab.value === 'announcement') return merged.filter((it) => it.type === 'announcement')
  return merged
})

async function loadData() {
  loading.value = true
  try {
    const [msgRes, annRes] = await Promise.all([
      MessagesService.getMessageList({ page: 1, size: 5, status: 0 }).catch(() => null),
      AnnouncementsService.getAnnouncementList({ current: 1, size: 5 }).catch(() => null),
    ])
    if (msgRes?.code === 200) pendingMessages.value = msgRes.data?.records || []
    if (annRes?.code === 200) recentAnnouncements.value = annRes.data?.records || []
  } finally {
    loading.value = false
  }
}

function formatTime(t2: string) {
  if (!t2) return ''
  const now = dayjs()
  const then = dayjs(t2)
  const diffMin = now.diff(then, 'minute')
  if (diffMin < 1) return t('common.justNow')
  if (diffMin < 60) return t('common.minutesAgo', { n: diffMin })
  if (diffMin < 60 * 24) return t('common.hoursAgo', { n: Math.floor(diffMin / 60) })
  return then.format('MM-DD HH:mm')
}

function pick(it: NotifyItem) {
  open.value = false
  router.push(it.path)
}

function goAll() {
  open.value = false
  router.push(activeTab.value === 'announcement' ? '/announcements' : '/messages')
}

let timer: number | null = null
onMounted(() => {
  loadData()
  timer = window.setInterval(loadData, 30_000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <a-popover
    v-model:open="open"
    trigger="click"
    placement="bottomRight"
    :arrow="false"
    overlay-class-name="lt-notify-popover"
    @open-change="(v: boolean) => v && loadData()"
  >
    <template #content>
      <div class="lt-notify">
        <header class="lt-notify__header">
          <span class="lt-notify__title">{{ t('notify.title') }}</span>
          <a class="lt-notify__mark" @click="loadData">{{ t('notify.refresh') }}</a>
        </header>

        <a-tabs v-model:active-key="activeTab" size="small" class="lt-notify__tabs">
          <a-tab-pane key="all" :tab="t('notify.tabAll')" />
          <a-tab-pane key="message">
            <template #tab>
              {{ t('notify.tabMessage') }}
              <a-badge v-if="unreadCount" :count="unreadCount" :offset="[4, -4]" />
            </template>
          </a-tab-pane>
          <a-tab-pane key="announcement" :tab="t('notify.tabAnnouncement')" />
        </a-tabs>

        <a-spin :spinning="loading">
          <div v-if="items.length" class="lt-notify__list">
            <div
              v-for="it in items"
              :key="it.key"
              class="lt-notify__item"
              @click="pick(it)"
            >
              <div class="lt-notify__icon" :class="`lt-notify__icon--${it.type}`">
                <MessageOutlined v-if="it.type === 'message'" />
                <NotificationOutlined v-else />
              </div>
              <div class="lt-notify__body">
                <div class="lt-notify__item-title">{{ it.title }}</div>
                <div class="lt-notify__item-desc">{{ it.desc }}</div>
                <div class="lt-notify__item-time">{{ formatTime(it.time) }}</div>
              </div>
            </div>
          </div>
          <div v-else class="lt-notify__empty">
            <InboxOutlined />
            <span>{{ t('notify.empty') }}</span>
          </div>
        </a-spin>

        <footer class="lt-notify__footer">
          <a @click="goAll">{{ t('notify.viewAll') }}</a>
        </footer>
      </div>
    </template>

    <a-tooltip :title="t('header.notifications')">
      <button type="button" class="lt-notify__trigger" :aria-label="t('header.notifications')">
        <a-badge :count="unreadCount" :dot="false" :offset="[-2, 2]" size="small">
          <BellOutlined />
        </a-badge>
      </button>
    </a-tooltip>
  </a-popover>
</template>

<style scoped>
.lt-notify__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--lt-radius-md);
  background: transparent;
  color: var(--lt-color-text-secondary);
  cursor: pointer;
  font-size: var(--lt-font-size-md);
  transition: var(--lt-motion-hover);
}
.lt-notify__trigger:hover {
  background: var(--lt-color-hover-bg);
  color: var(--lt-color-primary);
}

.lt-notify {
  width: 360px;
  display: flex;
  flex-direction: column;
}

.lt-notify__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lt-space-md) var(--lt-space-lg);
  border-bottom: 1px solid var(--lt-color-border-secondary);
}

.lt-notify__title {
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
}
.lt-notify__mark {
  color: var(--lt-color-primary);
  font-size: var(--lt-font-size-xs);
  cursor: pointer;
}

.lt-notify__tabs {
  padding: 0 var(--lt-space-md);
}
.lt-notify__tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.lt-notify__list {
  max-height: 360px;
  overflow-y: auto;
}

.lt-notify__item {
  display: flex;
  gap: var(--lt-space-md);
  padding: var(--lt-space-md) var(--lt-space-lg);
  cursor: pointer;
  border-bottom: 1px solid var(--lt-color-border-secondary);
  transition: background var(--lt-duration-fast) var(--lt-ease-in-out);
}
.lt-notify__item:last-child { border-bottom: none; }
.lt-notify__item:hover { background: var(--lt-color-hover-bg); }

.lt-notify__icon {
  width: 32px;
  height: 32px;
  border-radius: var(--lt-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  font-size: var(--lt-font-size-md);
}
.lt-notify__icon--message {
  color: var(--lt-color-warning);
  background: var(--lt-color-warning-bg);
}
.lt-notify__icon--announcement {
  color: var(--lt-color-primary);
  background: var(--lt-color-primary-bg);
}

.lt-notify__body { flex: 1; min-width: 0; }
.lt-notify__item-title {
  font-size: var(--lt-font-size-base);
  color: var(--lt-color-text);
  font-weight: var(--lt-font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lt-notify__item-desc {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-secondary);
  margin-top: var(--lt-space-2xs);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.lt-notify__item-time {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
  margin-top: var(--lt-space-xs);
}

.lt-notify__empty {
  padding: var(--lt-space-3xl) var(--lt-space-lg);
  text-align: center;
  color: var(--lt-color-text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lt-space-sm);
  font-size: var(--lt-font-size-sm);
}
.lt-notify__empty .anticon { font-size: var(--lt-font-size-3xl); }

.lt-notify__footer {
  padding: var(--lt-space-sm) var(--lt-space-lg);
  border-top: 1px solid var(--lt-color-border-secondary);
  text-align: center;
  background: var(--lt-color-bg-spotlight);
}
.lt-notify__footer a {
  color: var(--lt-color-primary);
  font-size: var(--lt-font-size-sm);
  cursor: pointer;
}
</style>

<style>
.lt-notify-popover .ant-popover-inner {
  padding: 0 !important;
  overflow: hidden;
}
</style>
