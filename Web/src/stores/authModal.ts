/**
 * 全局登录弹窗 store
 * 用途：未登录访问需登录页面 / 触发需登录操作时，统一弹出登录提示。
 *       路由守卫与页面操作共用同一实例，避免各处维护局部弹窗状态。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

const DEFAULT_MESSAGE = '此功能需要登录后才能使用，请先登录您的账户。'

export const useAuthModalStore = defineStore('auth-modal', () => {
  const visible = ref(false)
  const message = ref(DEFAULT_MESSAGE)
  /** 登录成功后跳转的目标路径（路由守卫拦截时记录），非守卫触发的弹窗为空 */
  const redirect = ref('')

  /** 弹出登录提示；redirect 用于登录成功后自动跳回被拦截的目标页 */
  const show = (msg?: string, to?: string) => {
    message.value = msg || DEFAULT_MESSAGE
    redirect.value = to || ''
    visible.value = true
  }

  /** 关闭弹窗 */
  const hide = () => {
    visible.value = false
  }

  /** 清除登录后跳转目标（跳转完成后调用，防重复跳转） */
  const clearRedirect = () => {
    redirect.value = ''
  }

  return { visible, message, redirect, show, hide, clearRedirect }
})
