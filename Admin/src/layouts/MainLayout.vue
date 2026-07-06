<script setup lang="ts">
import { ref, provide, onMounted, watch } from 'vue'
import TheHeader from '@/components/TheHeader.vue'
import TheFooter from '@/components/TheFooter.vue'
import TheSidebar from '@/components/TheSidebar.vue'
import TagsView from '@/components/TagsView.vue'
import { useTagsStore } from '@/stores/tabs'
import { useSettingsStore } from '@/stores/settings'
import { useRouter } from 'vue-router'

const settings = useSettingsStore()

// 侧边栏折叠状态：初始值取自 settings（用户偏好），
// 后续变化同步回 settings 以持久化。
const collapsed = ref(settings.sidebarCollapsed)
provide('sidebarCollapsed', collapsed)

watch(collapsed, (v) => { settings.setSidebarCollapsed(v) })
watch(() => settings.sidebarCollapsed, (v) => {
  if (v !== collapsed.value) collapsed.value = v
})

const tagsStore = useTagsStore()

onMounted(() => {
  const router = useRouter()
  const routes = router.options.routes
  tagsStore.addAffixTags([...routes])
})
</script>

<template>
  <a-layout class="lt-shell">
    <!-- 侧边栏 -->
    <a-layout-sider
      class="lt-shell__sider"
      :collapsed="collapsed"
      :collapsed-width="56"
      :width="220"
      :trigger="null"
      :bordered="false"
    >
      <TheSidebar />
    </a-layout-sider>

    <!-- 主区：Header（内含面包屑）+ TagsView + 内容 + Footer 纵向堆叠 -->
    <a-layout class="lt-shell__main">
      <TheHeader />
      <TagsView />

      <a-layout-content class="lt-shell__content">
        <router-view v-slot="{ Component }">
          <KeepAlive :include="tagsStore.cachedViews">
            <transition name="lt-fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </KeepAlive>
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
