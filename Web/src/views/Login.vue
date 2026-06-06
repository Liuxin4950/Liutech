<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useErrorHandler } from '../composables/useErrorHandler'
import type { RegisterRequest } from '../services/user'
import Icon from '../components/Icon.vue'

const router = useRouter()
const userStore = useUserStore()
const { handleFormSubmit, showSuccess, clearError, showSuccessToast } = useErrorHandler()

const isLogin = ref(true)
const loginMode = ref<'password' | 'email'>('password') // 密码登录或邮箱验证码登录

const loginForm = reactive({ username: '', password: '' })
const emailLoginForm = reactive({ email: '', code: '' })
const emailLoginErrors = reactive({ email: '', code: '' })
const emailCountdown = ref(0)
let emailTimer: ReturnType<typeof setInterval> | null = null
const registerForm = reactive({ username: '', email: '', code: '', nickname: '' })

const errors = reactive({ username: '', email: '', password: '', code: '', confirmPassword: '' })

const showPassword = reactive({ login: false, register: false, confirm: false })

const toggleMode = () => { isLogin.value = !isLogin.value; clearError() }

const clearForms = () => {
  Object.assign(loginForm, { username: '', password: '' })
  Object.assign(registerForm, { username: '', email: '', code: '', nickname: '' })
}

const clearErrors = () => {
  Object.assign(errors, { username: '', email: '', password: '', code: '', confirmPassword: '' })
}

const validateForm = () => {
  clearErrors()
  let isValid = true
  if (isLogin.value) {
    if (!loginForm.username.trim()) { errors.username = '请输入用户名'; isValid = false }
    if (!loginForm.password) { errors.password = '请输入密码'; isValid = false }
  } else {
    if (!registerForm.username.trim()) { errors.username = '请输入用户名'; isValid = false }
    else if (registerForm.username.length < 3 || registerForm.username.length > 20) { errors.username = '用户名长度应为3-20位字符'; isValid = false }
    if (!registerForm.email.trim()) { errors.email = '请输入邮箱地址'; isValid = false }
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) { errors.email = '请输入有效的邮箱地址'; isValid = false }
    if (!registerForm.code.trim()) { errors.code = '请输入验证码'; isValid = false }
  }
  return isValid
}

const handleLogin = async () => {
  if (!validateForm()) return
  const result = await handleFormSubmit(async () => await userStore.login(loginForm.username, loginForm.password))
  if (result) { showSuccessToast("登录成功！"); setTimeout(() => router.push((router.currentRoute.value.query.redirect as string) || '/'), 600) }
}

const handleRegister = async () => {
  if (!validateForm()) return
  const result = await handleFormSubmit(async () => {
    const data: RegisterRequest = { username: registerForm.username, email: registerForm.email, code: registerForm.code, nickname: registerForm.nickname || undefined }
    return await userStore.register(data)
  })
  if (result) { showSuccess('注册成功！请登录您的账户'); isLogin.value = true; clearForms() }
}

const handleSubmit = () => { isLogin.value ? handleLogin() : handleRegister() }

// 注册验证码相关
const registerCountdown = ref(0)
let registerTimer: ReturnType<typeof setInterval> | null = null

// M13: 页面卸载时清理所有 timer
onUnmounted(() => {
  if (registerTimer) { clearInterval(registerTimer); registerTimer = null }
  if (emailTimer) { clearInterval(emailTimer); emailTimer = null }
})

const handleSendRegisterCode = async () => {
  errors.email = ''
  if (!registerForm.email.trim()) { errors.email = '请输入邮箱地址'; return }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) { errors.email = '请输入有效的邮箱地址'; return }
  const { sendRegisterCode } = await import('../services/user')
  const result = await handleFormSubmit(async () => await sendRegisterCode(registerForm.email))
  if (result) { showSuccessToast('验证码已发送到您的邮箱'); startRegisterCountdown() }
}

