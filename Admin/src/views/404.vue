<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeOutlined, RollbackOutlined } from '@ant-design/icons-vue'

const router = useRouter()

const goHome = () => router.push('/')
const goBack = () => {
  if (window.history.length > 1) router.back()
  else router.push('/')
}
</script>

<template>
  <div class="lt-error-page">
    <a-result status="404" title="404" sub-title="抱歉，你访问的页面不存在">
      <template #extra>
        <a-space>
          <a-button type="primary" @click="goHome">
            <template #icon><HomeOutlined /></template>
            返回首页
          </a-button>
          <a-button @click="goBack">
            <template #icon><RollbackOutlined /></template>
            返回上页
          </a-button>
        </a-space>
      </template>
    </a-result>
  </div>
</template>

<style scoped>
.lt-error-page {
  min-height: calc(100vh - var(--lt-size-header) - var(--lt-size-tags-view));
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--lt-color-bg-layout);
}
</style>
