<script setup lang="ts">
</script>

<template>
  <div class="app-container">
    <!-- 路由视图 -->
    <router-view />
  </div>
</template>

<style scoped>
.app-container {
  width: 100%;
  min-height: 100vh;
  background-color: var(--bg-color);
  color: var(--text-color);
  transition: background-color 0.3s, color 0.3s;
}
</style>
