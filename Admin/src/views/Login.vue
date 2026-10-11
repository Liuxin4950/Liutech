<script setup lang="ts">
import { reactive, ref } from 'vue'
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

const REMEMBERED_USERNAME_KEY = 'lt-admin-remembered-username'
let rememberedUsername = ''
try { rememberedUsername = localStorage.getItem(REMEMBERED_USERNAME_KEY) || '' } catch { /* 存储不可用仍可登录 */ }
const loginForm = reactive({ username: rememberedUsername, password: '' })
const errors = reactive({ username: '', password: '' })
const showPassword = ref(false)
const rememberMe = ref(!!rememberedUsername)

function validate(): boolean {
  errors.username = ''
  errors.password = ''
  if (!loginForm.username.trim()) errors.username = '请输入用户名'
  if (!loginForm.password) errors.password = '请输入密码'
  return !errors.username && !errors.password
}

async function handleLogin() {
  if (userStore.isLoading || !validate()) return
  const ok = await userStore.login(loginForm.username.trim(), loginForm.password)
  if (ok) {
    try {
      if (rememberMe.value) localStorage.setItem(REMEMBERED_USERNAME_KEY, loginForm.username.trim())
      else localStorage.removeItem(REMEMBERED_USERNAME_KEY)
    } catch { /* 用户名记忆失败不影响登录 */ }
    message.success('登录成功')
    const requested = router.currentRoute.value.query.redirect
    const redirect = typeof requested === 'string' && requested.startsWith('/') && !requested.startsWith('//') && !requested.includes('\\') ? requested : '/'
    router.push(redirect)
  }
}

const currentYear = new Date().getFullYear()
</script>

<template>
  <div class="lt-login">
    <!-- 左侧品牌区：桌面端可见，移动端隐藏 -->
    <aside class="lt-login__brand">
      <div class="lt-login__brand-mark">
        <span class="lt-login__logo"><SafetyCertificateOutlined /></span>
        <span class="lt-login__logo-text">LiuTech 管理后台</span>
      </div>

      <div class="lt-login__brand-hero">
        <p class="lt-login__eyebrow">博客平台管理中心</p>
        <h1 class="lt-login__brand-title">欢迎回来</h1>
        <p class="lt-login__brand-desc">文章、用户、评论与 AI 能力，在这里统一管理与运营。</p>
        <ul class="lt-login__points">
          <li>端到端的内容管理与审核工作流</li>
          <li>AI 助手辅助创作与数据洞察</li>
          <li>键盘快捷操作与暗色主题</li>
        </ul>
      </div>

      <div class="lt-login__brand-foot">
        <span>© {{ currentYear }} LiuTech</span>
        <span class="lt-login__dot">·</span>
        <a href="https://liuxin.chat" target="_blank" rel="noopener">liuxin.chat</a>
      </div>
    </aside>

    <!-- 右侧登录表单 -->
    <main class="lt-login__panel">
      <div class="lt-login__card">
        <!-- 移动端品牌标识 -->
        <div class="lt-login__mobile-mark">
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
              :status="errors.username ? 'error' : ''"
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
              :status="errors.password ? 'error' : ''"
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
            <a-checkbox v-model:checked="rememberMe">记住用户名</a-checkbox>
            <a class="lt-login__forgot" href="https://liuxin.chat" target="_blank" rel="noopener noreferrer" title="请在博客前台登录窗口选择忘记密码">忘记密码？</a>
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

        <p class="lt-login__hint">仅限管理员访问 · 请妥善保管账号</p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.lt-login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: var(--lt-color-bg-page);
}
@supports (min-height: 100dvh) {
  .lt-login { min-height: 100dvh; }
}

