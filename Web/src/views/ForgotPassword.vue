<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useErrorHandler } from '../composables/useErrorHandler'
import { sendForgotPasswordCode, resetPassword } from '../services/user'
import Icon from '../components/Icon.vue'

const router = useRouter()
const { handleFormSubmit, showSuccessToast } = useErrorHandler()

// 步骤：1=输入邮箱 2=设置新密码
const step = ref(1)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null
const isLoading = ref(false)

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const errors = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const showPassword = reactive({ new: false, confirm: false })

// 页面卸载时清理 timer
onUnmounted(() => {
  if (timer) { clearInterval(timer); timer = null }
})

const clearErrors = () => {
  Object.assign(errors, { email: '', code: '', newPassword: '', confirmPassword: '' })
}

const validateEmail = () => {
  clearErrors()
  if (!form.email.trim()) { errors.email = '请输入邮箱地址'; return false }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { errors.email = '请输入有效的邮箱地址'; return false }
  return true
}

const validatePassword = () => {
  clearErrors()
  let valid = true
  if (!form.code.trim()) { errors.code = '请输入验证码'; valid = false }
  if (!form.newPassword) { errors.newPassword = '请输入新密码'; valid = false }
  else if (form.newPassword.length < 6) { errors.newPassword = '密码至少6位'; valid = false }
  if (form.newPassword !== form.confirmPassword) { errors.confirmPassword = '两次输入的密码不一致'; valid = false }
  return valid
}

const handleSendCode = async () => {
  if (!validateEmail()) return
  isLoading.value = true
  try {
    const result = await handleFormSubmit(async () => await sendForgotPasswordCode(form.email))
    if (result) {
      showSuccessToast('验证码已发送到您的邮箱')
      step.value = 2
      startCountdown()
    }
  } finally {
    isLoading.value = false
  }
}

const handleResetPassword = async () => {
  if (!validatePassword()) return
  isLoading.value = true
  try {
    const result = await handleFormSubmit(async () => await resetPassword({
      email: form.email,
      code: form.code,
      newPassword: form.newPassword
    }))
    if (result) {
      showSuccessToast('密码重置成功！请使用新密码登录')
      setTimeout(() => router.push('/login'), 800)
    }
  } finally {
    isLoading.value = false
  }
}

const startCountdown = () => {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (timer) clearInterval(timer)
      timer = null
    }
  }, 1000)
}

const handleResendCode = async () => {
  if (countdown.value > 0) return
  await handleSendCode()
}
</script>

<template>
  <div class="login-page">
    <div class="bg-decoration">
      <div class="bg-circle circle-1"></div>
      <div class="bg-circle circle-2"></div>
    </div>

    <div class="card-container">
      <div class="card-left">
        <div class="form-wrapper">
          <div class="card-header">
            <h2>重置密码</h2>
            <p>{{ step === 1 ? '输入注册邮箱，获取验证码' : '输入验证码并设置新密码' }}</p>
          </div>

          <!-- 步骤指示器 -->
          <div class="steps">
            <div class="step" :class="{ active: step >= 1, done: step > 1 }">
              <div class="step-circle">{{ step > 1 ? '✓' : '1' }}</div>
              <span>验证邮箱</span>
            </div>
            <div class="step-line" :class="{ active: step > 1 }"></div>
            <div class="step" :class="{ active: step >= 2 }">
              <div class="step-circle">2</div>
              <span>设置密码</span>
            </div>
          </div>

          <!-- 步骤1：输入邮箱 -->
          <form v-if="step === 1" @submit.prevent="handleSendCode" class="login-form">
            <div class="form-group">
              <label>邮箱地址</label>
              <div class="input-wrapper">
                <Icon name="mail" size="18" class="input-icon" />
                <input v-model="form.email" type="email" placeholder="请输入注册邮箱" :class="{ error: errors.email }" />
              </div>
              <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
            </div>

            <button type="submit" class="submit-btn" :disabled="isLoading">
              <span v-if="isLoading" class="loading-spinner"></span>
              <Icon v-else name="send" size="18" />
              {{ isLoading ? '发送中...' : '发送验证码' }}
            </button>
          </form>

          <!-- 步骤2：验证码 + 新密码 -->
          <form v-else @submit.prevent="handleResetPassword" class="login-form">
            <div class="form-group">
              <label>邮箱地址</label>
              <div class="input-wrapper">
                <Icon name="mail" size="18" class="input-icon" />
                <input :value="form.email" type="email" disabled class="disabled-input" />
              </div>
            </div>

            <button type="button" class="back-btn" @click="step = 1">
              <Icon name="arrow_back" size="14" /> 返回修改邮箱
            </button>

            <div class="form-group">
              <label>验证码</label>
              <div class="input-wrapper">
                <Icon name="key" size="18" class="input-icon" />
                <input v-model="form.code" type="text" placeholder="请输入6位验证码" maxlength="6" :class="{ error: errors.code }" />
                <button type="button" class="resend-btn" :disabled="countdown > 0" @click="handleResendCode">
                  {{ countdown > 0 ? `${countdown}s` : '重新发送' }}
                </button>
              </div>
              <span v-if="errors.code" class="error-message">{{ errors.code }}</span>
            </div>

            <div class="form-group">
              <label>新密码</label>
              <div class="input-wrapper">
                <Icon name="lock" size="18" class="input-icon" />
                <input v-model="form.newPassword" :type="showPassword.new ? 'text' : 'password'" placeholder="至少6位" :class="{ error: errors.newPassword }" />
                <button type="button" class="toggle-password" @click="showPassword.new = !showPassword.new">
                  <Icon :name="showPassword.new ? 'visibility_off' : 'visibility'" size="18" />
                </button>
              </div>
              <span v-if="errors.newPassword" class="error-message">{{ errors.newPassword }}</span>
            </div>

            <div class="form-group">
              <label>确认密码</label>
              <div class="input-wrapper">
                <Icon name="lock" size="18" class="input-icon" />
                <input v-model="form.confirmPassword" :type="showPassword.confirm ? 'text' : 'password'" placeholder="请再次输入新密码" :class="{ error: errors.confirmPassword }" />
                <button type="button" class="toggle-password" @click="showPassword.confirm = !showPassword.confirm">
                  <Icon :name="showPassword.confirm ? 'visibility_off' : 'visibility'" size="18" />
                </button>
              </div>
              <span v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</span>
            </div>

            <button type="submit" class="submit-btn" :disabled="isLoading">
              <span v-if="isLoading" class="loading-spinner"></span>
              <Icon v-else name="lock" size="18" />
              {{ isLoading ? '重置中...' : '重置密码' }}
            </button>
          </form>

          <div class="card-footer">
            <p>想起密码了？<router-link to="/login" class="link-btn">返回登录</router-link></p>
          </div>
        </div>
      </div>

      <div class="card-right">
        <div class="brand-content">
          <div class="brand-logo">
            <Icon name="key" size="48" class="logo-icon" />
          </div>
          <h1 class="brand-title">LiuTech</h1>
          <p class="brand-subtitle">密码重置</p>
          <p class="brand-desc">安全验证，保护您的账户<br />几步即可恢复访问</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.bg-decoration { position: absolute; inset: 0; z-index: 0; }
