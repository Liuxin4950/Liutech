<template>
  <div v-if="visible" class="modal-overlay" @click="close">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <div class="modal-title-wrap">
          <h3>{{ isLogin ? '欢迎回来' : '创建账户' }}</h3>
          <p v-if="message" class="modal-tip">{{ message }}</p>
        </div>
        <button class="close-btn" @click="close" aria-label="关闭">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
      </div>

      <div class="modal-body">
        <!-- 登录/注册切换 -->
        <div class="mode-tabs">
          <button type="button" class="mode-tab" :class="{ active: isLogin }" @click="isLogin || toggleMode()">登录</button>
          <button type="button" class="mode-tab" :class="{ active: !isLogin }" @click="!isLogin || toggleMode()">注册</button>
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

        <!-- 密码登录 / 注册表单 -->
        <form v-if="loginMode === 'password' || !isLogin" @submit.prevent="handleSubmit" class="auth-form">
          <!-- 登录块 -->
          <template v-if="isLogin">
            <div class="form-group">
              <label>用户名 / 邮箱</label>
              <div class="input-wrapper">
                <Icon name="user" size="18" class="input-icon" />
                <input ref="usernameInput" v-model="loginForm.username" type="text" placeholder="请输入用户名或邮箱" :class="{ error: errors.username }" />
              </div>
              <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
            </div>

            <div class="form-group">
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

            <div class="forgot-password">
              <router-link to="/forgot-password" class="link-btn-sm" @click="close">忘记密码？</router-link>
            </div>
          </template>

          <!-- 注册块 -->
          <template v-else>
            <div class="form-group">
              <label>邮箱地址</label>
              <div class="input-wrapper">
                <Icon name="mail" size="18" class="input-icon" />
                <input v-model="registerForm.email" type="email" placeholder="请输入邮箱地址" :class="{ error: errors.email }" />
                <button type="button" class="resend-btn" :disabled="registerCountdown > 0 || isSending" @click="handleSendRegisterCode">
                  {{ registerCountdown > 0 ? `${registerCountdown}s` : (isSending ? '发送中...' : '获取验证码') }}
                </button>
              </div>
              <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
            </div>

            <div class="form-group">
              <label>验证码</label>
              <div class="input-wrapper">
                <Icon name="key" size="18" class="input-icon" />
                <input v-model="registerForm.code" type="text" placeholder="请输入6位验证码" maxlength="6" :class="{ error: errors.code }" />
              </div>
              <span v-if="errors.code" class="error-message">{{ errors.code }}</span>
            </div>

            <div class="form-group">
              <label>密码</label>
              <div class="input-wrapper">
                <Icon name="lock" size="18" class="input-icon" />
                <input v-model="registerForm.password" :type="showPassword.login ? 'text' : 'password'" placeholder="至少6位" :class="{ error: errors.password }" />
                <button type="button" class="toggle-password" @click="showPassword.login = !showPassword.login">
                  <Icon :name="showPassword.login ? 'visibility_off' : 'visibility'" size="18" />
                </button>
              </div>
              <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
            </div>

            <p class="register-tip">用户名将自动生成，注册后可使用邮箱登录</p>
          </template>

          <button type="submit" class="submit-btn" :disabled="userStore.isLoading">
            <span v-if="userStore.isLoading" class="spinner-sm spinner-abs"></span>
            <Icon v-else :name="isLogin ? 'login' : 'person_add'" size="18" class="btn-icon" />
            {{ userStore.isLoading ? '处理中...' : (isLogin ? '登 录' : '立即注册') }}
          </button>
        </form>

        <!-- 邮箱验证码登录表单 -->
        <form v-if="isLogin && loginMode === 'email'" @submit.prevent="handleEmailLogin" class="auth-form">
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
              <button type="button" class="resend-btn" :disabled="emailCountdown > 0 || isSending" @click="handleSendEmailCode">
                {{ emailCountdown > 0 ? `${emailCountdown}s` : (isSending ? '发送中...' : '获取验证码') }}
              </button>
            </div>
            <span v-if="emailLoginErrors.code" class="error-message">{{ emailLoginErrors.code }}</span>
          </div>

          <button type="submit" class="submit-btn" :disabled="userStore.isLoading">
            <span v-if="userStore.isLoading" class="spinner-sm spinner-abs"></span>
            <Icon v-else name="login" size="18" class="btn-icon" />
            {{ userStore.isLoading ? '处理中...' : '登 录' }}
          </button>
        </form>

        <!-- 底部切换 -->
        <div class="card-footer">
          <p v-if="isLogin">还没有账户？<button type="button" @click="toggleMode()" class="link-btn">立即注册</button></p>
          <p v-else>已有账户？<button type="button" @click="toggleMode()" class="link-btn">立即登录</button></p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthForm } from '@/composables/useAuthForm'
import { useAuthModalStore } from '@/stores/authModal'
import Icon from './Icon.vue'

