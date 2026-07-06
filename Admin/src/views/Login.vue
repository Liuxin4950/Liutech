<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { message } from 'ant-design-vue'
import {
  UserOutlined,
  LockOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  SafetyCertificateOutlined,
  LoginOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const loginForm = reactive({ username: '', password: '' })
const errors = reactive({ username: '', password: '' })
const showPassword = ref(false)
const rememberMe = ref(false)

// 键入状态：给左侧插图做互动（暂用作装饰状态，无 3D 角色时给光晕加动）
const isTyping = computed(() => loginForm.username.length > 0 || loginForm.password.length > 0)

function validate(): boolean {
  errors.username = ''
  errors.password = ''
  if (!loginForm.username.trim()) errors.username = '请输入用户名'
  if (!loginForm.password) errors.password = '请输入密码'
  return !errors.username && !errors.password
}

async function handleLogin() {
  if (!validate()) return
  const ok = await userStore.login(loginForm.username, loginForm.password)
  if (ok) {
    message.success('登录成功')
    const redirect = router.currentRoute.value.query.redirect as string || '/'
    router.push(redirect)
  }
}

const currentYear = new Date().getFullYear()
</script>

<template>
  <div class="lt-login">
    <!-- 左侧品牌区：桌面端可见，移动端隐藏 -->
    <aside class="lt-login__brand" :class="{ 'lt-login__brand--typing': isTyping }">
      <div class="lt-login__brand-header">
        <div class="lt-login__logo">
          <SafetyCertificateOutlined />
        </div>
        <span class="lt-login__logo-text">LiuTech 管理后台</span>
      </div>

      <div class="lt-login__brand-hero">
        <h1 class="lt-login__brand-title">欢迎回来</h1>
        <p class="lt-login__brand-subtitle">
          全栈博客平台管理中心<br />
          文章、用户、AI 模型、系统配置，一站掌控
        </p>

        <ul class="lt-login__features">
          <li>· 端到端的内容管理与审核工作流</li>
          <li>· AI 助手辅助创作与数据洞察</li>
          <li>· 键盘友好、暗色主题、i18n 全支持</li>
        </ul>
      </div>

      <div class="lt-login__brand-footer">
        <span>© {{ currentYear }} LiuTech</span>
        <span class="lt-login__footer-sep">·</span>
        <a href="https://liuxin.chat" target="_blank" rel="noopener">liuxin.chat</a>
      </div>

      <!-- 装饰光晕 -->
      <div class="lt-login__glow lt-login__glow--1" />
      <div class="lt-login__glow lt-login__glow--2" />
      <div class="lt-login__grid" />
    </aside>

    <!-- 右侧登录表单 -->
    <main class="lt-login__panel">
      <div class="lt-login__form-wrap">
        <!-- 移动端 Logo -->
        <div class="lt-login__mobile-logo">
          <SafetyCertificateOutlined />
          <span>LiuTech 管理后台</span>
        </div>

        <header class="lt-login__form-header">
          <h2 class="lt-login__form-title">管理员登录</h2>
          <p class="lt-login__form-subtitle">请输入你的账号信息</p>
        </header>

        <form class="lt-login__form" @submit.prevent="handleLogin">
          <div class="lt-login__field">
            <label for="lt-login-username" class="lt-login__label">用户名</label>
            <a-input
              id="lt-login-username"
              v-model:value="loginForm.username"
              placeholder="请输入用户名"
              autocomplete="username"
              size="large"
              class="lt-login__input"
              :status="errors.username ? 'error' : ''"
              @press-enter="handleLogin"
            >
              <template #prefix><UserOutlined class="lt-login__input-icon" /></template>
            </a-input>
            <span v-if="errors.username" class="lt-login__error">{{ errors.username }}</span>
          </div>

          <div class="lt-login__field">
            <label for="lt-login-password" class="lt-login__label">密码</label>
            <a-input
              id="lt-login-password"
              v-model:value="loginForm.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
              size="large"
              class="lt-login__input"
              :status="errors.password ? 'error' : ''"
              @press-enter="handleLogin"
            >
              <template #prefix><LockOutlined class="lt-login__input-icon" /></template>
              <template #suffix>
                <button
                  type="button"
                  class="lt-login__eye"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <EyeInvisibleOutlined v-if="showPassword" />
                  <EyeOutlined v-else />
                </button>
              </template>
            </a-input>
            <span v-if="errors.password" class="lt-login__error">{{ errors.password }}</span>
          </div>

          <div class="lt-login__row">
            <a-checkbox v-model:checked="rememberMe">记住我</a-checkbox>
            <a class="lt-login__forgot" @click.prevent>忘记密码？</a>
          </div>

          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="userStore.isLoading"
            class="lt-login__submit"
          >
            <LoginOutlined v-if="!userStore.isLoading" />
            {{ userStore.isLoading ? '登录中...' : '登录' }}
          </a-button>
        </form>

        <div class="lt-login__hint">
          仅限管理员访问 · 请妥善保管账号
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.lt-login {
  height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: var(--lt-color-bg-container);
  overflow: hidden;
}

/* ===== 左侧品牌区 ===== */
.lt-login__brand {
  position: relative;
  padding: var(--lt-space-3xl);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: #fff;
  background:
    linear-gradient(135deg, var(--lt-color-brand-7) 0%, var(--lt-color-brand-9) 60%, #0a1a4a 100%);
  overflow: hidden;
}

.lt-login__brand-header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
}

.lt-login__logo {
  width: 40px;
  height: 40px;
  border-radius: var(--lt-radius-lg);
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--lt-font-size-xl);
  color: #fff;
}