/* ===== 左侧品牌区：深蓝灰底，只保留一处克制的品牌光 ===== */
.lt-login__brand {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--lt-space-2xl);
  padding: clamp(var(--lt-space-xl), 4vw, var(--lt-space-3xl));
  color: #fff;
  background:
    radial-gradient(80% 60% at 82% 8%, rgba(64, 150, 255, 0.16), transparent 65%),
    linear-gradient(165deg, #1b2740 0%, #131d30 55%, #0d1524 100%);
}
/* 细网格纹理，向左上淡出 */
.lt-login__brand::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 32px 32px;
  -webkit-mask-image: radial-gradient(120% 100% at 22% 18%, #000 20%, transparent 72%);
  mask-image: radial-gradient(120% 100% at 22% 18%, #000 20%, transparent 72%);
}
.lt-login__brand-mark,
.lt-login__brand-hero,
.lt-login__brand-foot {
  position: relative;
}

.lt-login__brand-mark {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
}
.lt-login__logo {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--lt-radius-md);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.14);
  font-size: var(--lt-font-size-md);
  color: #fff;
}
.lt-login__logo-text {
  font-size: var(--lt-font-size-md);
  font-weight: var(--lt-font-weight-semibold);
  letter-spacing: 0.02em;
}

.lt-login__brand-hero {
  max-width: 460px;
}
.lt-login__eyebrow {
  margin: 0 0 var(--lt-space-md);
  font-size: var(--lt-font-size-xs);
  letter-spacing: 0.1em;
  color: rgba(255, 255, 255, 0.45);
}
.lt-login__brand-title {
  margin: 0 0 var(--lt-space-lg);
  font-size: clamp(var(--lt-font-size-3xl), 3vw, var(--lt-font-size-4xl));
  font-weight: var(--lt-font-weight-bold);
  line-height: var(--lt-line-height-tight);
  letter-spacing: -0.02em;
}
.lt-login__brand-desc {
  margin: 0 0 var(--lt-space-xl);
  max-width: 34em;
  font-size: var(--lt-font-size-base);
  line-height: var(--lt-line-height-relaxed);
  color: rgba(255, 255, 255, 0.66);
}
.lt-login__points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-sm);
  font-size: var(--lt-font-size-sm);
  color: rgba(255, 255, 255, 0.56);
}
.lt-login__points li {
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
}
.lt-login__points li::before {
  content: '';
  flex: none;
  width: 4px;
  height: 4px;
  border-radius: var(--lt-radius-circle);
  background: rgba(120, 170, 255, 0.65);
}

.lt-login__brand-foot {
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
  font-size: var(--lt-font-size-xs);
  color: rgba(255, 255, 255, 0.42);
}
.lt-login__dot {
  color: rgba(255, 255, 255, 0.28);
}
.lt-login__brand-foot a {
  color: rgba(255, 255, 255, 0.72);
  transition: color var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-login__brand-foot a:hover {
  color: #fff;
}

/* ===== 右侧登录表单 ===== */
.lt-login__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(var(--lt-space-lg), 3vw, var(--lt-space-2xl));
  background: var(--lt-color-bg-page);
}
.lt-login__card {
  width: 100%;
  max-width: 400px;
  padding: clamp(var(--lt-space-xl), 3vw, var(--lt-space-2xl));
  background: var(--lt-color-bg-container);
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-xl);
  box-shadow: var(--lt-shadow-sm);
}

.lt-login__mobile-mark {
  display: none;
  align-items: center;
  gap: var(--lt-space-sm);
  margin-bottom: var(--lt-space-xl);
  font-size: var(--lt-font-size-md);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-primary);
}

.lt-login__form-header {
  margin-bottom: var(--lt-space-xl);
}
.lt-login__form-title {
  margin: 0 0 var(--lt-space-xs);
  font-size: var(--lt-font-size-2xl);
  font-weight: var(--lt-font-weight-bold);
  color: var(--lt-color-text);
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
  color: var(--lt-color-error-text);
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
.lt-login__forgot:hover {
  color: var(--lt-color-primary-hover);
}

.lt-login__submit {
  margin-top: var(--lt-space-xs);
  height: 44px;
  font-weight: var(--lt-font-weight-medium);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-sm);
}

.lt-login__hint {
  margin: var(--lt-space-xl) 0 0;
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
  .lt-login__mobile-mark {
    display: inline-flex;
  }
}
@media (max-width: 480px) {
  .lt-login__card {
    padding: var(--lt-space-xl);
  }
}
</style>
