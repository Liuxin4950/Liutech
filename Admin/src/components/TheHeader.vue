<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import theme from '../utils/theme.ts'
import { useUserStore } from '../stores/user'
import { UserOutlined, LogoutOutlined, DownOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isMenuOpen = ref(false)
const isUserMenuOpen = ref(false)

/**
 * 切换移动端菜单显示状态
 */
const toggleMenu = () => {
  isMenuOpen.value = !isMenuOpen.value;
};

/**
 * 导航到指定路由并关闭菜单
 * @param path 路由路径
 */
const navigateTo = (path: string) => {
  router.push(path)
  isMenuOpen.value = false
  isUserMenuOpen.value = false
}

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
  router.push('/')
}

/**
 * 计算导航激活态：
 * - 在文章详情页（post-detail）优先根据 route.query.from 映射高亮
 *   from 映射：categories/tags/archive -> 同名；home/posts/my-posts -> home
 * - 其它情况下回退到 route.meta.section
 */
const isActive = (section: string) => {
  const routeName = (route.name as string) || ''
  const from = (route.query.from as string) || ''
  if (routeName === 'post-detail') {
    const map: Record<string, string> = {
      categories: 'categories',
      tags: 'tags',
      archive: 'archive',
      home: 'home',
      posts: 'home',
      'my-posts': 'home'
    }
    const prefer = map[from]
    if (prefer) return prefer === section
  }
  return (route.meta?.section as string) === section
}

/**
 * 点击外部区域关闭菜单
 */
const handleClickOutside = (event: Event) => {
  const target = event.target as HTMLElement
  
  // 只有点击在header外部时才关闭菜单
  if (!target.closest('header')) {
    if (isUserMenuOpen.value) {
      isUserMenuOpen.value = false
    }
    if (isMenuOpen.value) {
      isMenuOpen.value = false
    }
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
      <div class="logo">
        <h2>LiuTech 管理后台</h2>
      </div>
      <div class="header-right">
        <a-space>
          <!-- 主题切换按钮 -->
          <a-button type="text" @click="theme.toggle" class="theme-btn">
            {{ theme.current.value === 'light' ? '🌙' : '☀️' }}
          </a-button>
          
          <!-- 用户信息 -->
          <a-dropdown v-if="userStore.isLoggedIn">
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
          <a-button v-else type="primary" @click="navigateTo('/login')">
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
  background: #fff;
  padding: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
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
  color: #1890ff;
  font-size: 20px;
  font-weight: 600;
}

.user-btn {
  color: rgba(0, 0, 0, 0.65);
}

.theme-btn {
  font-size: 16px;
}

.nav-link.router-link-exact-active,
.nav-link.is-active {
  color: var(--color-primary);
}

.nav-link.router-link-exact-active::after,
.nav-link.is-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: var(--color-primary);
}

/* 默认隐藏移动端菜单按钮 */
.mobile-menu-btn {
  width: 40px;
  height: 30px;
  padding: 5px;
  display: none;
}
.mobile-menu-btn div{
  width: 100%;
  height: 3px;
  background-color: var(--text-main);
}
.mobile-menu {
  width: 100%;
  position: fixed;
  top: 70px;
  left: 0;
  background-color: var(--bg-main);
  li:hover{
    color: var(--color-primary);
  }
}

</style>