<script setup lang="ts">
import { computed } from 'vue'
import { ConfigProvider, theme } from 'ant-design-vue'
import themeManager from './utils/theme'

// 根据当前主题获取 Ant Design Vue 主题配置
const antTheme = computed(() => {
  return themeManager.current.value === 'dark'
    ? { token: { colorPrimary: '#177ddc' }, algorithm: theme.darkAlgorithm }
    : { token: { colorPrimary: '#1677ff' }, algorithm: theme.defaultAlgorithm }
})
</script>

<template>
  <ConfigProvider :theme="antTheme">
    <div class="app-container">
      <router-view />
    </div>
  </ConfigProvider>
</template>

<style>
/* 全局深色模式样式 */
.dark {
  color-scheme: dark;
}

.dark body {
  background-color: #000000;
  color: #ffffff;
}
</style>
