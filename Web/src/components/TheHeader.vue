<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import theme from '../utils/theme.ts'
import { useUserStore } from '../stores/user'
import Icon from './Icon.vue'
import menuIconLight from '@/assets/image/icon/menu.png'
import menuIconDark from '@/assets/image/icon/menu_dark.png'
import logoUrl from '@/assets/image/logo/logo.png'

// 接收滚动位置
const props = defineProps<{
  scrollY?: number
}>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isMenuOpen = ref(false)
const isUserMenuOpen = ref(false)

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
const toggleUserMenu = () => {
  // 如果是移动端（假设宽度小于768px），则点击头像打开侧边栏
  if (window.innerWidth <= 768) {
    isMenuOpen.value = !isMenuOpen.value
    isUserMenuOpen.value = false
  } else {
    // Web 和平板尺寸，打开下拉菜单
    isUserMenuOpen.value = !isUserMenuOpen.value
  }
}

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

/** 导航滑块 */
const navListRef = ref<HTMLUListElement>()
const sliderStyle = ref({ left: '0px', transform: 'scale(0)', width: '0px' })
const isHovering = ref(false)

const updateSlider = (target?: HTMLElement | Event) => {
  if (!navListRef.value) return
  const li = (target instanceof HTMLElement ? target : null) || navListRef.value.querySelector('.is-active')?.closest('li') as HTMLElement
  if (li) {
    const ul = navListRef.value
    const pl = parseInt(getComputedStyle(ul).paddingLeft) || 0
    sliderStyle.value = {
      left: `${-pl}px`,
      transform: `translateX(${li.offsetLeft}px)`,
      width: `${li.offsetWidth}px`
    }
  } else {
    sliderStyle.value = { left: '0', transform: 'scale(0)', width: '0px' }
  }
}

/** hover 时滑块跟随 */
const onNavHover = (e: MouseEvent) => {
  const li = (e.target as HTMLElement).closest('li') as HTMLElement
  if (li) {
    isHovering.value = true
    updateSlider(li)
  }
}

/** 鼠标移开时恢复到激活位置 */
const onNavLeave = () => {
  isHovering.value = false
  updateSlider()
}

/** 点击外部关闭菜单 */
const handleClickOutside = (e: Event) => {
  const target = e.target as HTMLElement
  if (!target.closest('.user-menu-container')) {
    isUserMenuOpen.value = false
  }
  if (!target.closest('.mobile-drawer') && !target.closest('.mobile-menu-btn')) {
    isMenuOpen.value = false
  }
}

watch(() => route.path, () => nextTick(updateSlider))

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  window.addEventListener('resize', updateSlider)
  nextTick(updateSlider)
})
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('resize', updateSlider)
})
</script>


