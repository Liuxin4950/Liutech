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
    <div class="container">
      <div class="logo" @click="navigateTo('/')">
        <h1>MyBlog</h1>
      </div>
      
      <!-- 桌面端导航 -->
      <nav class="desktop-nav">
        <ul>
          <li><router-link to="/">首页</router-link></li>
          <li v-if="!userStore.isLoggedIn"><router-link to="/login">登录</router-link></li>
        </ul>
      </nav>
      
      <!-- 用户信息区域 -->
      <div v-if="userStore.isLoggedIn" class="user-section">
        <div class="user-info" @click="toggleUserMenu">
          <div class="user-avatar">
            <img v-if="userStore.avatar" :src="userStore.avatar" :alt="userStore.username" />
            <span v-else class="avatar-placeholder">{{ userStore.username.charAt(0).toUpperCase() }}</span>
          </div>
          <span class="username">{{ userStore.username }}</span>
          <span class="dropdown-arrow">▼</span>
        </div>
        
        <!-- 用户下拉菜单 -->
        <div class="user-menu" :class="{ 'is-open': isUserMenuOpen }">
          <div class="user-menu-header">
            <div class="user-avatar-large">
              <img v-if="userStore.avatar" :src="userStore.avatar" :alt="userStore.username" />
              <span v-else class="avatar-placeholder">{{ userStore.username.charAt(0).toUpperCase() }}</span>
            </div>
            <div class="user-details">
              <div class="username">{{ userStore.username }}</div>
              <div class="points">积分: {{ userStore.points }}</div>
            </div>
          </div>
          <ul class="user-menu-list">
            <li @click="navigateTo('/profile')">个人资料</li>
            <li @click="navigateTo('/settings')">设置</li>
            <li @click="handleLogout" class="logout-item">退出登录</li>
          </ul>
        </div>
      </div>
      
      <!-- 主题切换按钮 -->
      <button @click="theme.toggle" class="theme-toggle-btn">
        {{ theme.current === 'light' ? '🌙' : '☀️' }}
      </button>
      
      <!-- 移动端菜单按钮 -->
      <button class="mobile-menu-btn" @click="toggleMenu">
        <span></span>
        <span></span>
        <span></span>
      </button>
      
      <!-- 移动端菜单 -->
      <div class="mobile-menu" :class="{ 'is-open': isMenuOpen }">
        <ul>
          <li @click="navigateTo('/')">首页</li>
          <li v-if="!userStore.isLoggedIn" @click="navigateTo('/login')">登录</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/profile')">个人资料</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/settings')">设置</li>
          <li v-if="userStore.isLoggedIn" @click="handleLogout" class="logout-item">退出登录</li>
        </ul>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  background-color: var(--bg-color);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
  transition: background-color 0.3s;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  cursor: pointer;
}

.logo h1 {
  font-size: 1.5rem;
  margin: 0;
  color: var(--primary-color);
}

.desktop-nav ul {
  display: flex;
  list-style: none;
  margin: 0;
  padding: 0;
  gap: 30px;
}

.desktop-nav a {
  color: var(--text-color);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.3s;
  padding: 8px 0;
  position: relative;
}

.desktop-nav a:hover,
.desktop-nav a.router-link-active {
  color: var(--primary-color);
}

.desktop-nav a.router-link-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background-color: var(--primary-color);
}

.theme-toggle-btn {
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  transition: background-color 0.3s;
}

.theme-toggle-btn:hover {
  background-color: var(--hover-color);
}

.mobile-menu-btn {
  display: none;
  flex-direction: column;
  justify-content: space-between;
  width: 30px;
  height: 20px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.mobile-menu-btn span {
  width: 100%;
  height: 2px;
  background-color: var(--text-color);
  transition: all 0.3s;
}

.mobile-menu {
  display: none;
  position: absolute;
  top: 70px;
  left: 0;
  width: 100%;
  background-color: var(--bg-color);
  box-shadow: 0 5px 10px rgba(0, 0, 0, 0.1);
  transform: translateY(-100%);
  opacity: 0;
  transition: transform 0.3s, opacity 0.3s;
  z-index: 99;
}

.mobile-menu.is-open {
  transform: translateY(0);
  opacity: 1;
}

.mobile-menu ul {
  list-style: none;
  margin: 0;
  padding: 20px;
}

.mobile-menu li {
  padding: 15px 0;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-color);
  cursor: pointer;
}

.mobile-menu li:last-child {
  border-bottom: none;
}

.mobile-menu .logout-item {
  color: #f56565;
}

/* 用户信息区域样式 */
.user-section {
  position: relative;
  margin-left: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: var(--hover-color);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--primary-color);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  color: white;
  font-weight: 600;
  font-size: 14px;
}

.user-info .username {
  color: var(--text-color);
  font-weight: 500;
  font-size: 14px;
}

.dropdown-arrow {
  color: var(--text-color);
  font-size: 10px;
  transition: transform 0.3s;
}

.user-info:hover .dropdown-arrow {
  transform: rotate(180deg);
}

/* 用户下拉菜单样式 */
.user-menu {
  position: absolute;
  top: 100%;
  right: 0;
  width: 240px;
  background-color: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  transform: translateY(-10px);
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s ease;
  z-index: 1000;
}

.user-menu.is-open {
  transform: translateY(0);
  opacity: 1;
  visibility: visible;
}

.user-menu-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar-large {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--primary-color);
}

.user-avatar-large img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar-large .avatar-placeholder {
  color: white;
  font-weight: 600;
  font-size: 18px;
}

.user-details .username {
  color: var(--text-color);
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 4px;
}

.user-details .points {
  color: var(--text-color);
  opacity: 0.7;
  font-size: 12px;
}

.user-menu-list {
  list-style: none;
  margin: 0;
  padding: 8px 0;
}

.user-menu-list li {
  padding: 12px 16px;
  color: var(--text-color);
  cursor: pointer;
  transition: background-color 0.3s;
  font-size: 14px;
}

.user-menu-list li:hover {
  background-color: var(--hover-color);
}

.user-menu-list .logout-item {
  color: #f56565;
  border-top: 1px solid var(--border-color);
}

.user-menu-list .logout-item:hover {
  background-color: rgba(245, 101, 101, 0.1);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .desktop-nav {
    display: none;
  }
  
  .user-section {
    display: none;
  }
  
  .mobile-menu-btn {
    display: flex;
  }
  
  .mobile-menu {
    display: block;
  }
}
</style>