const startRegisterCountdown = () => {
  if (registerTimer) { clearInterval(registerTimer); registerTimer = null }
  registerCountdown.value = 60
  registerTimer = setInterval(() => { registerCountdown.value--; if (registerCountdown.value <= 0 && registerTimer) { clearInterval(registerTimer); registerTimer = null } }, 1000)
}

// 邮箱登录相关方法
import { sendEmailLoginCode } from '../services/user'

const handleSendEmailCode = async () => {
  emailLoginErrors.email = ''
  if (!emailLoginForm.email.trim()) { emailLoginErrors.email = '请输入邮箱地址'; return }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLoginForm.email)) { emailLoginErrors.email = '请输入有效的邮箱地址'; return }
  const result = await handleFormSubmit(async () => await sendEmailLoginCode(emailLoginForm.email))
  if (result) { showSuccessToast('验证码已发送到您的邮箱'); startEmailCountdown() }
}

const handleEmailLogin = async () => {
  emailLoginErrors.code = ''
  if (!emailLoginForm.code.trim()) { emailLoginErrors.code = '请输入验证码'; return }
  const result = await handleFormSubmit(async () => await userStore.emailLogin(emailLoginForm.email, emailLoginForm.code))
  if (result) { showSuccessToast('登录成功！'); setTimeout(() => router.push((router.currentRoute.value.query.redirect as string) || '/'), 600) }
}

const startEmailCountdown = () => {
  if (emailTimer) { clearInterval(emailTimer); emailTimer = null }
  emailCountdown.value = 60
  emailTimer = setInterval(() => { emailCountdown.value--; if (emailCountdown.value <= 0 && emailTimer) { clearInterval(emailTimer); emailTimer = null } }, 1000)
}
</script>