<template>
  <header class="sticky top-0 z-100">
    <div class="content px-20 flex-sb flex-ac">

      <!-- LOGO -->
      <h2 class="logo link text-primary" @click="navigateTo('/')">
        <img :src="logoUrl" alt="LiuTech" class="logo-mark" />
        <span>LiuTech</span>
      </h2>

      <!-- 桌面端导航 -->
      <nav class="desktop-nav">
        <ul ref="navListRef" class="flex nav-pill" :class="{ 'is-hovering': isHovering }" @mouseover="onNavHover" @mouseleave="onNavLeave">
          <div class="nav-slider" :style="sliderStyle"></div>
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
      <div class="flex flex-ac nav-user nav-pill-light relative">

        <!-- 用户信息 -->
        <div class="user-menu-container">
          <div
            v-if="userStore.isLoggedIn"
            class="flex flex-ac gap-8 link rounded user-info-btn"
            @click.stop="toggleUserMenu"
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
            class="text-main flex flex-ac gap-8 transition rounded p-8 hover-lift login-btn"
            @click="navigateTo('/login')"
            style="white-space: nowrap;"
          >
            <Icon name="user" size="16" />
            <span class="hide-on-mobile">登录/注册</span>
          </button>

          <!-- 用户菜单 -->
          <Transition name="fade">
            <div
              v-show="isUserMenuOpen"
              class="avatar-menu absolute transition"
              @click.stop
            >
              <ul class="list">
                <li @click="navigateTo('/profile')" class="transition link">
                  <Icon name="user" size="16" class="mr-8" />个人资料
                </li>
                <li v-if="userStore.isAdmin" @click="navigateTo('/my-posts')" class="transition link">
                  <Icon name="edit" size="16" class="mr-8" />我的文章
                </li>
                <li v-if="userStore.isAdmin" @click="navigateTo('/drafts')" class="transition link">
                  <Icon name="file" size="16" class="mr-8" />草稿箱
                </li>
                <li @click="navigateTo('/favorites')" class="transition link">
                  <Icon name="favorite" size="16" class="mr-8" />我的收藏
                </li>
                <li @click="handleLogout" class="transition link text-danger">
                  <Icon name="close" size="16" class="mr-8" />退出登录
                </li>
              </ul>
            </div>
          </Transition>
        </div>

        <div class="divider mx-8"></div>

        <!-- 主题按钮 -->
        <button @click="theme.toggle" class="link transition p-8 theme-btn" :title="theme.current.value === 'light' ? '切换到深色模式' : '切换到浅色模式'">
          <Icon :style="{ color: theme.current.value === 'light' ? 'var(--text-main)' : '#FFD700', filter: theme.current.value === 'light' ? 'drop-shadow(0 0 4px rgba(224, 247, 250, 0.8))' : 'none' }" :name="theme.current.value === 'light' ? 'moon' : 'sun'" size="20" class="theme-icon" />
        </button>

        <!-- 移动端按钮 -->
        <img
          class="mobile-menu-btn"
          :src="theme.current.value === 'light'
            ? menuIconLight
            : menuIconDark"
          @click.stop="toggleMenu"
        />
      </div>

      <!-- 移动端抽屉 -->
      <div class="mobile-drawer" :class="{ open: isMenuOpen }" @click.stop>
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
.logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 32px;
  font-weight: bold;
  line-height: 1;
}

.logo-mark {
  width: 34px;
  height: 34px;
  object-fit: contain;
  flex: 0 0 auto;
  border-radius: 8px;
}

header {
  width: 100%;
  height: var(--header-height);
  /* Remove the whole header background to allow pills to stand out */
  background-color: transparent !important;
  transition: all 0.3s ease;
  /* 移除 overflow: hidden; 否则下拉菜单会被截断 */
}

header>div {
  height: var(--header-height);
  /* 在移动端可能需要调整内容区域的边距 */
  @include respond(md) {
    padding: 0 10px;
  }
}

/* Nav Pill Style */
.nav-pill {
  position: relative;
  background: var(--surface-glass-muted);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 40px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-soft);
  transition: all 0.3s ease;
  @include respond(md) {
    padding: 2px 6px;
    gap: 2px;
  }
}
.nav-pill-light {
  position: relative;
  background: var(--surface-glass-muted);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 40px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-soft);
  transition: all 0.3s ease;
  padding: 4px 8px;
  @include respond(md) {
    padding: 2px 6px;
    gap: 2px;
  }
}

.divider {
  width: 1px;
  height: 20px;
  background-color: var(--border-base);
  opacity: 0.5;
  
  @include respond(md) {
    margin: 0 4px !important;
  }
}

.user-info-btn {
  @include respond(md) {
    gap: 4px;
  }
}

.user-avatar {
  width: 40px;
  height: 40px;
  border: 1px solid var(--border-base);
  
  @include respond(md) {
    width: 32px;
    height: 32px;
  }
}

.login-btn {
  @include respond(md) {
    padding: 4px;
    gap: 4px;
  }
}

.theme-btn {
  @include respond(md) {
    padding: 4px;
  }
}

.theme-icon {
  @include respond(md) {
    font-size: 16px !important;
  }
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
  padding: 8px;
  top: calc(100% + 8px); /* 动态计算：在整个右侧药丸正下方 8px 的位置 */
  right: 0; /* 与右侧药丸右边缘对齐 */
  width: 160px; /* 稍微加宽一点以更好地容纳图标和文字 */
  z-index: 999;
  border-radius: 16px;
  /* 统一为毛玻璃样式 */
  background: var(--surface-glass-muted);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-md);

  /* 在移动端隐藏这个下拉框，因为移动端有侧边栏 */
  @include respond(md) {
    display: none !important;
  }

  li {
    padding: 10px 16px;
    margin: 4px 0;
    border-radius: 12px;
    cursor: pointer;
    font-weight: 500;
    color: var(--text-main);
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    background-color: transparent;
  }

  li:last-child {
    color: var(--color-error);
    margin-top: 8px;
    border-top: 1px solid var(--border-light);
    border-radius: 0 0 12px 12px;
  }

  li:hover {
    background-color: var(--state-primary-bg);
    color: var(--color-primary);
    padding-left: 20px;
  }
}

