<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import type { RegisterRequest } from '../services/user'

const router = useRouter()
const userStore = useUserStore()

// 表单状态
const isLogin = ref(true) // true: 登录模式, false: 注册模式

// 登录表单数据
const loginForm = reactive({
  username: '',
  password: ''
})

// 注册表单数据
const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})

// 表单验证错误信息
const errors = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

/**
 * 切换登录/注册模式
 */
const toggleMode = () => {
  isLogin.value = !isLogin.value
  clearErrors()
  // 注意：不清空表单数据，让用户可以在两种模式间保持已输入的数据
  // 只有在提交成功后才清空表单
}

/**
 * 清空表单
 */
const clearForms = () => {
  Object.assign(loginForm, { username: '', password: '' })
  Object.assign(registerForm, {
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    nickname: ''
  })
}

/**
 * 清空当前模式的表单
 */
const clearCurrentForm = () => {
  if (isLogin.value) {
    Object.assign(loginForm, { username: '', password: '' })
  } else {
    Object.assign(registerForm, {
      username: '',
      email: '',
      password: '',
      confirmPassword: '',
      nickname: ''
    })
  }
}

/**
 * 清空错误信息
 */
const clearErrors = () => {
  Object.assign(errors, {
    username: '',
    email: '',
    password: '',
    confirmPassword: ''
  })
}

/**
 * 表单验证
 */
const validateForm = () => {
  clearErrors()
  let isValid = true

  if (isLogin.value) {
    // 登录验证
    if (!loginForm.username.trim()) {
      errors.username = '请输入用户名'
      isValid = false
    }
    if (!loginForm.password) {
      errors.password = '请输入密码'
      isValid = false
    }
  } else {
    // 注册验证
    if (!registerForm.username.trim()) {
      errors.username = '请输入用户名'
      isValid = false
    } else if (registerForm.username.length < 3 || registerForm.username.length > 20) {
      errors.username = '用户名长度应为3-20位字符'
      isValid = false
    }

    if (!registerForm.email.trim()) {
      errors.email = '请输入邮箱地址'
      isValid = false
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) {
      errors.email = '请输入有效的邮箱地址'
      isValid = false
    }

    if (!registerForm.password) {
      errors.password = '请输入密码'
      isValid = false
    } else if (registerForm.password.length < 8) {
      errors.password = '密码至少8位字符'
      isValid = false
    } else if (!/(?=.*[a-zA-Z])(?=.*\d)/.test(registerForm.password)) {
      errors.password = '密码必须包含字母和数字'
      isValid = false
    }

    if (registerForm.password !== registerForm.confirmPassword) {
      errors.confirmPassword = '两次输入的密码不一致'
      isValid = false
    }
  }

  return isValid
}

/**
 * 处理登录
 */
const handleLogin = async () => {
  if (!validateForm()) return

  try {
    // 使用用户状态管理进行登录
    await userStore.login(loginForm.username, loginForm.password)
    
    // 登录成功后跳转到首页
    router.push('/')
    alert('登录成功！')
  } catch (error) {
    console.error('登录失败:', error)
    
    // 根据错误信息显示对应的错误提示
    if (error instanceof Error) {
      const message = error.message
      
      // 根据 API 文档的错误码进行处理
      if (message.includes('用户名或密码错误') || message.includes('1004')) {
        errors.username = '用户名或密码错误'
      } else if (message.includes('用户不存在') || message.includes('1001')) {
        errors.username = '用户不存在'
      } else if (message.includes('账户已被禁用') || message.includes('1005')) {
        errors.username = '账户已被禁用，请联系管理员'
      } else if (message.includes('网络连接失败')) {
        errors.username = '网络连接失败，请检查网络设置'
      } else {
        errors.username = message || '登录失败，请稍后重试'
      }
    } else {
      errors.username = '登录失败，请稍后重试'
    }
  }
}

/**
 * 处理注册
 */
const handleRegister = async () => {
  if (!validateForm()) return

  try {
    // 构建注册数据
    const registerData: RegisterRequest = {
      username: registerForm.username,
      email: registerForm.email,
      password: registerForm.password,
      nickname: registerForm.nickname || undefined
    }
    
    // 使用用户状态管理进行注册
    await userStore.register(registerData)
    
    // 注册成功后切换到登录模式
    isLogin.value = true
    clearForms()
    alert('注册成功！请登录您的账户')
  } catch (error) {
    console.error('注册失败:', error)
    
    // 根据错误信息显示对应的错误提示
    if (error instanceof Error) {
      const message = error.message
      
      // 根据 API 文档的错误码进行处理
      if (message.includes('用户名已存在') || message.includes('1002')) {
        errors.username = '用户名已存在'
      } else if (message.includes('邮箱已被注册') || message.includes('1003')) {
        errors.email = '邮箱已被注册'
      } else if (message.includes('请求参数错误') || message.includes('400')) {
        errors.email = '请求参数错误，请检查输入信息'
      } else if (message.includes('网络连接失败')) {
        errors.email = '网络连接失败，请检查网络设置'
      } else {
        errors.email = message || '注册失败，请稍后重试'
      }
    } else {
      errors.email = '注册失败，请稍后重试'
    }
  }
}

