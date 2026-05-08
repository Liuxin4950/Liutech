<template>
  <div class="not-found-page">
    <div class="not-found-content">
      <h1 class="error-code">404</h1>
      <p class="error-message">页面不存在</p>
      <p class="error-hint">您访问的页面可能已被移除或地址有误</p>
      <router-link to="/" class="back-home-btn">返回首页</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
// 404 页面
</script>

<style scoped>
.not-found-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 40px 20px;
}

.not-found-content {
  text-align: center;
}

.error-code {
  font-size: 6rem;
  font-weight: 800;
  color: var(--color-primary);
  line-height: 1;
  margin-bottom: 16px;
}

.error-message {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-title);
  margin-bottom: 8px;
}

.error-hint {
  font-size: 1rem;
  color: var(--text-subtle);
  margin-bottom: 32px;
}

.back-home-btn {
  display: inline-block;
  padding: 12px 32px;
  background-color: var(--color-primary);
  color: var(--text-on-primary);
  border-radius: 25px;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.3s;
}

.back-home-btn:hover {
  background-color: var(--color-primary-dark);
  transform: translateY(-2px);
}
</style>
