<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeOutlined, LoginOutlined } from '@ant-design/icons-vue'
import { removeToken } from '../utils/auth'

const router = useRouter()

const goHome = () => router.push('/')
const goLogin = () => {
  removeToken()
  localStorage.removeItem('userInfo')
  router.push('/login')
}
</script>

<template>
  <div class="lt-error-page">
    <a-result status="403" title="403" sub-title="抱歉，你没有权限访问此页面">
      <template #extra>
        <a-space>
          <a-button type="primary" @click="goHome">
            <template #icon><HomeOutlined /></template>
            返回首页
          </a-button>
          <a-button @click="goLogin">
            <template #icon><LoginOutlined /></template>
            重新登录
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