/**
 * 提交表单
 */
const handleSubmit = () => {
  if (isLogin.value) {
    handleLogin()
  } else {
    handleRegister()
  }
}
</script>

<template>
  <div class="login-container">    
    <main class="main-content">
      <div class="login-wrapper">
        <div class="login-card">
          <!-- 标题 -->
          <div class="card-header">
            <h2>{{ isLogin ? '登录' : '注册' }}</h2>
            <p>{{ isLogin ? '欢迎回来！' : '创建您的账户' }}</p>
          </div>

          <!-- 表单 -->
          <form @submit.prevent="handleSubmit" class="login-form">
            <!-- 用户名 -->
            <div class="form-group">
              <label for="username">用户名</label>
              <input
                v-if="isLogin"
                id="username-login"
                v-model="loginForm.username"
                type="text"
                placeholder="请输入用户名"
                :class="{ 'error': errors.username }"
              />
              <input
                v-else
                id="username-register"
                v-model="registerForm.username"
                type="text"
                placeholder="请输入用户名"
                :class="{ 'error': errors.username }"
              />
              <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
            </div>

            <!-- 邮箱 (仅注册时显示) -->
            <div v-if="!isLogin" class="form-group">
              <label for="email">邮箱地址</label>
              <input
                id="email"
                v-model="registerForm.email"
                type="email"
                placeholder="请输入邮箱地址"
                :class="{ 'error': errors.email }"
              />
              <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
            </div>

            <!-- 昵称 (仅注册时显示) -->
            <div v-if="!isLogin" class="form-group">
              <label for="nickname">昵称 (可选)</label>
              <input
                id="nickname"
                v-model="registerForm.nickname"
                type="text"
                placeholder="请输入昵称"
              />
            </div>

            <!-- 密码 -->
            <div class="form-group">
              <label for="password">密码</label>
              <input
                v-if="isLogin"
                id="password-login"
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                :class="{ 'error': errors.password }"
              />
              <input
                v-else
                id="password-register"
                v-model="registerForm.password"
                type="password"
                placeholder="至少8位，包含字母和数字"
                :class="{ 'error': errors.password }"
              />
              <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
            </div>

            <!-- 确认密码 (仅注册时显示) -->
            <div v-if="!isLogin" class="form-group">
              <label for="confirmPassword">确认密码</label>
              <input
                id="confirmPassword"
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                :class="{ 'error': errors.confirmPassword }"
              />
              <span v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</span>
            </div>

            <!-- 提交按钮 -->
            <button type="submit" class="submit-btn" :disabled="userStore.isLoading">
              <span v-if="userStore.isLoading" class="loading-spinner"></span>
              {{ userStore.isLoading ? '处理中...' : (isLogin ? '登录' : '注册') }}
            </button>
          </form>

          <!-- 切换模式 -->
          <div class="card-footer">
            <p>
              {{ isLogin ? '还没有账户？' : '已有账户？' }}
              <button type="button" @click="toggleMode" class="toggle-btn">
                {{ isLogin ? '立即注册' : '立即登录' }}
              </button>
            </p>
          </div>
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
  background: linear-gradient(135deg, var(--bg-color) 0%, var(--hover-color) 100%);
}

.login-wrapper {
  width: 100%;
  max-width: 400px;
}

.login-card {
  background: var(--bg-color);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  padding: 40px;
  border: 1px solid var(--border-color);
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
  color: var(--text-color);
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 8px;
}

.card-header p {
  color: var(--text-color);
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
  color: var(--text-color);
  font-weight: 500;
  font-size: 14px;
}

.form-group input {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid var(--border-color);
  border-radius: 8px;
  font-size: 16px;
  background: var(--bg-color);
  color: var(--text-color);
  transition: all 0.3s ease;
}

.form-group input:focus {
  outline: none;
  border-color: var(--primary-color);
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
  background: var(--primary-color);
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
  background: var(--secondary-color);
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
  color: var(--text-color);
  opacity: 0.7;
  font-size: 14px;
  margin: 0;
}

.toggle-btn {
  color: var(--primary-color);
  background: none;
  border: none;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
  margin-left: 4px;
  transition: color 0.3s ease;
}

.toggle-btn:hover {
  color: var(--secondary-color);
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