.bg-circle { position: absolute; border-radius: 50%; opacity: 0.08; }
.circle-1 { width: 500px; height: 500px; background: var(--color-primary); top: -150px; left: -150px; }
.circle-2 { width: 400px; height: 400px; background: var(--color-accent); bottom: -100px; right: -100px; }

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

.card-left { background: var(--bg-card); order: 1; }
.form-wrapper { padding: 40px; }

.card-header {
  margin-bottom: 24px;
  h2 { font-size: 1.5rem; font-weight: 600; color: var(--text-title); margin: 0 0 6px 0; }
  p { color: var(--text-subtle); font-size: 0.9rem; margin: 0; }
}

/* 步骤指示器 */
.steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 32px;
}
.step {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.85rem;
  &.active { color: var(--color-primary); }
  &.done { color: var(--text-muted); }
}
.step-circle {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8rem;
  font-weight: 600;
  border: 2px solid var(--border-base);
  background: var(--bg-soft);
}
/* 修复 H8: 选择器从 .step 上下文匹配 .step-circle */
.step.active .step-circle { border-color: var(--color-primary); background: var(--color-primary); color: white; }
.step.done .step-circle { border-color: var(--text-muted); background: var(--text-muted); color: white; }
.step-line {
  width: 40px;
  height: 2px;
  background: var(--border-base);
  &.active { background: var(--color-primary); }
}

.login-form { margin-bottom: 16px; }
.form-group { margin-bottom: 16px; }
.form-group label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-main);
  font-weight: 500;
  font-size: 0.9rem;
}
.input-wrapper { position: relative; display: flex; align-items: center; }
.input-icon { position: absolute; left: 14px; color: var(--text-muted); pointer-events: none; }

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
  &:hover { color: var(--text-main); }
}

.disabled-input {
  opacity: 0.7;
  cursor: not-allowed;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 0.85rem;
  cursor: pointer;
  padding: 4px 0;
  margin-bottom: 16px;
  &:hover { text-decoration: underline; }
}

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

.error-message {
  display: block;
  color: var(--color-error);
  font-size: 0.8rem;
  margin-top: 6px;
  padding-left: 4px;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

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

.card-footer {
  text-align: center;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
  p { color: var(--text-subtle); font-size: 0.9rem; margin: 0; }
}
.link-btn {
  color: var(--color-primary);
  font-weight: 600;
  text-decoration: none;
  &:hover { text-decoration: underline; }
}

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
.brand-content { text-align: center; position: relative; z-index: 1; }
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
}
.logo-icon { color: white; }
.brand-title { font-size: 1.8rem; font-weight: 700; color: white; margin: 0 0 6px 0; letter-spacing: -0.5px; }
.brand-subtitle { font-size: 0.95rem; color: rgba(255, 255, 255, 0.8); margin: 0 0 24px 0; }
.brand-desc { font-size: 0.85rem; color: rgba(255, 255, 255, 0.7); line-height: 1.8; margin: 0; }

@include respond(lg) {
  .card-container { grid-template-columns: 1fr; width: 420px; max-width: calc(100vw - 40px); }
  .card-left { order: 2; }
  .card-right { order: 1; min-height: 180px; padding: 24px; }
  .brand-logo { width: 56px; height: 56px; margin-bottom: 12px; border-radius: 14px; }
  .brand-title { font-size: 1.4rem; }
  .brand-subtitle { font-size: 0.85rem; margin-bottom: 12px; }
  .brand-desc { font-size: 0.8rem; }
  .form-wrapper { padding: 24px; }
}
@include respond(sm) {
  .card-container { max-width: calc(100vw - 24px); }
  .form-wrapper { padding: 20px; }
  .card-header h2 { font-size: 1.3rem; }
}
</style>

