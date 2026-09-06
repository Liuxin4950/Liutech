<script setup lang="ts">
// App根组件（作者：刘鑫，修改时间：2025-08-26 16:01:05 +08:00）
import GlobalPageLoader from '@/components/GlobalPageLoader.vue'
import RouteProgress from '@/components/RouteProgress.vue'
import { useRouteLoading } from '@/composables/useRouteLoading'
import { usePageSeo } from '@/composables/usePageSeo'

usePageSeo()

const { isInitialLoading } = useRouteLoading()
</script>

<template>
  <div class="app-container">
    <router-view />
    <GlobalPageLoader :show="isInitialLoading" />
    <RouteProgress />
  </div>
</template>

<style scoped>
</style>
