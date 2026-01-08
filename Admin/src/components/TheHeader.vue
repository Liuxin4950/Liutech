<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import theme from '../utils/theme.ts'
import { useUserStore } from '../stores/user'
import {
  UserOutlined,
  LogoutOutlined,
  DownOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const isUserMenuOpen = ref(false)

/**
 * 切换用户菜单显示状态
 */
const toggleUserMenu = () => {
  isUserMenuOpen.value = !isUserMenuOpen.value
}

/**
 * 处理用户登出
 */
const handleLogout = () => {
  userStore.logout()
  isUserMenuOpen.value = false
  router.push('/login')
}

/**
 * 点击外部区域关闭菜单
 */
const handleClickOutside = (event: Event) => {
  const target = event.target as HTMLElement
  if (!target.closest('.user-dropdown') && !target.closest('.user-btn')) {
    isUserMenuOpen.value = false
  }
}

// 生命周期钩子
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <a-layout-header class="admin-header">
    <div class="header-content">
      <!-- Logo -->
      <div class="logo">
        <h2>LiuTech 管理后台</h2>
      </div>

      <!-- 右侧操作区 -->
      <div class="header-right">
        <a-space>
          <!-- 主题切换按钮 -->
          <a-button type="text" @click="theme.toggle" class="theme-btn">
            {{ theme.current.value === 'light' ? '🌙' : '☀️' }}
          </a-button>

          <!-- 用户信息 -->
          <a-dropdown v-if="userStore.isLoggedIn" class="user-dropdown">
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile">
                  <UserOutlined />
                  个人资料
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
            <a-button type="text" class="user-btn">
              <UserOutlined />
              {{ userStore.username || 'Admin' }}
              <DownOutlined />
            </a-button>
          </a-dropdown>

          <!-- 未登录状态 -->
          <a-button v-else type="primary" @click="router.push('/login')">
            <UserOutlined />
            登录
          </a-button>
        </a-space>
      </div>
    </div>
  </a-layout-header>
</template>

<style scoped>
.admin-header {
  background: var(--bg-card);
  padding: 0;
  /* box-shadow: 0 2px 2px rgba(0, 0, 0, 0.1);  */
  border-bottom: 2px solid var(--border-light);

  height: 64px;
  line-height: 64px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  padding: 0 24px;
}

.logo h2 {
  margin: 0;
  color: var(--color-primary);
  font-size: 20px;
  font-weight: 600;
  white-space: nowrap;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-btn {
  color: var(--text-secondary);
}

.theme-btn {
  font-size: 16px;
}
</style>
