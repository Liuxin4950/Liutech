<script setup lang="ts">
import { inject, ref, watch, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DashboardOutlined,
  FileTextOutlined,
  FolderOutlined,
  TagsOutlined,
  TeamOutlined,
  NotificationOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  HistoryOutlined,
  CloudOutlined,
  SettingOutlined,
  PictureOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()

// 从父组件注入折叠状态
const collapsed = inject<Ref<boolean>>('sidebarCollapsed')!

// 菜单配置 - 支持二级折叠
const menuItems = [
  {
    key: 'dashboard',
    icon: DashboardOutlined,
    label: '仪表盘',
    path: '/'
  },
  // 内容管理分组
  {
    key: 'content',
    icon: FileTextOutlined,
    label: '内容管理',
    children: [
      { key: 'posts', icon: FileTextOutlined, label: '文章管理', path: '/posts' },
      { key: 'categories', icon: FolderOutlined, label: '分类管理', path: '/categories' },
      { key: 'tags', icon: TagsOutlined, label: '标签管理', path: '/tags' }
    ]
  },
  // 用户管理
  {
    key: 'users',
    icon: TeamOutlined,
    label: '用户管理',
    path: '/users'
  },
  // 系统管理分组
  {
    key: 'system',
    icon: SettingOutlined,
    label: '系统管理',
    children: [
      { key: 'announcements', icon: NotificationOutlined, label: '公告管理', path: '/announcements' },
      { key: 'carousels', icon: PictureOutlined, label: '轮播图管理', path: '/carousels' },
      { key: 'logs', icon: HistoryOutlined, label: '操作日志', path: '/logs' },
      { key: 'music', icon: CloudOutlined, label: 'AI音乐', path: '/music' }
    ]
  }
]

// 获取当前选中的 key
const getSelectedKey = (): string[] => {
  const path = route.path
  if (path === '/' || path === '/dashboard') return ['dashboard']
  if (path.startsWith('/posts')) return ['posts']
  if (path.startsWith('/categories')) return ['categories']
  if (path.startsWith('/tags')) return ['tags']
  if (path.startsWith('/users')) return ['users']
  if (path.startsWith('/announcements')) return ['announcements']
  if (path.startsWith('/carousels')) return ['carousels']
  if (path.startsWith('/logs')) return ['logs']
  if (path.startsWith('/music')) return ['music']
  return ['dashboard']
}

// 获取当前展开的 submenu keys
const getOpenKeys = (): string[] => {
  const path = route.path
  if (path.startsWith('/posts') || path.startsWith('/categories') || path.startsWith('/tags')) {
    return ['content']
  }
  if (path.startsWith('/announcements') || path.startsWith('/carousels') || path.startsWith('/logs') || path.startsWith('/music')) {
    return ['system']
  }
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
const findMenuItem = (key: string, items = menuItems): any => {
  for (const item of items) {
    if (item.key === key) return item
    if (item.children) {
      const found = findMenuItem(key, item.children)
      if (found) return found
    }
  }
  return null
}

// 切换折叠状态
const toggleCollapsed = () => {
  collapsed.value = !collapsed.value
}
</script>

<template>
  <div class="sidebar-content">
    <!-- 折叠按钮 -->
    <div class="collapse-trigger" @click="toggleCollapsed">
      <MenuUnfoldOutlined v-if="collapsed" />
      <MenuFoldOutlined v-else />
    </div>

    <!-- 菜单 -->
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
        <!-- 有子菜单的用 SubMenu -->
        <a-sub-menu v-if="item.children" :key="item.key">
          <template #icon><component :is="item.icon" /></template>
          <template #title>{{ item.label }}</template>
          <a-menu-item v-for="child in item.children" :key="child.key">
            <template #icon><component :is="child.icon" /></template>
            <span>{{ child.label }}</span>
          </a-menu-item>
        </a-sub-menu>
        <!-- 没有子菜单的用 MenuItem -->
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
}

.collapse-trigger {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-main);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s;
  border-bottom: 1px solid var(--border-light);
}

.collapse-trigger:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

.sidebar-menu {
  flex: 1;
  border-right: 0;
}

/* 深色模式覆盖 */
.sidebar-menu :deep(.ant-menu) {
  background: transparent;
}

.sidebar-menu :deep(.ant-menu-item) {
  color: var(--text-secondary);
  margin: 4px 8px;
  border-radius: 6px;
}

.sidebar-menu :deep(.ant-menu-item:hover) {
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

.sidebar-menu :deep(.ant-menu-item-selected) {
  color: var(--color-primary) !important;
  background: var(--color-primary-bg) !important;
}

.sidebar-menu :deep(.ant-menu-submenu-title) {
  color: var(--text-secondary);
  margin: 4px 8px;
  border-radius: 6px;
}

.sidebar-menu :deep(.ant-menu-submenu-title:hover) {
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

.sidebar-menu :deep(.ant-menu-submenu-open > .ant-menu-submenu-title) {
  color: var(--color-primary);
}

/* 子菜单样式 */
.sidebar-menu :deep(.ant-menu-sub) {
  background: transparent !important;
}

.sidebar-menu :deep(.ant-menu-item::after) {
  display: none;
}
</style>
