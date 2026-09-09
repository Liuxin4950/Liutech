/**
 * 用户状态管理
 * 使用 Pinia 管理用户登录状态和用户信息
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { UserService, type User, type RegisterRequest } from '../services/user'
import { getToken, removeToken, setToken } from '../utils/auth'

export const useUserStore = defineStore('user', () => {
  // 状态
  const userInfo = ref<User | null>(null)
  const isLoading = ref(false)

  // 计算属性
  const isLoggedIn = computed(() => {
    return !!userInfo.value && !!getToken()
  })

  const username = computed(() => {
    return userInfo.value?.username || ''
  })

  const avatar = computed(() => {
    return userInfo.value?.avatar || ''
  })

  // 动作
  /**
   * 登录
   * @param username 用户名
   * @param password 密码
   */
  const login = async (username: string, password: string) => {
    isLoading.value = true
    try {
      // 拦截器保证 code === 200 才会返回；非 200 已在拦截器抛错并提示后端 message
      const response = await UserService.login({ username, password })
      if (response.data.token) {
        setToken(response.data.token)
      }
      await fetchUserInfo()
      return true
    } catch (error: any) {
      if (!error?.isBusiness) message.error('登录失败')
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 注册
   * @param registerData 注册数据
   */
  const register = async (registerData: RegisterRequest) => {
    isLoading.value = true
    try {
      // 拦截器保证 code === 200 才会返回，失败已在拦截器抛错并提示
      await UserService.register(registerData)
      return true
    } catch (error: any) {
      if (!error?.isBusiness) message.error('注册失败')
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 登出
   */
  const logout = () => {
    removeToken()
    userInfo.value = null
  }

  /**
   * 获取用户信息
   */
  const fetchUserInfo = async () => {
    if (!getToken()) {
      userInfo.value = null
      return
    }

    try {
      // 拦截器保证 code === 200 才会返回，失败已在拦截器抛错并提示
      const response = await UserService.getCurrentUser()
      userInfo.value = response.data
    } catch (error: any) {
      console.error('获取用户信息失败:', error)
      // 401 已由拦截器清凭证并跳登录页，这里只兜底非业务错误，避免重复提示
      if (!error?.isBusiness) message.error('获取用户信息失败，请重新登录')
      // 获取用户信息失败通常是 token 失效，清除登录状态
      logout()
    }
  }

  /**
   * 初始化用户状态
   * 应用启动时调用，检查本地存储的token并获取用户信息
   */
  const initUserState = async () => {
    if (getToken()) {
      await fetchUserInfo()
    }
  }

  /**
   * 更新用户信息
   * @param newUserInfo 新的用户信息
   */
  const updateUserInfo = (newUserInfo: Partial<User>) => {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, ...newUserInfo }
    }
  }

  return {
    // 状态
    userInfo,
    isLoading,
    
    // 计算属性
    isLoggedIn,
    username,
    avatar,
    
    // 动作
    login,
    register,
    logout,
    fetchUserInfo,
    initUserState,
    updateUserInfo
  }
}, {
  persist: {
    key: 'user-store',
    storage: localStorage,
    pick: ['userInfo']
  }
})