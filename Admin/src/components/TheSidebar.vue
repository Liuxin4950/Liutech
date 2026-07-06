<script setup lang="ts">
import { inject, ref, watch, computed, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DashboardOutlined,
  FileTextOutlined,
  FolderOutlined,
  TagsOutlined,
  TeamOutlined,
  NotificationOutlined,
  HistoryOutlined,
  SettingOutlined,
  PictureOutlined,
  RobotOutlined,
  MessageOutlined,
  CommentOutlined,
  DollarOutlined,
  CloudOutlined,
  CloudDownloadOutlined,
  FundOutlined,
  CloudServerOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import logoUrl from '@/assets/image/logo/logo.png'
import { useI18n } from '@/i18n'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

// 从父组件注入折叠状态
const collapsed = inject<Ref<boolean>>('sidebarCollapsed')!

// 菜单配置：label 用 i18n key，展示时由模板 t() 翻译
const menuItems = computed(() => [
  { key: 'dashboard', icon: DashboardOutlined, label: t('menu.dashboard'), path: '/' },
  {
    key: 'content', icon: FileTextOutlined, label: t('menu.content'),
    children: [
      { key: 'posts', icon: FileTextOutlined, label: t('menu.posts'), path: '/posts' },
      { key: 'categories', icon: FolderOutlined, label: t('menu.categories'), path: '/categories' },
      { key: 'tags', icon: TagsOutlined, label: t('menu.tags'), path: '/tags' },
      { key: 'comments', icon: CommentOutlined, label: t('menu.comments'), path: '/comments' },
    ],
  },
  {
    key: 'user-management', icon: TeamOutlined, label: t('menu.userManagement'),
    children: [
      { key: 'users', icon: TeamOutlined, label: t('menu.users'), path: '/users' },
      { key: 'points', icon: DollarOutlined, label: t('menu.points'), path: '/points' },
    ],
  },
  {
    key: 'operations', icon: FundOutlined, label: t('menu.operations'),
    children: [
      { key: 'announcements', icon: NotificationOutlined, label: t('menu.announcements'), path: '/announcements' },
      { key: 'carousels', icon: PictureOutlined, label: t('menu.carousels'), path: '/carousels' },
      { key: 'messages', icon: MessageOutlined, label: t('menu.messages'), path: '/messages' },
    ],
  },
  {
    key: 'media', icon: CloudServerOutlined, label: t('menu.media'),
    children: [
      { key: 'images', icon: PictureOutlined, label: t('menu.images'), path: '/images' },
      { key: 'resources', icon: CloudDownloadOutlined, label: t('menu.resources'), path: '/resources' },
      { key: 'music', icon: CloudOutlined, label: t('menu.music'), path: '/music' },
    ],
  },
  {
    key: 'ai-center', icon: ThunderboltOutlined, label: t('menu.aiCenter'),
    children: [
      { key: 'ai-models', icon: RobotOutlined, label: t('menu.aiModels'), path: '/ai-models' },
      { key: 'ai-settings', icon: SettingOutlined, label: t('menu.aiSettings'), path: '/ai-settings' },
    ],
  },
  {
    key: 'system', icon: SettingOutlined, label: t('menu.system'),
    children: [
      { key: 'logs', icon: HistoryOutlined, label: t('menu.logs'), path: '/logs' },
      { key: 'settings', icon: SettingOutlined, label: t('menu.systemSettings'), path: '/settings' },
    ],
  },
])

const getSelectedKey = (): string[] => {
  const path = route.path
  if (path === '/' || path === '/dashboard') return ['dashboard']
  if (path.startsWith('/posts')) return ['posts']
  if (path.startsWith('/categories')) return ['categories']
  if (path.startsWith('/tags')) return ['tags']
  if (path.startsWith('/comments')) return ['comments']
  if (path.startsWith('/users')) return ['users']
  if (path.startsWith('/points')) return ['points']
  if (path.startsWith('/announcements')) return ['announcements']
  if (path.startsWith('/carousels')) return ['carousels']
  if (path.startsWith('/messages')) return ['messages']
  if (path.startsWith('/images')) return ['images']
  if (path.startsWith('/resources')) return ['resources']
  if (path.startsWith('/music')) return ['music']
  if (path.startsWith('/ai-models')) return ['ai-models']
  if (path.startsWith('/ai-settings')) return ['ai-settings']
  if (path.startsWith('/logs')) return ['logs']
  if (path.startsWith('/settings')) return ['settings']
  return ['dashboard']
}

const getOpenKeys = (): string[] => {
  const path = route.path
  if (path.startsWith('/posts') || path.startsWith('/categories') || path.startsWith('/tags') || path.startsWith('/comments')) return ['content']
  if (path.startsWith('/users') || path.startsWith('/points')) return ['user-management']
  if (path.startsWith('/announcements') || path.startsWith('/carousels') || path.startsWith('/messages')) return ['operations']
  if (path.startsWith('/images') || path.startsWith('/resources') || path.startsWith('/music')) return ['media']
  if (path.startsWith('/ai-models') || path.startsWith('/ai-settings')) return ['ai-center']
  if (path.startsWith('/logs') || path.startsWith('/settings')) return ['system']
  return []
}

// 菜单状态
const menuSelectedKeys = ref<string[]>(getSelectedKey())
const menuOpenKeys = ref<string[]>(getOpenKeys())

// 监听路由变化，更新菜单状态
watch(() => route.path, () => {
  menuSelectedKeys.value = getSelectedKey()
  menuOpenKeys.value = getOpenKeys()
}, { immediate: true })

// 菜单点击处理
const handleMenuClick = ({ key }: { key: string }) => {
  const item = findMenuItem(key)
  if (item && item.path) {
    router.push(item.path)
  }
}

// SubMenu 展开/收起处理
const handleOpenChange = (keys: string[]) => {
  menuOpenKeys.value = keys
}

// 递归查找菜单项
const findMenuItem = (key: string, items: any[] = menuItems.value): any => {
  for (const item of items) {
    if (item.key === key) return item
    if (item.children) {
      const found = findMenuItem(key, item.children)
      if (found) return found
    }
  }
  return null
}
</script>

<template>
  <div class="sidebar-content">
    <div class="logo" :class="{ collapsed }" @click="router.push('/')">
      <img :src="logoUrl" alt="LiuTech 管理后台" class="logo-mark" />
      <h2 v-show="!collapsed">LiuTech 管理后台</h2>
    </div>

    <a-menu
      v-model:selectedKeys="menuSelectedKeys"
      v-model:openKeys="menuOpenKeys"
      mode="inline"
      theme="light"
      class="sidebar-menu"
      :inline-collapsed="collapsed"
      :inline-indent="24"
      @click="handleMenuClick"
      @openChange="handleOpenChange"
    >
      <template v-for="item in menuItems" :key="item.key">
        <a-sub-menu v-if="item.children" :key="item.key">
          <template #icon><component :is="item.icon" /></template>
          <template #title>{{ item.label }}</template>
          <a-menu-item v-for="child in item.children" :key="child.key">
            <template #icon><component :is="child.icon" /></template>
            <span>{{ child.label }}</span>
          </a-menu-item>
        </a-sub-menu>
        <a-menu-item v-if="!item.children" :key="item.key">
          <template #icon><component :is="item.icon" /></template>
          <span>{{ item.label }}</span>
        </a-menu-item>
      </template>
    </a-menu>
  </div>
</template>

<style scoped>
.sidebar-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lt-color-bg-container);
  border-right: 1px solid var(--lt-color-border-secondary);
}

.logo {
  width: 100%;
  height: var(--lt-size-header);
  padding: 0 var(--lt-space-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-sm);
  cursor: pointer;
  overflow: hidden;
  border-bottom: 1px solid var(--lt-color-border-secondary);
  transition: var(--lt-motion-hover);
}

.logo.collapsed {
  padding: 0;
  gap: 0;
}

.logo-mark {
  width: 32px;
  height: 32px;
  object-fit: contain;
  flex: 0 0 auto;
  border-radius: var(--lt-radius-md);
}

.logo h2 {
  margin: 0;
  color: var(--lt-color-primary);
  font-size: var(--lt-font-size-lg);
  font-weight: var(--lt-font-weight-semibold);
  white-space: nowrap;
  line-height: 1;
}

.collapse-trigger { display: none; }

.sidebar-menu {
  flex: 1;
  border-right: 0;
  padding: var(--lt-space-sm) var(--lt-space-xs) 0 0;
  background: transparent;
}

.sidebar-menu :deep(.ant-menu),
.sidebar-menu :deep(.ant-menu-sub) {
  background: transparent !important;
}

.sidebar-menu :deep(.ant-menu-item::after) {
  display: none;
}
</style>
