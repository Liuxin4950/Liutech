/**
 * 登录/注册表单逻辑（单点定义，页面与弹窗共用）
 *
 * 用途：Login.vue（独立页面）与 LoginModal.vue（登录弹窗）共用同一份表单状态与提交逻辑，
 *       避免两份实现逻辑漂移。成功行为（页面跳转 / 弹窗关闭）由调用方通过 onLoginSuccess 注入。
 *
 * 异常处理约定：
 * - 前端校验失败：字段级 errors 红字提示（validateForm）
 * - 后端业务失败（密码错误/验证码错误等）：api.ts 拦截器 toast 提示，composable 不重复弹窗
 * - 网络错误/超时：handleFormSubmit → handleApiError 兜底提示
 * - 验证码倒计时：cleanupTimers 在组件卸载 / 弹窗关闭时清理，防泄漏
 */
import { ref, reactive } from 'vue'
import { useUserStore } from '../stores/user'
import { useErrorHandler } from './useErrorHandler'
import type { RegisterRequest } from '../services/user'
import { sendEmailLoginCode } from '../services/user'

export function useAuthForm(options?: {
  /** 登录成功后的动作（页面版跳转，弹窗版关闭），由调用方注入 */
  onLoginSuccess?: () => void
}) {
  const userStore = useUserStore()
  const { handleFormSubmit, showSuccess, clearError, showSuccessToast } = useErrorHandler()

  const isLogin = ref(true)
  const loginMode = ref<'password' | 'email'>('password') // 密码登录或邮箱验证码登录

  const loginForm = reactive({ username: '', password: '' })
  const emailLoginForm = reactive({ email: '', code: '' })
  const emailLoginErrors = reactive({ email: '', code: '' })
  const emailCountdown = ref(0)
  let emailTimer: ReturnType<typeof setInterval> | null = null
  const registerForm = reactive({ email: '', code: '', password: '' })
  const isSending = ref(false)

  const errors = reactive({ username: '', email: '', password: '', code: '', confirmPassword: '' })

  const showPassword = reactive({ login: false })

  const toggleMode = () => { isLogin.value = !isLogin.value; clearErrors(); Object.assign(emailLoginErrors, { email: '', code: '' }); clearError(); loginMode.value = 'password' }

  const clearForms = () => {
    Object.assign(loginForm, { username: '', password: '' })
    Object.assign(registerForm, { email: '', code: '', password: '' })
  }

  const clearErrors = () => {
    Object.assign(errors, { username: '', email: '', password: '', code: '', confirmPassword: '' })
  }

  const validateForm = () => {
    clearErrors()
    let isValid = true
    if (isLogin.value) {
      if (!loginForm.username.trim()) { errors.username = '请输入用户名或邮箱'; isValid = false }
      if (!loginForm.password) { errors.password = '请输入密码'; isValid = false }
    } else {
      if (!registerForm.email.trim()) { errors.email = '请输入邮箱地址'; isValid = false }
      else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) { errors.email = '请输入有效的邮箱地址'; isValid = false }
      if (!registerForm.code.trim()) { errors.code = '请输入验证码'; isValid = false }
      if (!registerForm.password) { errors.password = '请输入密码'; isValid = false }
      else if (registerForm.password.length < 6) { errors.password = '密码至少6位'; isValid = false }
    }
    return isValid
  }

  /** 生成随机用户名：user_ + 6位小写字母数字，满足后端 3-20 位约束 */
  const generateRandomUsername = () => {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
    let suffix = ''
    for (let i = 0; i < 6; i++) {
      suffix += chars.charAt(Math.floor(Math.random() * chars.length))
    }
    return `user_${suffix}`
  }

  const handleLogin = async () => {
    if (!validateForm()) return
    const result = await handleFormSubmit(async () => await userStore.login(loginForm.username, loginForm.password))
    if (result) { showSuccessToast('登录成功！'); options?.onLoginSuccess?.() }
  }

  const handleRegister = async () => {
    if (!validateForm()) return
    const result = await handleFormSubmit(async () => {
      const data: RegisterRequest = {
        username: generateRandomUsername(),
        email: registerForm.email,
        code: registerForm.code,
        password: registerForm.password
      }
      return await userStore.register(data)
    })
    if (result) {
      showSuccess('注册成功！请使用邮箱登录')
      isLogin.value = true
      loginMode.value = 'email'
      clearForms()
    }
  }

  const handleSubmit = () => { isLogin.value ? handleLogin() : handleRegister() }

  // 注册验证码相关
  const registerCountdown = ref(0)
  let registerTimer: ReturnType<typeof setInterval> | null = null

  const handleSendRegisterCode = async () => {
    errors.email = ''
    if (!registerForm.email.trim()) { errors.email = '请输入邮箱地址'; return }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email)) { errors.email = '请输入有效的邮箱地址'; return }
    isSending.value = true
    try {
      const { sendRegisterCode } = await import('../services/user')
      const result = await handleFormSubmit(async () => await sendRegisterCode(registerForm.email))
      if (result) { showSuccessToast('验证码已发送到您的邮箱'); startRegisterCountdown() }
    } finally {
      isSending.value = false
    }
  }

  const startRegisterCountdown = () => {
    if (registerTimer) { clearInterval(registerTimer); registerTimer = null }
    registerCountdown.value = 60
    registerTimer = setInterval(() => { registerCountdown.value--; if (registerCountdown.value <= 0 && registerTimer) { clearInterval(registerTimer); registerTimer = null } }, 1000)
  }

  // 邮箱登录相关方法
  const handleSendEmailCode = async () => {
    emailLoginErrors.email = ''
    if (!emailLoginForm.email.trim()) { emailLoginErrors.email = '请输入邮箱地址'; return }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLoginForm.email)) { emailLoginErrors.email = '请输入有效的邮箱地址'; return }
    isSending.value = true
    try {
      const result = await handleFormSubmit(async () => await sendEmailLoginCode(emailLoginForm.email))
      if (result) { showSuccessToast('验证码已发送到您的邮箱'); startEmailCountdown() }
    } finally {
      isSending.value = false
    }
  }

  const handleEmailLogin = async () => {
    emailLoginErrors.email = ''
    emailLoginErrors.code = ''
    if (!emailLoginForm.email.trim()) { emailLoginErrors.email = '请输入邮箱地址'; return }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLoginForm.email)) { emailLoginErrors.email = '请输入有效的邮箱地址'; return }
    if (!emailLoginForm.code.trim()) { emailLoginErrors.code = '请输入验证码'; return }
    const result = await handleFormSubmit(async () => await userStore.emailLogin(emailLoginForm.email, emailLoginForm.code))
    if (result) { showSuccessToast('登录成功！'); options?.onLoginSuccess?.() }
  }

  const startEmailCountdown = () => {
    if (emailTimer) { clearInterval(emailTimer); emailTimer = null }
    emailCountdown.value = 60
    emailTimer = setInterval(() => { emailCountdown.value--; if (emailCountdown.value <= 0 && emailTimer) { clearInterval(emailTimer); emailTimer = null } }, 1000)
  }

  /** 清理验证码倒计时（组件卸载 / 弹窗关闭时调用，防 timer 泄漏） */
  const cleanupTimers = () => {
    if (registerTimer) { clearInterval(registerTimer); registerTimer = null }
    if (emailTimer) { clearInterval(emailTimer); emailTimer = null }
  }

  /** 重置表单到初始态（弹窗关闭后清除残留输入，下次打开是干净表单） */
  const resetForm = () => {
    cleanupTimers()
    isLogin.value = true
    loginMode.value = 'password'
    Object.assign(loginForm, { username: '', password: '' })
    Object.assign(emailLoginForm, { email: '', code: '' })
    Object.assign(registerForm, { email: '', code: '', password: '' })
    Object.assign(emailLoginErrors, { email: '', code: '' })
    clearErrors()
    registerCountdown.value = 0
    emailCountdown.value = 0
    showPassword.login = false
  }

  return {
    // 状态
    isLogin,
    loginMode,
    loginForm,
    emailLoginForm,
    emailLoginErrors,
    emailCountdown,
    registerForm,
    isSending,
    errors,
    showPassword,
    registerCountdown,
    userStore,
    // 方法
    toggleMode,
    handleSubmit,
    handleLogin,
    handleRegister,
    handleEmailLogin,
    handleSendRegisterCode,
    handleSendEmailCode,
    cleanupTimers,
    resetForm
  }
}
