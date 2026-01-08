<script setup lang="ts">
import { ref, provide } from 'vue'
import TheHeader from '@/components/TheHeader.vue'
import TheFooter from '@/components/TheFooter.vue'
import TheSidebar from '@/components/TheSidebar.vue'

// 侧边栏折叠状态
const collapsed = ref(false)

// 提供给子组件使用
provide('sidebarCollapsed', collapsed)
</script>

<template>
  <a-layout class="main-layout">
    <a-layout-header class="header">
      <TheHeader />
    </a-layout-header>
    <a-layout class="content-layout">
      <a-layout-sider 
        class="sidebar" 
        :width="collapsed ? 80 : 200" 
        :collapsed="collapsed"
        theme="light"
      >
        <TheSidebar />
      </a-layout-sider>
      <a-layout-content class="main-content">
        <div class="content-wrapper">
          <router-view />
        </div>
      </a-layout-content>
    </a-layout>
    <a-layout-footer class="footer">
      <TheFooter />
    </a-layout-footer>
  </a-layout>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
}

.header {
  padding: 0;
  height: 64px;
  line-height: 64px;
  background: var(--bg-card);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.content-layout {
  min-height: calc(100vh - 128px);
}

.sidebar {
  background: var(--bg-card);
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

.main-content {
  background: var(--bg-main);
  min-height: calc(100vh - 128px);
}

.content-wrapper {
  padding: 24px;
  min-height: 100%;
}

.footer {
  padding: 0;
  background: transparent;
  border-top: none;
}

/* 深色模式覆盖 Ant Design 组件 */
:deep(.ant-layout-sider) {
  background: var(--bg-card);
}

:deep(.ant-layout-header) {
  background: var(--bg-card);
}

:deep(.ant-table) {
  background: var(--bg-card);
}

:deep(.ant-table-thead > tr > th) {
  background: var(--bg-hover);
  color: var(--text-main);
}

:deep(.ant-table-tbody > tr > td) {
  color: var(--text-main);
}

:deep(.ant-table-tbody > tr:hover > td) {
  background: var(--bg-hover);
}

:deep(.ant-card) {
  background: var(--bg-card);
  color: var(--text-main);
}

:deep(.ant-card-head) {
  color: var(--text-main);
  border-bottom-color: var(--border-light);
}

:deep(.ant-form-item-label > label) {
  color: var(--text-secondary);
}

:deep(.ant-input),
:deep(.ant-select-selector),
:deep(.ant-picker) {
  background: var(--bg-card) !important;
  border-color: var(--border-base) !important;
  color: var(--text-main) !important;
}

:deep(.ant-input-affix-wrapper) {
  background: var(--bg-card) !important;
  border-color: var(--border-base) !important;
}

:deep(.ant-select-dropdown) {
  background: var(--bg-card);
}

:deep(.ant-select-item) {
  color: var(--text-main);
}

:deep(.ant-select-item-option-active) {
  background: var(--bg-hover);
}

:deep(.ant-select-item-option-selected) {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}
</style>