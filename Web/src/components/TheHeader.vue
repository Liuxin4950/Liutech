<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import theme from '../utils/theme.ts'
import { useUserStore } from '../stores/user'

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
  <header class="sticky top-0 z-100 ">
    <div class="content px-20 flex flex-ac flex-sb ">
      <div class="text-xl font-bold link text-primary">
        <h2>LiuTech</h2>
      </div>
      
      <!-- 桌面端导航 -->
      <nav class="desktop-nav">
        <ul class="flex gap-30">
          <li><router-link to="/" class="nav-link transition" :class="{ 'is-active': isActive('home') }">首页</router-link></li>
          <li><router-link to="/categories" class="nav-link transition" :class="{ 'is-active': isActive('categories') }">分类</router-link></li>
          <li><router-link to="/tags" class="nav-link transition" :class="{ 'is-active': isActive('tags') }">标签</router-link></li>
          <li><router-link to="/archive" class="nav-link transition" :class="{ 'is-active': isActive('archive') }">归档</router-link></li>
          <li><router-link to="/about" class="nav-link transition" :class="{ 'is-active': isActive('about') }">关于我</router-link></li>
        
        </ul>
      </nav>
      

      <div class="flex flex-ac gap-16 nav-user" >
        <!-- 用户信息区域 -->
        <div class="relative user-menu-container">
          <!-- 已登录状态 -->
          <div v-if="userStore.isLoggedIn" class="flex flex-ac gap-8 link rounded transition" @click="toggleUserMenu">
            <div class="user-avatar rounded-full bg-primary flex flex-ct link">
              <img v-if="userStore.avatar" :src="userStore.avatar" :alt="userStore.username" class="fit rounded-full" />
              <div v-else class="text-white font-semibold text-sm">{{ userStore.username?.charAt(0).toUpperCase() }}</div>
            </div>
            <div class="flex flex-col link">
              <span class="font-medium">{{ userStore.username }}</span>
              <span class="text-sm text-muted">{{ userStore.points }}积分</span>
            </div>
          </div>
          
          <!-- 未登录状态 -->
          <button v-else class=" text-white flex flex-ac gap-8 transition  rounded p-8 hover-lift" @click="navigateTo('/login')">
            <span class="text-base">👤</span>
            <span>登录</span>
          </button>
          
          <!-- 用户下拉菜单 -->
          <div v-show="isUserMenuOpen" class="avatar-menu absolute card transition bg-main" @click.stop>
            <ul class="list">
              <li @click="navigateTo('/profile')" class="transition link">个人资料</li>
              <li @click="navigateTo('/my-posts')" class="transition link">我的文章</li>
              <li @click="navigateTo('/drafts')" class="transition link">草稿箱</li>
              <li @click="navigateTo('/settings')" class="transition link">设置</li>
              <li @click="handleLogout" class="transition link border-t text-danger">退出登录</li>
            </ul>
          </div>
        </div>
        
        <!-- 主题切换按钮 -->
        <button @click="theme.toggle" class="rounded transition hover-bg p-8 text-lg">
          {{ theme.current.value === 'light' ? '🌙' : '☀️' }}
        </button>
      </div>
     
      
      <!-- 移动端菜单按钮 -->
      <button class="mobile-menu-btn flex flex-col flex-sb" @click="toggleMenu">
        <div class=""></div>
        <div class=""></div>
        <div class=""></div>

      </button>
      
      <!-- 移动端菜单 -->
      <div class="mobile-menu " 
          v-show="isMenuOpen"
           @click.stop>
        <ul class="list">
          <li @click="navigateTo('/')" class="p-16 hover-bg transition border-b link">🏠 首页</li>
          <li @click="navigateTo('/posts')" class="p-16 hover-bg transition border-b link">📚 全部文章</li>
          <li @click="navigateTo('/categories')" class="p-16 hover-bg transition border-b link">📂 分类</li>
          <li @click="navigateTo('/tags')" class="p-16 hover-bg transition border-b link">📂 标签</li>
          <li @click="navigateTo('/archive')" class="p-16 hover-bg transition border-b link">📂 归档</li>
          <li @click="navigateTo('/about')" class="p-16 hover-bg transition border-b link">👤 关于我</li>
        </ul>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
header{
  width: 100%;
  height: 70px;
  background-color: var(--bg-main);
  box-shadow: var(--shadow-sm);
}
header > div{
  height: 70px;
}

.user-avatar{
  width: 40px;
  height: 40px;
  cursor: pointer;
}

ul,ol {
  list-style: none;
  padding: 0;
  margin: 0;
}
.avatar-menu{
  top: 70px;
  width: 140px; 
  z-index: 99;
  bar
  li{
    margin-bottom: 12px;
    cursor: pointer;
  }
  li:last-child {
    margin-bottom: 0;
  }
  li:hover{
    color: var(--color-primary);
  }
}

/* 导航链接样式 */
.nav-link {
  color: var(--text-main);
  text-decoration: none;
  font-weight: 500;
  position: relative;
  padding: 8px 0;
  cursor: pointer;
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

/* 移动端响应式 */
@media (max-width: 768px) {
  .desktop-nav {
    display: none;
  }

  .mobile-menu-btn {
    display: flex;
  }
  .nav-user{
    display: none;
  }
}
</style>