<template>
  <div class="login-page">
    <!-- 背景 -->
    <div class="bg-decoration">
      <div class="bg-circle circle-1"></div>
      <div class="bg-circle circle-2"></div>
      <div class="bg-circle circle-3"></div>
    </div>

    <!-- 返回首页 -->
    <router-link to="/" class="back-home">
      <Icon name="arrow_back" size="16" /> 返回首页
    </router-link>

    <!-- 拼接卡片容器 -->
    <div class="card-container">
      <!-- 左侧表单区 -->
      <div class="card-left">
        <div class="form-wrapper">
          <!-- 标题 -->
          <div class="card-header">
            <h2>{{ isLogin ? '欢迎回来' : '创建账户' }}</h2>
            <p>{{ isLogin ? '请登录您的账户' : '开始您的技术之旅' }}</p>
          </div>

          <!-- 切换标签 -->
          <div class="mode-tabs">
            <button class="mode-tab" :class="{ active: isLogin }" @click="isLogin = true">登录</button>
            <button class="mode-tab" :class="{ active: !isLogin }" @click="isLogin = false">注册</button>
            <div class="tab-indicator" :class="{ 'tab-indicator-right': !isLogin }"></div>
          </div>

          <!-- 登录模式切换（仅登录时显示） -->
          <div v-if="isLogin" class="login-mode-tabs">
            <button type="button" class="mode-tab-sm" :class="{ active: loginMode === 'password' }" @click="loginMode = 'password'">
              <Icon name="lock" size="14" /> 密码登录
            </button>
            <button type="button" class="mode-tab-sm" :class="{ active: loginMode === 'email' }" @click="loginMode = 'email'">
              <Icon name="mail" size="14" /> 邮箱登录
            </button>
          </div>

          <!-- 表单 -->
          <form v-if="loginMode === 'password' || !isLogin" @submit.prevent="handleSubmit" class="login-form">
            <div class="form-group">
              <label>用户名</label>
              <div class="input-wrapper">
                <Icon name="user" size="18" class="input-icon" />
                <input v-if="isLogin" v-model="loginForm.username" type="text" placeholder="请输入用户名" :class="{ error: errors.username }" />
                <input v-else v-model="registerForm.username" type="text" placeholder="请输入用户名" :class="{ error: errors.username }" />
              </div>
              <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
            </div>

            <transition name="slide-fade">
              <div v-if="!isLogin" class="form-group">
                <label>邮箱地址</label>
                <div class="input-wrapper">
                  <Icon name="mail" size="18" class="input-icon" />
                  <input v-model="registerForm.email" type="email" placeholder="请输入邮箱地址" :class="{ error: errors.email }" />
                  <button type="button" class="resend-btn" :disabled="registerCountdown > 0" @click="handleSendRegisterCode">
                    {{ registerCountdown > 0 ? `${registerCountdown}s` : '获取验证码' }}
                  </button>
                </div>
                <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
              </div>
            </transition>

            <transition name="slide-fade">
              <div v-if="!isLogin" class="form-group">
                <label>验证码</label>
                <div class="input-wrapper">
                  <Icon name="key" size="18" class="input-icon" />
                  <input v-model="registerForm.code" type="text" placeholder="请输入6位验证码" maxlength="6" :class="{ error: errors.code }" />
                </div>
                <span v-if="errors.code" class="error-message">{{ errors.code }}</span>
              </div>
            </transition>

            <transition name="slide-fade">
              <div v-if="!isLogin" class="form-group">
                <label>昵称 (可选)</label>
                <div class="input-wrapper">
                  <Icon name="user" size="18" class="input-icon" />
                  <input v-model="registerForm.nickname" type="text" placeholder="注册后可在个人资料修改" />
                </div>
              </div>
            </transition>

            <div v-if="isLogin" class="form-group">
              <label>密码</label>
              <div class="input-wrapper">
                <Icon name="lock" size="18" class="input-icon" />
                <input v-model="loginForm.password" :type="showPassword.login ? 'text' : 'password'" placeholder="请输入密码" :class="{ error: errors.password }" />
                <button type="button" class="toggle-password" @click="showPassword.login = !showPassword.login">
                  <Icon :name="showPassword.login ? 'visibility_off' : 'visibility'" size="18" />
                </button>
              </div>
              <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
            </div>
            <div v-if="isLogin" class="forgot-password">
              <router-link to="/forgot-password" class="link-btn-sm">忘记密码？</router-link>
            </div>



            <button type="submit" class="submit-btn" :disabled="userStore.isLoading">
              <span v-if="userStore.isLoading" class="loading-spinner"></span>
              <Icon v-else :name="isLogin ? 'login' : 'person_add'" size="18" />
              {{ userStore.isLoading ? '处理中...' : (isLogin ? '登 录' : '立即注册') }}
            </button>
          </form>

          <!-- 邮箱验证码登录表单 -->
          <form v-if="isLogin && loginMode === 'email'" @submit.prevent="handleEmailLogin" class="login-form">
            <div class="form-group">
              <label>邮箱地址</label>
              <div class="input-wrapper">
                <Icon name="mail" size="18" class="input-icon" />
                <input v-model="emailLoginForm.email" type="email" placeholder="请输入注册邮箱" :class="{ error: emailLoginErrors.email }" />
              </div>
              <span v-if="emailLoginErrors.email" class="error-message">{{ emailLoginErrors.email }}</span>
            </div>

            <div class="form-group">
              <label>验证码</label>
              <div class="input-wrapper">
                <Icon name="key" size="18" class="input-icon" />
                <input v-model="emailLoginForm.code" type="text" placeholder="请输入6位验证码" maxlength="6" :class="{ error: emailLoginErrors.code }" />
                <button type="button" class="resend-btn" :disabled="emailCountdown > 0" @click="handleSendEmailCode">
                  {{ emailCountdown > 0 ? `${emailCountdown}s` : '获取验证码' }}
                </button>
              </div>
              <span v-if="emailLoginErrors.code" class="error-message">{{ emailLoginErrors.code }}</span>
            </div>

            <button type="submit" class="submit-btn" :disabled="userStore.isLoading">
              <span v-if="userStore.isLoading" class="loading-spinner"></span>
              <Icon v-else name="login" size="18" />
              {{ userStore.isLoading ? '处理中...' : '登 录' }}
            </button>
          </form>

          <div class="card-footer">
            <p v-if="isLogin">还没有账户？<button type="button" @click="isLogin = false" class="link-btn">立即注册</button></p>
            <p v-else>已有账户？<button type="button" @click="isLogin = true" class="link-btn">立即登录</button></p>
          </div>
        </div>
      </div>

      <!-- 右侧品牌区 -->
      <div class="card-right">
        <div class="brand-content">
          <router-link to="/" class="brand-link">
          <div class="brand-logo">
            <Icon name="code" size="48" class="logo-icon" />
          </div>
          <h1 class="brand-title">LiuTech</h1>
          <p class="brand-subtitle">技术博客平台</p>
          <p class="brand-desc">分享技术，记录成长<br />探索无限可能</p>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

