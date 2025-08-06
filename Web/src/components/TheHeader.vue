<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
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
  <header class="bg-color border-b sticky top-0 z-100 h-70">
    <div class="content mx-auto h-full px-20 flex flex-ac flex-sb">
      <div class="text-xl font-bold text-primary cursor-pointer">
        <h2>LiuTech</h2>
      </div>
      
      <!-- 桌面端导航 -->
      <nav class="desktop-nav">
        <ul class="flex gap-30">
          <li><router-link to="/" exact class="nav-link transition">首页</router-link></li>
          <li><router-link to="/categories" class="nav-link transition">分类</router-link></li>
          <li><router-link to="/tags" class="nav-link transition">标签</router-link></li>
          <li><router-link to="/archive" class="nav-link transition">归档</router-link></li>

          <!-- <li><router-link to="/posts" class="nav-link transition">全部文章</router-link></li>
          <li><router-link to="/create" class="nav-link transition">发布文章</router-link></li> -->
          <li><router-link to="/about" class="nav-link transition">关于我</router-link></li>
        
        </ul>
      </nav>
      

      <div class="flex flex-ac gap-16">
        <!-- 用户信息区域 -->
        <div class="relative user-menu-container">
          <!-- 已登录状态 -->
          <div v-if="userStore.isLoggedIn" class="flex flex-ac gap-8 cursor-pointer hover-bg p-8 rounded transition" @click="toggleUserMenu">
            <div class="w-35 h-35 rounded-full bg-primary flex flex-ct">
              <img v-if="userStore.avatar" :src="userStore.avatar" :alt="userStore.username" class="w-full h-full object-cover rounded-full" />
              <div v-else class="text-white font-semibold text-sm">{{ userStore.username?.charAt(0).toUpperCase() }}</div>
            </div>
            <div class="flex flex-col">
              <span class="font-medium">{{ userStore.username }}</span>
              <span class="text-sm text-muted">{{ userStore.points }}积分</span>
            </div>
          </div>
          
          <!-- 未登录状态 -->
          <button v-else class="bg-primary text-white flex flex-ac gap-8 transition hover-bg rounded p-8 hover-lift" @click="navigateTo('/login')">
            <span class="text-base">👤</span>
            <span>登录</span>
          </button>
          
          <!-- 用户下拉菜单 -->
          <div class="absolute top-full right-0 min-w-200 z-1000 card transition-all" :class="{ 'opacity-100 visible translate-y-0': isUserMenuOpen, 'opacity-0 invisible -translate-y-10': !isUserMenuOpen }" @click.stop>
            <ul class="list">
              <li @click="navigateTo('/profile')" class="p-12 hover-bg transition cursor-pointer">📝 个人资料</li>
              <li @click="navigateTo('/my-posts')" class="p-12 hover-bg transition cursor-pointer">📚 我的文章</li>
              <li @click="navigateTo('/drafts')" class="p-12 hover-bg transition cursor-pointer">📄 草稿箱</li>
              <li @click="navigateTo('/settings')" class="p-12 hover-bg transition cursor-pointer">⚙️ 设置</li>
              <li @click="handleLogout" class="p-12 hover-bg transition cursor-pointer border-t text-danger">🚪 退出登录</li>
            </ul>
          </div>
        </div>
        
        <!-- 主题切换按钮 -->
        <button @click="theme.toggle" class="rounded transition hover-bg p-8 text-lg">
          {{ theme.current.value === 'light' ? '🌙' : '☀️' }}
        </button>
      </div>
     
      
      <!-- 移动端菜单按钮 -->
      <button class="mobile-menu-btn flex flex-col gap-4 p-8 w-30 h-20" @click="toggleMenu">
        <span class="w-full h-2 bg-text"></span>
        <span class="w-full h-2 bg-text"></span>
        <span class="w-full h-2 bg-text"></span>
      </button>
      
      <!-- 移动端菜单 -->
      <div class="absolute top-70 left-0 w-full card z-99 transition-all mobile-menu" 
           :class="{ 'translate-y-0 opacity-100': isMenuOpen, '-translate-y-full opacity-0': !isMenuOpen }" 
           :style="{ pointerEvents: isMenuOpen ? 'auto' : 'none' }" 
           @click.stop>
        <ul class="list">
          <li @click="navigateTo('/')" class="p-16 hover-bg transition border-b cursor-pointer">🏠 首页</li>
          <li @click="navigateTo('/posts')" class="p-16 hover-bg transition border-b cursor-pointer">📚 全部文章</li>
          <li @click="navigateTo('/categories')" class="p-16 hover-bg transition border-b cursor-pointer">📂 分类</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/create')" class="p-16 hover-bg transition border-b cursor-pointer">✍️ 发布文章</li>
          <li @click="navigateTo('/about')" class="p-16 hover-bg transition border-b cursor-pointer">👤 关于我</li>
          <li v-if="!userStore.isLoggedIn" @click="navigateTo('/login')" class="p-16 hover-bg transition border-b cursor-pointer">🔑 登录</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/profile')" class="p-16 hover-bg transition border-b cursor-pointer">📝 个人资料</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/my-posts')" class="p-16 hover-bg transition border-b cursor-pointer">📚 我的文章</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/drafts')" class="p-16 hover-bg transition border-b cursor-pointer">📄 草稿箱</li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/settings')" class="p-16 hover-bg transition border-b cursor-pointer">⚙️ 设置</li>
          <li v-if="userStore.isLoggedIn" @click="handleLogout" class="p-16 hover-bg transition text-danger cursor-pointer">🚪 退出登录</li>
        </ul>
      </div>
    </div>
  </header>
</template>

<style scoped>
/* 导航链接样式 */
.nav-link {
  color: var(--text-color);
  text-decoration: none;
  font-weight: 500;
  position: relative;
  padding: 8px 0;
}

.nav-link.router-link-exact-active {
  color: var(--primary-color);
}

.nav-link.router-link-exact-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: var(--primary-color);
}

/* 默认隐藏移动端菜单按钮 */
.mobile-menu-btn {
  display: none;
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .desktop-nav {
    display: none;
  }

  .mobile-menu-btn {
    display: flex;
  }
}
</style>