interface Props {
  visible: boolean
  message?: string
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'close'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const router = useRouter()
const authModalStore = useAuthModalStore()

// 表单逻辑与登录页共用 useAuthForm；弹窗版登录成功后：关闭弹窗、重置表单，
// 若为路由守卫拦截（记录过 redirect）则自动跳回目标页
const {
  isLogin, loginMode, loginForm, emailLoginForm, emailLoginErrors, emailCountdown,
  registerForm, isSending, errors, showPassword, registerCountdown, userStore,
  toggleMode, handleSubmit, handleSendRegisterCode, handleSendEmailCode,
  handleEmailLogin, cleanupTimers, resetForm
} = useAuthForm({
  onLoginSuccess: () => {
    const redirect = authModalStore.redirect
    authModalStore.hide()
    resetForm()
    if (redirect) {
      authModalStore.clearRedirect()
      router.push(redirect)
    }
  }
})

const usernameInput = ref<HTMLInputElement | null>(null)

// 打开弹窗时自动聚焦用户名输入框，减少操作步骤
watch(() => authModalStore.visible, (v) => {
  if (v) {
    nextTick(() => usernameInput.value?.focus())
  }
})

const close = () => {
  // 关闭即清理：倒计时 timer 与表单残留输入，下次打开是干净表单
  resetForm()
  emit('update:visible', false)
  emit('close')
}

onUnmounted(cleanupTimers)
</script>

<style scoped lang="scss">
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--overlay-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--bg-soft);
  border-radius: 12px;
  box-shadow: var(--shadow-modal);
  max-width: 420px;
  width: 90%;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 24px 12px;
}

.modal-title-wrap h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-title);
}

.modal-tip {
  margin: 6px 0 0;
  font-size: 0.8rem;
  color: var(--text-subtle);
  line-height: 1.5;
}

.close-btn {
  background: none;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: var(--text-subtle);
  border-radius: 4px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.close-btn:hover {
  background: var(--bg-card);
  color: var(--text-subtle);
}

.modal-body {
  padding: 0 24px 20px;
}

/* ===== 登录/注册切换 ===== */
.mode-tabs {
  display: flex;
  position: relative;
  margin-bottom: 16px;
  background: var(--bg-card);
  border-radius: 30px;
  padding: 4px;
}

.mode-tab {
  flex: 1;
  padding: 8px 16px;
  border: none;
  background: none;
  font-size: 0.9rem;
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
  background: var(--bg-soft);
  border-radius: 26px;
  box-shadow: var(--shadow-sm);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  &.tab-indicator-right { transform: translateX(100%); }
}

/* ===== 登录模式切换 ===== */
.login-mode-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.mode-tab-sm {
  flex: 1;
  padding: 7px 12px;
  border: 1.5px solid var(--border-base);
  border-radius: 30px;
  background: var(--bg-card);
  color: var(--text-subtle);
  font-size: 0.8rem;
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

/* ===== 表单 ===== */
.auth-form { margin-bottom: 12px; }

.form-group { margin-bottom: 14px; }

.form-group label {
  display: block;
  margin-bottom: 6px;
  color: var(--text-main);
  font-weight: 500;
  font-size: 0.85rem;
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
  padding: 11px 14px 11px 42px;
  border: 1.5px solid var(--border-base);
  border-radius: 8px;
  font-size: 0.9rem;
  background: var(--bg-card);
  color: var(--text-main);
  transition: all 0.25s ease;
  box-sizing: border-box;
  &::placeholder { color: var(--text-muted); }
  &:hover { border-color: var(--border-strong); }
  &:focus { outline: none; border-color: var(--color-primary); box-shadow: 0 0 0 4px rgba(var(--color-primary-rgb), 0.1); }
  &.error { border-color: var(--color-error); box-shadow: 0 0 0 4px rgba(234, 67, 53, 0.1); }
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
  font-size: 0.78rem;
  margin-top: 6px;
  padding-left: 4px;
}

/* 忘记密码链接 */
.forgot-password {
  text-align: right;
  margin-top: -8px;
  margin-bottom: 12px;
}

.link-btn-sm {
  color: var(--color-primary);
  font-size: 0.8rem;
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
  font-size: 0.78rem;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 8px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s ease;
  &:hover:not(:disabled) { background: var(--color-primary-dark); }
  &:disabled { background: var(--border-base); color: var(--text-muted); cursor: not-allowed; }
}

/* 提交按钮 */
.submit-btn {
  position: relative;
  width: 100%;
  min-height: 44px;
  padding: 10px 14px;
  margin-top: 6px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
  border: none;
  border-radius: 30px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: box-shadow 0.3s ease, filter 0.2s ease, opacity 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  &:hover:not(:disabled) { box-shadow: 0 6px 20px rgba(var(--color-primary-rgb), 0.4); filter: brightness(1.05); }
  &:active:not(:disabled) { filter: brightness(0.97); }
  &:disabled { opacity: 0.75; cursor: not-allowed; }
}

.btn-icon,
.spinner-abs {
  position: absolute;
  left: 24px;
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
}

.spinner-sm {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top: 2px solid currentColor;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  flex-shrink: 0;
}

.register-tip {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin: -4px 0 8px;
  padding-left: 4px;
  line-height: 1.5;
}

.card-footer {
  text-align: center;
  padding-top: 14px;
  border-top: 1px solid var(--border-light);
  p { color: var(--text-subtle); font-size: 0.85rem; margin: 0; }
}

.link-btn {
  color: var(--color-primary);
  background: none;
  border: none;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.85rem;
  margin-left: 4px;
  transition: color 0.2s;
  &:hover { color: var(--color-primary-dark); text-decoration: underline; }
}
</style>
