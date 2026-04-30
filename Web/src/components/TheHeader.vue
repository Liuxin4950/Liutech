<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import theme from '../utils/theme.ts'
import { useUserStore } from '../stores/user'
import Icon from './Icon.vue'
import menuIconLight from '@/assets/image/icon/menu.png'
import menuIconDark from '@/assets/image/icon/menu_dark.png'

// 接收滚动位置
const props = defineProps<{
  scrollY?: number
}>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isMenuOpen = ref(false)
const isUserMenuOpen = ref(false)

// 计算背景颜色和透明度
const headerBackgroundStyle = computed(() => {
  const scrollValue = props.scrollY || 0
  // 滚动0-100px范围内，透明度从0渐变到0.95
  const maxScroll = 100
  const minOpacity = 0  // 默认状态下的基础透明度
  const opacity = minOpacity + Math.min(scrollValue / maxScroll, 0.9)

  // 引用 theme.current 确保主题切换时重新计算
  void theme.current.value

  // 获取当前主题的背景色
  const rootStyles = getComputedStyle(document.documentElement)
  const bgColor = rootStyles.getPropertyValue('--bg-main').trim()
  
  // 将十六进制颜色转换为rgba
  let r = 255, g = 255, b = 255 // 默认白色
  if (bgColor.startsWith('#')) {
    const hex = bgColor.slice(1)
    if (hex.length === 3) {
      r = parseInt(hex[0] + hex[0], 16)
      g = parseInt(hex[1] + hex[1], 16)
      b = parseInt(hex[2] + hex[2], 16)
    } else if (hex.length === 6) {
      r = parseInt(hex[0] + hex[1], 16)
      g = parseInt(hex[2] + hex[3], 16)
      b = parseInt(hex[4] + hex[5], 16)
    }
  }
  
  return {
    backgroundColor: `rgba(${r}, ${g}, ${b}, ${opacity})`
  }
})

/** 导航配置（避免重复写） */
const navItems = [
  { label: '首页', path: '/', section: 'home', icon: 'home' },
  { label: '分类', path: '/categories', section: 'categories', icon: 'folder' },
  { label: '标签', path: '/tags', section: 'tags', icon: 'tag' },
  { label: '归档', path: '/archive', section: 'archive', icon: 'archive' },
  { label: '关于我', path: '/about', section: 'about', icon: 'user' }
]

/** 关闭所有菜单 */
const closeMenus = () => {
  isMenuOpen.value = false
  isUserMenuOpen.value = false
}

/** 打开/关闭菜单 */
const toggleMenu = () => (isMenuOpen.value = !isMenuOpen.value)
const toggleUserMenu = () => (isUserMenuOpen.value = !isUserMenuOpen.value)

/** 跳转并关闭菜单 */
const navigateTo = (path: string) => {
  router.push(path)
  closeMenus()
}

/** 退出登录 */
const handleLogout = () => {
  userStore.logout()
  closeMenus()
  router.push('/')
}

/** 判断激活导航 */
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
    return map[from] === section
  }
  return route.meta?.section === section
}

