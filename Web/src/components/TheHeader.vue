<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import theme from '../utils/theme.ts'
import { useUserStore } from '../stores/user'

const router = useRouter()
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
</script>

<template>
  <header class="header">
    <div class="header-content flex flex-ac flex-sb">
      <div class="logo" @click="navigateTo('/')">
        <h2>LiuTech</h2>
      </div>
      
      <!-- 桌面端导航 -->
      <nav class="desktop-nav">
        <ul class="flex">
          <li><router-link to="/" exact class="link transition">首页</router-link></li>
          <li><router-link to="/posts" class="link transition">全部文章</router-link></li>
          <li><router-link to="/create" class="link transition">发布文章</router-link></li>
          <li><router-link to="/about" class="link transition">关于我</router-link></li>
        </ul>
      </nav>
      

      <div class="flex flex-ac gap-16">
        <!-- 用户信息区域 -->
        <div class="user-section">
          <!-- 已登录状态 -->
          <div v-if="userStore.isLoggedIn" class="user-info flex flex-ac gap-8" @click="toggleUserMenu">
            <div class="user-avatar">
              <img v-if="userStore.avatar" :src="userStore.avatar" :alt="userStore.username" class="img" />
              <div v-else class="default-avatar flex flex-ct">{{ userStore.username?.charAt(0).toUpperCase() }}</div>
            </div>
            <div class="flex flex-col">
              <span class="username font-medium">{{ userStore.username }}</span>
              <span class="points text-sm text-muted">{{ userStore.points }}积分</span>
            </div>
          </div>
          
          <!-- 未登录状态 -->
          <button v-else class="login-btn flex flex-ac gap-8 transition hover-bg rounded p-8" @click="navigateTo('/login')">
            <span class="login-icon">👤</span>
            <span>登录</span>
          </button>
          
          <!-- 用户下拉菜单 -->
          <div class="user-menu card" :class="{ 'is-open': isUserMenuOpen }">
            <ul class="user-menu-list">
              <li @click="navigateTo('/profile')" class="p-12 hover-bg transition">📝 个人资料</li>
              <li @click="navigateTo('/my-posts')" class="p-12 hover-bg transition">📚 我的文章</li>
              <li @click="navigateTo('/drafts')" class="p-12 hover-bg transition">📄 草稿箱</li>
              <li @click="navigateTo('/settings')" class="p-12 hover-bg transition">⚙️ 设置</li>
              <li @click="handleLogout" class="logout-item p-12 hover-bg transition border-t">🚪 退出登录</li>
            </ul>
          </div>
        </div>
        
        <!-- 主题切换按钮 -->
        <button @click="theme.toggle" class="theme-toggle-btn rounded transition hover-bg p-8">
          {{ theme.current.value === 'light' ? '🌙' : '☀️' }}
        </button>
      </div>
     
      
      <!-- 移动端菜单按钮 -->
      <button class="mobile-menu-btn flex flex-col gap-4 p-8" @click="toggleMenu">
        <span class="menu-line"></span>
        <span class="menu-line"></span>
        <span class="menu-line"></span>
      </button>
      
      <!-- 移动端菜单 -->
      <div class="mobile-menu card" :class="{ 'is-open': isMenuOpen }">
        <ul class="list">
          <li @click="navigateTo('/')" class="list-item p-16 hover-bg transition border-b">🏠 首页</li>
          <li @click="navigateTo('/posts')" class="list-item p-16 hover-bg transition border-b">📚 全部文章</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/create')" class="list-item p-16 hover-bg transition border-b">✍️ 发布文章</li>
          <li @click="navigateTo('/about')" class="list-item p-16 hover-bg transition border-b">👤 关于我</li>
          <li v-if="!userStore.isLoggedIn" @click="navigateTo('/login')" class="list-item p-16 hover-bg transition border-b">🔑 登录</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/profile')" class="list-item p-16 hover-bg transition border-b">📝 个人资料</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/my-posts')" class="list-item p-16 hover-bg transition border-b">📚 我的文章</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/drafts')" class="list-item p-16 hover-bg transition border-b">📄 草稿箱</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/settings')" class="list-item p-16 hover-bg transition border-b">⚙️ 设置</li>
          <li v-if="userStore.isLoggedIn" @click="handleLogout" class="logout-item list-item p-16 hover-bg transition text-primary">🚪 退出登录</li>
        </ul>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  background: var(--bg-color);
  border-bottom: 1px solid var(--border-color);
  z-index: 100;
  height: 70px;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  padding: 0 20px;
}

.logo {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary-color);
  text-decoration: none;
}

.desktop-nav ul {
  gap: 30px;
}

.desktop-nav a {
  color: var(--text-color);
  text-decoration: none;
  font-weight: 500;
  position: relative;
  padding: 8px 0;
}

.desktop-nav a.router-link-exact-active {
  color: var(--primary-color);
}

.desktop-nav a.router-link-exact-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: var(--primary-color);
}

.theme-toggle-btn {
  font-size: 1.2rem;
}

.mobile-menu-btn {
  display: none;
  width: 30px;
  height: 20px;
}

.menu-line {
  width: 100%;
  height: 2px;
  background-color: var(--text-color);
}

.mobile-menu {
  display: none;
  position: absolute;
  top: 70px;
  left: 0;
  width: 100%;
  transform: translateY(-100%);
  opacity: 0;
  transition: transform 0.3s, opacity 0.3s;
  z-index: 99;
}

.mobile-menu.is-open {
  transform: translateY(0);
  opacity: 1;
}

.logout-item {
  color: #f56565 !important;
}

.user-section {
  position: relative;
}

.login-btn {
  background: var(--primary-color);
  color: white;
}

.login-btn:hover {
  background: var(--secondary-color);
  transform: translateY(-1px);
}

.login-icon {
  font-size: 16px;
}

.user-info {
  cursor: pointer;
}

.user-info:hover {
  background-color: var(--hover-color);
}

.user-avatar {
  width: 35px;
  height: 35px;
  border-radius: 50%;
  background: var(--primary-color);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.default-avatar {
  width: 100%;
  height: 100%;
  color: white;
  font-weight: 600;
  font-size: 14px;
}

.user-menu {
  position: absolute;
  top: 100%;
  right: 0;
  min-width: 200px;
  z-index: 1000;
  opacity: 0;
  visibility: hidden;
  transform: translateY(-10px);
  transition: all 0.3s;
}

.user-menu.is-open {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.user-avatar-large {
  width: 40px;
  height: 40px;
  background: var(--primary-color);
}

.user-avatar-large img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar-large .default-avatar {
  color: white;
  font-weight: 600;
  font-size: 18px;
}

@media (max-width: 768px) {
  .desktop-nav {
    display: none;
  }

  .mobile-menu-btn {
    display: flex;
  }

  .mobile-menu {
    display: block;
  }

  .user-menu {
    right: -10px;
    width: 220px;
  }

  .logo {
    font-size: 1.3rem;
  }
}
</style>