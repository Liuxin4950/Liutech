<script setup lang="ts">
import { onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthForm } from '../composables/useAuthForm'
import Icon from '../components/Icon.vue'

const router = useRouter()

// 表单逻辑与登录弹窗共用 useAuthForm；页面版登录成功后跳转 redirect 或首页
const {
  isLogin, loginMode, loginForm, emailLoginForm, emailLoginErrors, emailCountdown,
  registerForm, isSending, errors, showPassword, registerCountdown, userStore,
  toggleMode, handleSubmit, handleSendRegisterCode, handleSendEmailCode,
  handleEmailLogin, cleanupTimers
} = useAuthForm({
  onLoginSuccess: () => setTimeout(() => router.push((router.currentRoute.value.query.redirect as string) || '/'), 600)
})

// M13: 页面卸载时清理验证码倒计时 timer
onUnmounted(cleanupTimers)
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
          <!-- 标题（页面标题组合设计：蓝色药丸 + 深色标题 + 橙色高亮） -->
          <div class="card-header">
            <div class="page-title">
              <span class="title-badge"><Icon :name="isLogin ? 'lock' : 'person_add'" size="12" /> {{ isLogin ? 'Welcome' : 'Sign Up' }}</span>
              <h1 class="title-heading">
                <template v-if="isLogin">欢迎<span class="title-highlight">回来</span></template>
                <template v-else>创建<span class="title-highlight">账户</span></template>
              </h1>
              <p class="title-desc">{{ isLogin ? '请登录您的账户' : '开始您的技术之旅' }}</p>
            </div>
          </div>

          <!-- 切换标签 -->
          <div class="mode-tabs">
            <button class="mode-tab" :class="{ active: isLogin }" @click="isLogin || toggleMode()">登录</button>
            <button class="mode-tab" :class="{ active: !isLogin }" @click="!isLogin || toggleMode()">注册</button>
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
            <div class="form-block-wrapper">
            <transition name="slide-fade">
              <!-- 登录块 -->
              <div v-if="isLogin" key="login" class="form-block">
                <div class="form-group">
                  <label>用户名 / 邮箱</label>
                  <div class="input-wrapper">
                    <Icon name="user" size="18" class="input-icon" />
                    <input v-model="loginForm.username" type="text" placeholder="请输入用户名或邮箱" :class="{ error: errors.username }" />
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
                  <router-link to="/forgot-password" class="link-btn-sm">忘记密码？</router-link>
                </div>
              </div>

              <!-- 注册块 -->
              <div v-else key="register" class="form-block">
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
              </div>
            </transition>
            </div>

            <button type="submit" class="submit-btn" :disabled="userStore.isLoading">
              <span v-if="userStore.isLoading" class="spinner-sm spinner-abs"></span>
              <Icon v-else :name="isLogin ? 'login' : 'person_add'" size="18" class="btn-icon" />
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

          <div class="card-footer">
            <p v-if="isLogin">还没有账户？<button type="button" @click="toggleMode()" class="link-btn">立即注册</button></p>
            <p v-else>已有账户？<button type="button" @click="toggleMode()" class="link-btn">立即登录</button></p>
          </div>
        </div>
      </div>

      <!-- 右侧品牌区 -->
      <div class="card-right">
        <div class="brand-decor decor-1"></div>
        <div class="brand-decor decor-2"></div>
        <div class="brand-content">
          <router-link to="/" class="brand-link">
          <div class="brand-logo">
            <Icon name="code" size="30" class="logo-icon" />
          </div>
          <div class="brand-title">LiuTech</div>
          <p class="brand-subtitle">TECH · BLOG</p>
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
  border-radius: 12px;
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

  .page-title {
    gap: 10px;
    margin-bottom: 0;
  }
}

// 切换标签
.mode-tabs {
  display: flex;
  position: relative;
  margin-bottom: 24px;
  background: var(--bg-soft);
  border-radius: 30px;
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
  border-radius: 26px;
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
  border-radius: 30px;
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
  border-radius: 8px;
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
  border-radius: 8px;
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
  position: relative;
  width: 100%;
  min-height: 48px;
  padding: 12px 14px;
  margin-top: 8px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
  border: none;
  border-radius: 30px;
  font-size: 1rem;
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
  background:
    radial-gradient(circle at 15% 15%, rgba(255, 255, 255, 0.16) 0%, transparent 45%),
    radial-gradient(circle at 85% 85%, rgba(255, 255, 255, 0.12) 0%, transparent 45%),
    linear-gradient(160deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 40px;
  position: relative;
  overflow: hidden;
  order: 2;
}

.brand-decor {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.14);

  &.decor-1 {
    width: 260px;
    height: 260px;
    top: -90px;
    right: -90px;
  }

  &.decor-2 {
    width: 180px;
    height: 180px;
    bottom: -50px;
    left: -50px;
  }
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
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.logo-icon { color: rgba(255, 255, 255, 0.92); }

.brand-title {
  font-size: 2rem;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
  background: linear-gradient(135deg, #ffffff 0%, rgba(255, 255, 255, 0.72) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-subtitle {
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.35em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.72);
  margin: 0 0 24px 0;
}

.brand-desc {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1.8;
  margin: 0;
}

// 过渡动画：新旧块同时过渡，旧块绝对定位在下层快速淡出，新块在上层淡入，不覆盖
.form-block-wrapper {
  position: relative;
}

.form-block {
  position: relative;
  z-index: 1;
}

.slide-fade-enter-active { transition: opacity 0.25s ease-out; }
.slide-fade-leave-active {
  transition: opacity 0.15s ease-in;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 0;
}
.slide-fade-enter-from { opacity: 0; }
.slide-fade-leave-to { opacity: 0; }

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

  .brand-logo { width: 56px; height: 56px; margin-bottom: 12px; border-radius: 50%; :deep(svg) { width: 24px; height: 24px; } }
  .brand-title { font-size: 1.4rem; }
  .brand-subtitle { font-size: 0.85rem; margin-bottom: 12px; }
  .brand-desc { font-size: 0.8rem; }

  .form-wrapper { padding: 24px; }
}

// 手机端：直接隐藏右侧品牌区，只保留表单主体，避免纵向溢出
@include respond(md) {
  .card-right { display: none; }
  .card-left { order: 1; }
  .card-container { border-radius: 12px; }

  // 返回首页悬浮于页面顶部，宽度与卡片一致并水平居中，左缘与卡片左缘对齐
  .back-home {
    position: absolute;
    top: 12px;
    left: 0;
    right: 0;
    width: min(420px, calc(100vw - 40px));
    margin: 0 auto;
  }
}

// 小屏手机：进一步压缩表单间距
@include respond(sm) {
  .card-container { max-width: calc(100vw - 20px); }
  .form-wrapper { padding: 16px; }
  .card-header { margin-bottom: 16px; }
  .mode-tabs { margin-bottom: 16px; }
  .login-mode-tabs { margin-bottom: 12px; }
  .form-group { margin-bottom: 12px; }
  .forgot-password { margin-bottom: 12px; }
  .card-footer { padding-top: 12px; }
  .submit-btn { min-height: 44px; }
  .back-home { width: min(420px, calc(100vw - 20px)); }
}
</style>

