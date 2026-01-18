<script setup lang="ts">
import { ref, provide, onMounted } from 'vue'
import TheHeader from '@/components/TheHeader.vue'
import TheFooter from '@/components/TheFooter.vue'
import TheSidebar from '@/components/TheSidebar.vue'
import TagsView from '@/components/TagsView.vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { useTagsStore } from '@/stores/tabs'
import { useRouter } from 'vue-router'

// 侧边栏折叠状态
const collapsed = ref(false)

// 提供给子组件使用
provide('sidebarCollapsed', collapsed)

// 标签页状态管理
const tagsStore = useTagsStore()

// 页面加载时初始化固定标签
onMounted(() => {
  // 获取路由配置中的固定标签
  const router = useRouter()
  const routes = router.options.routes
  tagsStore.addAffixTags([...routes])
})
</script>

<template>
  <a-layout class="main-layout">


    <!-- 主内容区域 -->
    <a-layout class="content-layout">
      <!-- 侧边栏容器 - 固定不滚动 -->
      <div class="sidebar-container">
        <a-layout-sider
          class="sidebar"
          :width="collapsed ? 80 : 200"
          :collapsed="collapsed"
          theme="light"
        >
          <TheSidebar />
        </a-layout-sider>
      </div>
      <a-layout-content class="main-content">
        <!-- 顶部导航栏 -->
        <a-layout-header class="header">
          <TheHeader />
        </a-layout-header>
        <!-- 标签栏和面包屑区域 - 固定不滚动 -->
        <div class="nav-bar-wrapper">
          <TagsView />
          <Breadcrumb />
        </div>
        <div class="content-wrapper">
          <!-- 使用 KeepAlive 缓存页面组件 -->
          <KeepAlive :include="tagsStore.cachedViews">
            <router-view v-slot="{ Component }">
              <transition name="fade" mode="out-in">
                <component :is="Component" />
              </transition>
            </router-view>
          </KeepAlive>
        </div>
        <TheFooter />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.main-layout {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.header {
  padding: 0;
  height: 64px;
  line-height: 64px;
  background: var(--bg-card);
  flex-shrink: 0;
  z-index: 100;
  position: relative;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.content-layout {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: row;
}

/* 侧边栏容器 */
.sidebar-container {
  height: 100%;
  overflow-y: auto;
  background: var(--bg-card);
  flex-shrink: 0;
  /* box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1); */
  z-index: 99;
}

.sidebar {
  background: var(--bg-card);
  min-height: 100%;
}

.main-content {
  flex: 1;
  height: 100%;
  overflow-y: auto;
  background: var(--bg-main);
  display: flex;
  flex-direction: column;
}

/* 标签栏和面包屑容器 - 固定在内容区域顶部 */
.nav-bar-wrapper {
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-light);
  position: sticky;
  top: 0;
  z-index: 98;
}

.content-wrapper {
  /* padding: 16px 24px 24px; */
  /* 让内容区域撑满剩余空间，但由内容决定高度 */
  flex: 1; 
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>