/** 点击外部关闭菜单 */
const handleClickOutside = (e: Event) => {
  const target = e.target as HTMLElement
  if (!target.closest('header')) closeMenus()
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
</script>


<template>
  <header class="sticky top-0 z-100" :style="headerBackgroundStyle">
    <div class="content px-20 flex-sb flex-ac">

      <!-- LOGO -->
      <h2 class="logo link text-primary">LiuTech</h2>

      <!-- 桌面端导航 -->
      <nav class="desktop-nav">
        <ul class="flex">
          <li v-for="item in navItems" :key="item.path">
            <router-link
              :to="item.path"
              class="nav-link transition"
              :class="{ 'is-active': isActive(item.section) }"
            >
              <Icon :name="item.icon" size="16" class="nav-icon" />
              {{ item.label }}
            </router-link>
          </li>
        </ul>
      </nav>

      <!-- 用户区域 + 主题 + 移动端按钮 -->
      <div class="flex flex-ac nav-user">

        <!-- 用户信息 -->
        <div class="relative user-menu-container">
          <div
            v-if="userStore.isLoggedIn"
            class="flex flex-ac gap-8 link rounded"
            @click="toggleUserMenu"
          >
            <div class="user-avatar rounded-full link flex-jc">
              <img
                v-if="userStore.avatar"
                :src="userStore.avatar" :alt="userStore.username"
                class="fit rounded-full"
              />
              <div v-else class="text-main font-semibold text-sm">
                {{ userStore.username?.charAt(0).toUpperCase() }}
              </div>
            </div>

            <div class="flex flex-col link">
              <span class="font-medium">{{ userStore.username }}</span>
              <span class="flex flex-ac text-sm text-muted">
                <span class="user-points">{{ userStore.points }}</span>积分
              </span>
            </div>
          </div>

          <!-- 未登录 -->
          <button
            v-else
            class="text-main flex flex-ac gap-8 transition rounded p-8 hover-lift"
            @click="navigateTo('/login')"
          >
            <Icon name="user" size="16" />
            <span>登录/注册</span>
          </button>

          <!-- 用户菜单 -->
          <div
            v-show="isUserMenuOpen"
            class="avatar-menu absolute card transition bg-main"
            @click.stop
          >
            <ul class="list">
              <li @click="navigateTo('/profile')" class="transition link">个人资料</li>
              <li v-if="userStore.isAdmin" @click="navigateTo('/my-posts')" class="transition link">我的文章</li>
              <li v-if="userStore.isAdmin" @click="navigateTo('/drafts')" class="transition link">草稿箱</li>
              <li @click="navigateTo('/favorites')" class="transition link">我的收藏</li>
              <li @click="handleLogout" class="transition link border-t text-danger">退出登录</li>
            </ul>
          </div>
        </div>

        <!-- 主题按钮 -->
        <button @click="theme.toggle" class="link transition p-8" :title="theme.current.value === 'light' ? '切换到深色模式' : '切换到浅色模式'">
          <Icon :style="{ color: theme.current.value === 'light' ? '#333333' : '#FFD700', filter: theme.current.value === 'light' ? 'drop-shadow(0 0 4px rgba(224, 247, 250, 0.8))' : 'none' }" :name="theme.current.value === 'light' ? 'moon' : 'sun'" size="24" />
        </button>

        <!-- 移动端按钮 -->
        <img
          class="mobile-menu-btn"
          :src="theme.current.value === 'light'
            ? menuIconLight
            : menuIconDark"
          @click="toggleMenu"
        />
      </div>

      <!-- 移动端抽屉 -->
      <div class="mobile-drawer" :style="headerBackgroundStyle" :class="{ open: isMenuOpen }" @click.stop>
        <ul class="list">
          <li
            v-for="item in navItems"
            :key="item.path"
            @click="navigateTo(item.path)"
            class="p-16 hover-bg transition border-b link"
          >
            <Icon :name="item.icon" size="18" class="mr-8" />
            {{ item.label }}
          </li>

          <li v-if="userStore.isLoggedIn" @click="navigateTo('/profile')" class="p-16 border-b link">
            <Icon name="user" size="18" class="mr-8" />个人资料
          </li>
          <li v-if="userStore.isAdmin" @click="navigateTo('/my-posts')" class="p-16 border-b link">
            <Icon name="edit" size="18" class="mr-8" />我的文章
          </li>
          <li v-if="userStore.isAdmin" @click="navigateTo('/drafts')" class="p-16 border-b link">
            <Icon name="file" size="18" class="mr-8" />草稿箱
          </li>
          <li v-if="userStore.isLoggedIn" @click="navigateTo('/favorites')" class="p-16 border-b link">
            <Icon name="favorite" size="18" class="mr-8" />我的收藏
          </li>
          <li v-if="userStore.isLoggedIn" @click="handleLogout" class="p-16 text-error border-b link">
            <Icon name="close" size="18" class="mr-8" />退出登录
          </li>
        </ul>
      </div>
    </div>
  </header>
</template>





<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
.logo{
  font-size: 32px;
  font-weight: bold;
}

header {
  width: 100%;
  height: 70px;
  background-color: rgba(255, 255, 255, 0);
  backdrop-filter: blur(2px);
  // box-shadow: var(--shadow-sm);
  transition: background-color 0.3s ease;
}

header>div {
  height: 70px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border: 1px solid var(--border-base);
}

.font-medium{
  text-shadow: 0 0 2px var(--bg-card);
}
.user-points {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-right: 5px;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  background-color: var(--color-warning);
  border-radius: 9px;
  color: var(--text-main);
  font-size: 11px;
  font-weight: 700;
}

ul,
ol {
  list-style: none;
  padding: 0;
  margin: 0;
}

.avatar-menu {
  padding: 0;
  top: 60px;
  width: 140px;
  z-index: 99;
  overflow: hidden;

  li {
    padding: 6px 16px;
    cursor: pointer;
  }

  li:last-child {
    color: red;
  }

  li:hover {
    color: white;
    background: var(--color-primary);
  }
}

.desktop-nav {
    @include respond(md) {
      display: none;
    }
}


/* 导航链接样式 */
.nav-link {
  text-shadow: 0 0 2px rgba(var(--bg-card), $alpha: .5);
  color: var(--text-main);
  text-decoration: none;
  font-weight: 500;
  position: relative;
  padding: 10px 0;
  margin: 0 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-icon {
  vertical-align: -2px;
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
  height: 4px;
  background: var(--color-primary);
}

/* 默认隐藏移动端菜单按钮 */
.mobile-menu-btn {
  width: 50px;
  height: 50px;
  padding: 5px;
  display: none;
  @include respond(md) {

   display: block;

  }
}


.mobile-drawer {
  position: fixed;
  top: 70px;
  right: 0;
  width: 80%;
  max-width: 200px;
  height: calc(100vh - 70px);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  transform: translateX(100%);
  transition: transform 0.3s ease;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 2px var(--shadow-lg);
  li {
    margin: 8px 16px;
    padding: 12px 20px;
    border-radius: 12px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    font-weight: 500;
    color: var(--text-main);
    display: flex;
    align-items: center;
    border: 1px solid transparent;
    background-color: var(--bg-card);
    box-shadow: var(--shadow-sm);
    gap: 5px;
    vertical-align: middle;

  }
  li:hover {
      // background-color: var(--color-primary);
      color: var(--color-primary);
      padding-left: 24px;
      border-color: var(--border-light);
    }

  li:active {
      transform: scale(0.98);
      background-color: var(--bg-main);
    }
  
}

.mobile-drawer.open {
  transform: translateX(0);
}



</style>
