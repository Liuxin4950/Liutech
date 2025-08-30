<script setup lang="ts">
import { computed, inject, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DashboardOutlined,
  FileTextOutlined,
  FolderOutlined,
  TagsOutlined,
  TeamOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()

// 从父组件注入折叠状态
const collapsed = inject<Ref<boolean>>('sidebarCollapsed')!

// 当前选中的菜单项
const selectedKeys = computed(() => {
  const path = route.path
  if (path === '/' || path === '/dashboard') return ['dashboard']
  if (path.startsWith('/posts')) return ['posts']
  if (path.startsWith('/categories')) return ['categories']
  if (path.startsWith('/tags')) return ['tags']
  if (path.startsWith('/users')) return ['users']
  return ['dashboard']
})

// 菜单点击处理
const handleMenuClick = ({ key }: { key: string }) => {
  switch (key) {
    case 'dashboard':
      router.push('/')
      break
    case 'posts':
      router.push('/posts')
      break
    case 'categories':
      router.push('/categories')
      break
    case 'tags':
      router.push('/tags')
      break
    case 'users':
      router.push('/users')
      break
  }
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
      v-model:selectedKeys="selectedKeys"
      mode="inline"
      theme="light"
      class="sidebar-menu"
      :inline-collapsed="collapsed"
      @click="handleMenuClick"
    >
      <a-menu-item key="dashboard">
        <DashboardOutlined />
        <span>仪表盘</span>
      </a-menu-item>
      <a-menu-item key="posts">
        <FileTextOutlined />
        <span>文章管理</span>
      </a-menu-item>
      <a-menu-item key="categories">
        <FolderOutlined />
        <span>分类管理</span>
      </a-menu-item>
      <a-menu-item key="tags">
        <TagsOutlined />
        <span>标签管理</span>
      </a-menu-item>
      <a-menu-item key="users">
        <TeamOutlined />
        <span>用户管理</span>
      </a-menu-item>
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
  background: #f0f0f0;
  color: #666;
  cursor: pointer;
  transition: all 0.3s;
  border-bottom: 1px solid #e8e8e8;
}

.collapse-trigger:hover {
  background: #e6f7ff;
  color: #1890ff;
}

.sidebar-menu {
  flex: 1;
  border-right: 0;
}
</style>