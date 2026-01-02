<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeOutlined, LockOutlined } from '@ant-design/icons-vue'

const router = useRouter()

const goHome = () => {
  router.push('/')
}

const goLogin = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  router.push('/login')
}
</script>

<template>
  <div class="403-container">
    <div class="403-content">
      <div class="icon-wrapper">
        <LockOutlined class="icon" />
      </div>
      <h1>403</h1>
      <h2>权限不足</h2>
      <p>抱歉，您没有权限访问此页面</p>
      <div class="actions">
        <a-button type="primary" size="large" @click="goHome">
          <template #icon><HomeOutlined /></template>
          返回首页
        </a-button>
        <a-button size="large" @click="goLogin">
          重新登录
        </a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.403-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
}

.403-content {
  text-align: center;
  padding: 48px;
}

.icon-wrapper {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  background: #fff1f0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon {
  font-size: 40px;
  color: #ff4d4f;
}

h1 {
  font-size: 72px;
  font-weight: 700;
  color: #ff4d4f;
  margin: 0;
  line-height: 1;
}

h2 {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main);
  margin: 16px 0 8px;
}

p {
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 32px;
}

.actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}
</style>