// 页面容器
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

// 背景装饰
.bg-decoration {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.08;
}

.circle-1 {
  width: 500px;
  height: 500px;
  background: var(--color-primary);
  top: -150px;
  left: -150px;
}

.circle-2 {
  width: 400px;
  height: 400px;
  background: var(--color-accent);
  bottom: -100px;
  right: -100px;
}

.circle-3 {
  width: 300px;
  height: 300px;
  background: var(--color-primary);
  top: 40%;
  left: 10%;
}


// 返回首页链接
.back-home {
  position: absolute;
  top: 20px;
  left: 24px;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
  font-size: 0.85rem;
  text-decoration: none;
  padding: 6px 12px;
  border-radius: 6px;
  transition: all 0.2s ease;

  &:hover {
    color: var(--color-primary);
    background: var(--bg-soft);
  }
}

// 拼接卡片容器
.card-container {
  display: grid;
  grid-template-columns: 1fr 320px;
  width: 800px;
  max-width: calc(100vw - 40px);
  position: relative;
  z-index: 1;
  box-shadow: var(--shadow-lg);
  border-radius: 16px;
  overflow: hidden;
}

// 左侧表单区
.card-left {
  background: var(--bg-card);
  order: 1;
}

.form-wrapper {
  padding: 40px;
}

.card-header {
  margin-bottom: 24px;
  h2 { font-size: 1.5rem; font-weight: 600; color: var(--text-title); margin: 0 0 6px 0; }
  p { color: var(--text-subtle); font-size: 0.9rem; margin: 0; }
}

// 切换标签
.mode-tabs {
  display: flex;
  position: relative;
  margin-bottom: 24px;
  background: var(--bg-soft);
  border-radius: 10px;
  padding: 4px;
}

.mode-tab {
  flex: 1;
  padding: 10px 16px;
  border: none;
  background: none;
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--text-subtle);
  cursor: pointer;
  transition: color 0.3s ease;
  position: relative;
  z-index: 1;
  &.active { color: var(--text-title); }
  &:hover:not(.active) { color: var(--text-main); }
}

.tab-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background: var(--bg-card);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  &.tab-indicator-right { transform: translateX(100%); }
}

// 表单样式
.login-form { margin-bottom: 16px; }

/* 登录模式切换 */
.login-mode-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.mode-tab-sm {
  flex: 1;
  padding: 8px 12px;
  border: 1.5px solid var(--border-base);
  border-radius: 8px;
  background: var(--bg-soft);
  color: var(--text-subtle);
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s ease;
  &.active {
    border-color: var(--color-primary);
    background: var(--color-primary);
    color: white;
  }
  &:hover:not(.active) { border-color: var(--border-strong); }
}

/* 忘记密码链接 */
.forgot-password {
  text-align: right;
  margin-top: -8px;
  margin-bottom: 16px;
}
.link-btn-sm {
  color: var(--color-primary);
  font-size: 0.85rem;
  text-decoration: none;
  &:hover { text-decoration: underline; }
}

/* 邮箱登录重新发送按钮 */
.resend-btn {
  position: absolute;
  right: 12px;
  background: var(--color-primary);
  border: none;
  color: #fff;
  font-size: 0.8rem;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s ease;
  &:hover:not(:disabled) { background: var(--color-primary-dark); }
  &:disabled { background: var(--border-base); color: var(--text-muted); cursor: not-allowed; }
}

