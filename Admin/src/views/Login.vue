<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useErrorHandler } from '../composables/useErrorHandler'
const router = useRouter()
const userStore = useUserStore()
const { handleFormSubmit, showSuccess, clearError,showSuccessToast } = useErrorHandler()

// 表单状态 - 管理端只需要登录功能

// 登录表单数据
const loginForm = reactive({
  username: '',
  password: ''
})



// 表单验证错误信息
const errors = reactive({
  username: '',
  password: ''
})



/**
 * 清空表单
 */
const clearForms = () => {
  Object.assign(loginForm, { username: '', password: '' })
}



/**
 * 清空错误信息
 */
const clearErrors = () => {
  Object.assign(errors, {
    username: '',
    password: ''
  })
}

/**
 * 表单验证
 */
const validateForm = () => {
  clearErrors()
  let isValid = true

  // 登录验证
  if (!loginForm.username.trim()) {
    errors.username = '请输入用户名'
    isValid = false
  }
  if (!loginForm.password) {
    errors.password = '请输入密码'
    isValid = false
  }

  return isValid
}

/**
 * 处理登录
 */
const handleLogin = async () => {
  if (!validateForm()) return

  const result = await handleFormSubmit(async () => {
    return await userStore.login(loginForm.username, loginForm.password)
  })

  if (result) {
    showSuccessToast("登录成功！")
    // showSuccess('登录成功！')
    // 获取重定向路径，如果没有则跳转到首页
    const redirect = router.currentRoute.value.query.redirect as string || '/'
    router.push(redirect)
  }
}



/**
 * 提交表单
 */
const handleSubmit = () => {
  handleLogin()
}
</script>

<template>
  <div class="login-container">    
    <main class="main-content">
      <div class="login-wrapper">
        <div class="login-card">
          <!-- 标题 -->
          <div class="card-header">
            <h2>管理员登录</h2>
            <p>欢迎回来！</p>
          </div>

          <!-- 表单 -->
          <form @submit.prevent="handleSubmit" class="login-form">
            <!-- 用户名 -->
            <div class="form-group">
              <label for="username">用户名</label>
              <input
                id="username"
                v-model="loginForm.username"
                type="text"
                placeholder="请输入用户名"
                :class="{ 'error': errors.username }"
              />
              <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
            </div>



            <!-- 密码 -->
            <div class="form-group">
              <label for="password">密码</label>
              <input
                id="password"
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                :class="{ 'error': errors.password }"
              />
              <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
            </div>



            <!-- 提交按钮 -->
            <button type="submit" class="submit-btn" :disabled="userStore.isLoading">
              <span v-if="userStore.isLoading" class="loading-spinner"></span>
              {{ userStore.isLoading ? '处理中...' : '登录' }}
            </button>
          </form>


        </div>
      </div>
    </main>
    
  </div>
</template>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--bg-tag) 100%);
}

.login-wrapper {
  width: 100%;
  max-width: 400px;
}

.login-card {
  background: var(--bg-soft);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  padding: 40px;
  border: 1px solid var(--border-soft);
  transition: all 0.3s ease;
}

.login-card:hover {
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.15);
}

.card-header {
  text-align: center;
  margin-bottom: 32px;
}

.card-header h2 {
  color: var(--text-main);
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 8px;
}

.card-header p {
  color: var(--text-main);
  opacity: 0.7;
  font-size: 14px;
}

.login-form {
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-main);
  font-weight: 500;
  font-size: 14px;
}

.form-group input {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid var(--border-soft);
  border-radius: 8px;
  font-size: 16px;
  background: var(--bg-soft);
  color: var(--text-main);
  transition: all 0.3s ease;
}

.form-group input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.form-group input.error {
  border-color: #f56565;
  box-shadow: 0 0 0 3px rgba(245, 101, 101, 0.1);
}

.error-message {
  display: block;
  color: #f56565;
  font-size: 12px;
  margin-top: 4px;
}

.submit-btn {
  width: 100%;
  padding: 14px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.submit-btn:hover:not(:disabled) {
  background: var(--bg-tag-hover);
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid transparent;
  border-top: 2px solid currentColor;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.card-footer {
  text-align: center;
}

.card-footer p {
  color: var(--text-main);
  opacity: 0.7;
  font-size: 14px;
  margin: 0;
}

.toggle-btn {
  color: var(--color-primary);
  background: none;
  border: none;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
  margin-left: 4px;
  transition: color 0.3s ease;
}

.toggle-btn:hover {
  color: var(--color-primary-dark);
  text-decoration: underline;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .main-content {
    padding: 20px 16px;
  }
  
  .login-card {
    padding: 24px;
  }
  
  .card-header h2 {
    font-size: 24px;
  }
}
</style>