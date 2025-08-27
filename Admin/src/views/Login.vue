<script setup lang="ts">
import { ref, reactive, h } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useErrorHandler } from '../composables/useErrorHandler'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
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
    message.success('登录成功！')
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
    <div class="login-wrapper">
      <a-card class="login-card" :bordered="false">
        <!-- 标题 -->
        <div class="card-header">
          <h2>管理员登录</h2>
          <p>欢迎回来！</p>
        </div>

        <!-- 表单 -->
        <a-form
          :model="loginForm"
          @finish="handleSubmit"
          layout="vertical"
          class="login-form"
        >
          <!-- 用户名 -->
          <a-form-item
            label="用户名"
            name="username"
            :validate-status="errors.username ? 'error' : ''"
            :help="errors.username"
          >
            <a-input
               v-model:value="loginForm.username"
               placeholder="请输入用户名"
               size="large"
             >
               <template #prefix>
                 <UserOutlined />
               </template>
             </a-input>
          </a-form-item>

          <!-- 密码 -->
          <a-form-item
            label="密码"
            name="password"
            :validate-status="errors.password ? 'error' : ''"
            :help="errors.password"
          >
            <a-input-password
               v-model:value="loginForm.password"
               placeholder="请输入密码"
               size="large"
             >
               <template #prefix>
                 <LockOutlined />
               </template>
             </a-input-password>
          </a-form-item>

          <!-- 提交按钮 -->
          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              :loading="userStore.isLoading"
              block
            >
              {{ userStore.isLoading ? '登录中...' : '登录' }}
            </a-button>
          </a-form-item>
        </a-form>
      </a-card>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-wrapper {
  width: 100%;
  max-width: 400px;
}

.login-card {
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  border-radius: 12px;
}

.card-header {
  text-align: center;
  margin-bottom: 30px;
}

.card-header h2 {
  color: #333;
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 600;
}

.card-header p {
  color: #666;
  margin: 0;
  font-size: 16px;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .login-container {
    padding: 10px;
  }
  
  .card-header h2 {
    font-size: 24px;
  }
}
</style>