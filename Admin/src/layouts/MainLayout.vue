<script setup lang="ts">
import TheHeader from '@/components/TheHeader.vue'
import TheFooter from '@/components/TheFooter.vue'
import TheSidebar from '@/components/TheSidebar.vue'

</script>

<template>
  <a-layout class="main-layout">
    <a-layout-header class="header">
      <TheHeader />
    </a-layout-header>
    <a-layout class="content-layout">
      <a-layout-sider class="sidebar" width="200" theme="light">
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
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.content-layout {
  min-height: calc(100vh - 128px);
}

.sidebar {
  background: #fff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

.main-content {
  background: #f0f2f5;
  min-height: calc(100vh - 128px);
}

.content-wrapper {
  padding: 24px;
  min-height: 100%;
}

.footer {
  text-align: center;
  background: #fff;
  padding: 12px 0;
}
</style>