.lt-login__logo-text {
  font-size: var(--lt-font-size-lg);
  font-weight: var(--lt-font-weight-semibold);
  letter-spacing: 0.02em;
}

.lt-login__brand-hero {
  position: relative;
  z-index: 2;
  max-width: 480px;
}

.lt-login__brand-title {
  font-size: 44px;
  font-weight: var(--lt-font-weight-bold);
  line-height: 1.15;
  letter-spacing: -0.02em;
  margin: 0 0 var(--lt-space-lg);
}

.lt-login__brand-subtitle {
  font-size: var(--lt-font-size-md);
  line-height: var(--lt-line-height-relaxed);
  color: rgba(255, 255, 255, 0.75);
  margin: 0 0 var(--lt-space-2xl);
}

.lt-login__features {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-sm);
  font-size: var(--lt-font-size-sm);
  color: rgba(255, 255, 255, 0.65);
  line-height: var(--lt-line-height-relaxed);
}

.lt-login__brand-footer {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
  font-size: var(--lt-font-size-xs);
  color: rgba(255, 255, 255, 0.55);
}

.lt-login__footer-sep {
  color: rgba(255, 255, 255, 0.3);
}

.lt-login__brand-footer a {
  color: rgba(255, 255, 255, 0.75);
  transition: color var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-login__brand-footer a:hover {
  color: #fff;
}

/* 装饰：光晕 + 栅格 */
.lt-login__glow {
  position: absolute;
  border-radius: var(--lt-radius-circle);
  filter: blur(80px);
  pointer-events: none;
  transition: transform 1s var(--lt-ease-in-out), opacity 0.6s ease;
}
.lt-login__glow--1 {
  top: -80px;
  right: -80px;
  width: 320px;
  height: 320px;
  background: rgba(96, 165, 250, 0.35);
}
.lt-login__glow--2 {
  bottom: -100px;
  left: -60px;
  width: 400px;
  height: 400px;
  background: rgba(139, 92, 246, 0.25);
}
.lt-login__brand--typing .lt-login__glow--1 {
  transform: translate(20px, 20px) scale(1.1);
}
.lt-login__brand--typing .lt-login__glow--2 {
  transform: translate(-20px, -30px) scale(1.15);
}

.lt-login__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 24px 24px;
  pointer-events: none;
}

/* ===== 右侧登录表单 ===== */
.lt-login__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--lt-space-2xl);
  background: var(--lt-color-bg-container);
}

.lt-login__form-wrap {
  width: 100%;
  max-width: 400px;
}

.lt-login__mobile-logo {
  display: none;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-sm);
  margin-bottom: var(--lt-space-2xl);
  font-size: var(--lt-font-size-lg);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-primary);
}

.lt-login__form-header {
  margin-bottom: var(--lt-space-2xl);
}

.lt-login__form-title {
  margin: 0 0 var(--lt-space-xs);
  font-size: var(--lt-font-size-2xl);
  font-weight: var(--lt-font-weight-bold);
  color: var(--lt-color-text);
  letter-spacing: -0.01em;
}

.lt-login__form-subtitle {
  margin: 0;
  font-size: var(--lt-font-size-sm);
  color: var(--lt-color-text-tertiary);
}

.lt-login__form {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-lg);
}

.lt-login__field {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-xs);
}

.lt-login__label {
  font-size: var(--lt-font-size-sm);
  font-weight: var(--lt-font-weight-medium);
  color: var(--lt-color-text-secondary);
}

.lt-login__input-icon {
  color: var(--lt-color-text-tertiary);
}

.lt-login__error {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-error);
}

.lt-login__eye {
  border: none;
  background: transparent;
  color: var(--lt-color-text-tertiary);
  cursor: pointer;
  padding: 0;
  display: inline-flex;
  align-items: center;
  transition: color var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-login__eye:hover {
  color: var(--lt-color-primary);
}

.lt-login__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: calc(var(--lt-space-xs) * -1);
}

.lt-login__forgot {
  color: var(--lt-color-primary);
  font-size: var(--lt-font-size-sm);
  cursor: pointer;
}

.lt-login__submit {
  margin-top: var(--lt-space-sm);
  height: 44px;
  font-weight: var(--lt-font-weight-medium);
  letter-spacing: 0.02em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-sm);
}

.lt-login__hint {
  margin-top: var(--lt-space-xl);
  text-align: center;
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
}

/* ===== 响应式：<960px 隐藏左侧品牌区 ===== */
@media (max-width: 960px) {
  .lt-login {
    grid-template-columns: 1fr;
  }
  .lt-login__brand {
    display: none;
  }
  .lt-login__mobile-logo {
    display: inline-flex;
  }
}
</style>