.desktop-nav {
    @include respond(md) {
      display: none;
    }
}


/* 导航链接样式 */
.nav-link {
  color: var(--text-main);
  text-decoration: none;
  font-weight: 500;
  position: relative;
  z-index: 1;
  padding: 10px 16px;
  border-radius: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: all 0.3s ease;
  
  @include respond(md) {
    padding: 4px 8px;
    margin: 0 2px;
    font-size: 14px;
  }
}

.nav-link:hover {
  background: transparent;
}

.nav-slider {
  position: absolute;
  top: 0;
  height: 100%;
  background: rgba(var(--color-primary-rgb), 0.12);
  border-radius: 40px;
  transition: left 0.4s cubic-bezier(0.4, 0, 0.2, 1), transform 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;
  will-change: transform;
  z-index: 0;
}

:root.dark .nav-slider {
  background: rgba(var(--color-primary-rgb), 0.18);
}

.nav-icon {
  vertical-align: -2px;
}

.nav-link.router-link-exact-active,
.nav-link.is-active {
  color: var(--color-primary);
}

/* hover 时，鼠标所在的导航项文字变色 */
.is-hovering .nav-link:hover {
  color: var(--color-primary);
}

/* hover 时，原先激活的导航项文字恢复普通色（除非鼠标在它上面） */
.is-hovering .nav-link.router-link-exact-active:not(:hover),
.is-hovering .nav-link.is-active:not(:hover) {
  color: var(--text-main);
}

.nav-link.router-link-exact-active:hover,
.nav-link.is-active:hover {
  background: transparent;
}
/* 默认隐藏移动端菜单按钮 */

.mobile-menu-btn {
  height: 28px; /* 进一步减小按钮高度 */
  width: 28px;  /* 进一步减小按钮宽度 */
  margin-left: 2px; /* 减小左边距 */
  display: none;
  cursor: pointer;
  @include respond(md) {
   display: block;
  }
}


.hide-on-mobile {
  @include respond(md) {
    display: none;
  }
}

.mobile-drawer {
  position: fixed;
  top: var(--header-height);
  right: 10px; /* 改为悬浮卡片，距离右侧有边距 */
  width: 240px; /* 固定合理宽度 */
  max-height: calc(100vh - var(--header-height) - 20px);
  /* 使用与顶部药丸风格一致的毛玻璃效果 */
  background: var(--surface-glass-muted);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--border-soft);
  border-radius: 20px; /* 圆润的边角 */
  
  /* 修改动画：从缩小渐隐到放大显示，类似弹出菜单 */
  transform: scale(0.95);
  opacity: 0;
  pointer-events: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: top right;
  
  z-index: 1000;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  overflow-y: auto;
  padding: 12px 8px; /* 调整内边距 */
  
  li {
    margin: 4px 8px; /* 减小菜单项间距 */
    padding: 12px 16px;
    border-radius: 12px;
    transition: all 0.2s ease;
    font-weight: 500;
    color: var(--text-main);
    display: flex;
    align-items: center;
    border: 1px solid transparent;
    /* 移除每个li默认的卡片背景，让整体的毛玻璃透出来 */
    background-color: transparent;
    box-shadow: none;
    gap: 8px;
  }
  
  li:hover {
    background-color: rgba(var(--color-primary-rgb), 0.1);
    color: var(--color-primary);
    padding-left: 20px; /* 轻微缩进反馈 */
  }

  li:active {
    transform: scale(0.98);
    background-color: var(--state-primary-bg-active);
  }
}

/* Vue Transition 渐变动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.mobile-drawer.open {
  transform: scale(1);
  opacity: 1;
  pointer-events: auto;
}



</style>