.form-group { margin-bottom: 16px; }

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-main);
  font-weight: 500;
  font-size: 0.9rem;
}

.input-wrapper { position: relative; display: flex; align-items: center; }

.input-icon {
  position: absolute;
  left: 14px;
  color: var(--text-muted);
  pointer-events: none;
}

.form-group input {
  width: 100%;
  padding: 12px 14px 12px 44px;
  border: 1.5px solid var(--border-base);
  border-radius: 10px;
  font-size: 0.95rem;
  background: var(--bg-soft);
  color: var(--text-main);
  transition: all 0.25s ease;
  &::placeholder { color: var(--text-muted); }
  &:hover { border-color: var(--border-strong); }
  &:focus { outline: none; border-color: var(--color-primary); box-shadow: 0 0 0 4px rgba(var(--color-primary-rgb), 0.1); }
  &.error { border-color: var(--color-error); box-shadow: 0 0 0 4px rgba(234, 67, 53, 0.1); }
  /* M14: 验证码输入框右侧留空间给获取验证码按钮 */
  .input-wrapper:has(.resend-btn) & { padding-right: 96px; }
}

.toggle-password {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s;
  &:hover { color: var(--text-main); }
}

.error-message {
  display: block;
  color: var(--color-error);
  font-size: 0.8rem;
  margin-top: 6px;
  padding-left: 4px;
}

// 提交按钮
.submit-btn {
  width: 100%;
  padding: 14px;
  margin-top: 8px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  &:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(var(--color-primary-rgb), 0.4); }
  &:disabled { opacity: 0.7; cursor: not-allowed; }
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top: 2px solid currentColor;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

// 底部链接
.card-footer {
  text-align: center;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
  p { color: var(--text-subtle); font-size: 0.9rem; margin: 0; }
}

.link-btn {
  color: var(--color-primary);
  background: none;
  border: none;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.9rem;
  margin-left: 4px;
  transition: color 0.2s;
  &:hover { color: var(--color-primary-dark); text-decoration: underline; }
}

// ============================================
// 右侧品牌区
.card-right {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 40px;
  position: relative;
  overflow: hidden;
  order: 2;
}


// 品牌区可点击跳转首页
.brand-link {
  text-decoration: none;
  display: block;
  cursor: pointer;
  transition: opacity 0.2s ease;

  &:hover { opacity: 0.9; }
}

.brand-content {
  text-align: center;
  position: relative;
  z-index: 1;
}

.brand-logo {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
  animation: float 6s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.logo-icon { color: white; }

.brand-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: white;
  margin: 0 0 6px 0;
  letter-spacing: -0.5px;
}

.brand-subtitle {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.8);
  margin: 0 0 24px 0;
}

.brand-desc {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1.8;
  margin: 0;
}

// 过渡动画
.slide-fade-enter-active { transition: all 0.3s ease-out; }
.slide-fade-leave-active { transition: all 0.2s cubic-bezier(1, 0.5, 0.8, 1); }
.slide-fade-enter-from, .slide-fade-leave-to { transform: translateY(-10px); opacity: 0; }

// ============================================
// 响应式 - 移动端上下堆叠
@include respond(lg) {
  .card-container {
    grid-template-columns: 1fr;
    width: 420px;
    max-width: calc(100vw - 40px);
  }

  .card-left { order: 2; }
  .card-right { order: 1; min-height: 180px; padding: 24px; }

  .brand-logo { width: 56px; height: 56px; margin-bottom: 12px; border-radius: 14px; :deep(svg) { width: 28px; height: 28px; } }
  .brand-title { font-size: 1.4rem; }
  .brand-subtitle { font-size: 0.85rem; margin-bottom: 12px; }
  .brand-desc { font-size: 0.8rem; }

  .form-wrapper { padding: 24px; }
}

@include respond(sm) {
  .card-container { max-width: calc(100vw - 24px); }
  .form-wrapper { padding: 20px; }
  .card-header h2 { font-size: 1.3rem; }
  .submit-btn { padding: 12px; }
}
</style>

