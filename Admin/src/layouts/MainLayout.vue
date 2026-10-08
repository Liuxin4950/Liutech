<script setup lang="ts">
import { ref, reactive, computed, provide, onMounted, onBeforeUnmount, watch } from 'vue'
import TheHeader from '@/components/TheHeader.vue'
import TheFooter from '@/components/TheFooter.vue'
import TheSidebar from '@/components/TheSidebar.vue'
import TagsView from '@/components/TagsView.vue'
import { useTagsStore } from '@/stores/tabs'
import { useSettingsStore } from '@/stores/settings'
import { useRoute, useRouter } from 'vue-router'

const settings = useSettingsStore()
const route = useRoute()

// 手机使用独立抽屉，不让 220px 侧栏挤占内容，也不覆盖桌面折叠偏好。
const mobileQuery = window.matchMedia('(max-width: 767px)')
const isMobile = ref(mobileQuery.matches)
const mobileMenuOpen = ref(false)
const collapsed = computed({
  get: () => isMobile.value ? !mobileMenuOpen.value : settings.sidebarCollapsed,
  set: (value: boolean) => {
    if (isMobile.value) mobileMenuOpen.value = !value
    else settings.setSidebarCollapsed(value)
  },
})
provide('sidebarCollapsed', collapsed)
const mobileChanged = (event: MediaQueryListEvent) => {
  isMobile.value = event.matches
  mobileMenuOpen.value = false
}
watch(() => route.path, () => { mobileMenuOpen.value = false })
onBeforeUnmount(() => mobileQuery.removeEventListener('change', mobileChanged))

const tagsStore = useTagsStore()

/**
 * 强制重挂载「当前页面」用的令牌，按 route.path 分桶存储。
 *
 * KeepAlive 的 include 变更只会丢弃缓存条目（pruneCacheEntry 对"当前正在渲染"
 * 的实例只清标志位、不卸载），因此「刷新」必须再改变 key 才能让页面真正重新挂载、
 * 重新拉数据。TagsView 通过 inject('ltReloadCurrentView') 触发。
 *
 * ⚠️ 令牌必须按 path 分桶，不能是单个全局值：key 参与每个页签的计算，
 * 若用全局 token，刷新 A 会同时改掉 B、C… 的 key，切回它们时缓存全部未命中、
 * 被迫重挂载——反而破坏多页签缓存。分桶后只有当前 path 的 key 变化。
 */
const reloadTokens = reactive<Record<string, number>>({})
const viewKey = computed(() => `${route.path}::${reloadTokens[route.path] ?? 0}`)
provide('ltReloadCurrentView', () => {
  reloadTokens[route.path] = (reloadTokens[route.path] ?? 0) + 1
})

onMounted(() => {
  mobileQuery.addEventListener('change', mobileChanged)
  const router = useRouter()
  const routes = router.options.routes
  tagsStore.addAffixTags([...routes])
})
</script>

<template>
  <a-layout class="lt-shell">
    <!-- 侧边栏 -->
    <a-layout-sider
      v-if="!isMobile"
      class="lt-shell__sider"
      :collapsed="collapsed"
      :collapsed-width="56"
      :width="220"
      :trigger="null"
      :bordered="false"
    >
      <TheSidebar />
    </a-layout-sider>
    <a-drawer v-else v-model:open="mobileMenuOpen" placement="left" :width="220" :closable="false" :body-style="{ padding: 0 }">
      <TheSidebar />
    </a-drawer>

    <!-- 主区：Header（内含面包屑）+ TagsView + 内容 + Footer 纵向堆叠 -->
    <a-layout class="lt-shell__main">
      <TheHeader />
      <TagsView />

      <a-layout-content class="lt-shell__content">
        <router-view v-slot="{ Component }">
          <!-- ⚠️ transition 必须在 KeepAlive 外层：
               KeepAlive 只认「状态组件/Suspense」这一种子节点，包在它里面的
               Transition 会让 KeepAlive 直接放行、不写缓存（已用 vue 3.5.34 实测）。 -->
          <transition name="lt-fade" mode="out-in">
            <KeepAlive :include="tagsStore.cachedViews">
              <component :is="Component" :key="viewKey" />
            </KeepAlive>
          </transition>
        </router-view>
      </a-layout-content>

      <TheFooter />
    </a-layout>
  </a-layout>
</template>

<style scoped>
.lt-shell {
  height: 100vh;
  overflow: hidden;
  background: var(--lt-color-bg-layout);
}

.lt-shell__sider {
  background: var(--lt-color-bg-container);
  border-right: 1px solid var(--lt-color-border-secondary);
  height: 100vh;
  overflow-y: auto;
  z-index: var(--lt-z-sidebar);
}

.lt-shell__main {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--lt-color-bg-layout);
}

.lt-shell__content {
  flex: 1;
  overflow-y: auto;
  background: var(--lt-color-bg-layout);
  /* 页面级 padding，让所有子页面自动获得呼吸空间 */
  padding: var(--lt-space-page-y) var(--lt-space-page-x);
}

.lt-fade-enter-active,
.lt-fade-leave-active {
  transition: var(--lt-motion-fade);
}
.lt-fade-enter-from,
.lt-fade-leave-to {
  opacity: 0;
}

.lt-shell__sider :deep(.ant-layout-sider-children) {
  height: 100%;
}
</style>
