/**
 * 用户状态管理
 * 使用 Pinia 管理用户登录状态和用户信息
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { UserService, type UserInfo, type RegisterRequest } from '../services/user'
import { showErrorToast } from '../utils/errorHandler'

export const useUserStore = defineStore('user', () => {
  // 状态
  const userInfo = ref<UserInfo | null>(null)
  const isLoading = ref(false)
  const lastFetchTime = ref<number>(0)

  // 缓存时间（5分钟）
  const CACHE_DURATION = 5 * 60 * 1000

  // 计算属性
  const isLoggedIn = computed(() => {
    return !!userInfo.value && UserService.isLoggedIn()
  })

  const username = computed(() => {
    return userInfo.value?.username || ''
  })

  const avatar = computed(() => {
    return userInfo.value?.avatarUrl || ''
  })

  const points = computed(() => {
    return userInfo.value?.points || 0
  })

  const isAdmin = computed(() => {
    return userInfo.value?.role?.toLowerCase() === 'admin'
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
      await UserService.login({ username, password })
      await fetchUserInfo()
      return true
    } catch (error) {
      // 重新抛出，由调用方（如 handleFormSubmit）捕获并弹出 Toast
      throw error
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
      const userData = await UserService.register(registerData)
      userInfo.value = userData
      return true
    } catch (error) {
      // 重新抛出，由调用方（如 handleFormSubmit）捕获并弹出 Toast
      throw error
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 登出
   */
  const logout = () => {
    UserService.logout()
    userInfo.value = null
  }

  /**
   * 邮箱验证码登录
   */
  const emailLogin = async (email: string, code: string) => {
    isLoading.value = true
    try {
      await UserService.verifyEmailLogin({ email, code })
      await fetchUserInfo()
      return true
    } catch (error) {
      throw error
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取用户信息
   * @param forceRefresh 是否强制刷新（忽略缓存）
   */
  const fetchUserInfo = async (forceRefresh = false) => {
    if (!UserService.isLoggedIn()) {
      userInfo.value = null
      return
    }

    // 如果数据还在缓存期内且不强制刷新，跳过请求
    if (!forceRefresh && userInfo.value && Date.now() - lastFetchTime.value < CACHE_DURATION) {
      return
    }

    try {
      const userData = await UserService.getCurrentUser()
      userInfo.value = userData
      lastFetchTime.value = Date.now()
    } catch (error: any) {
      console.error('获取用户信息失败:', error)
      // 401错误静默处理（token无效或过期）
      if (error.response?.status === 401) {
        logout()
      } else {
        // 其他错误使用 Toast 提示（自动消失）
        showErrorToast('获取用户信息失败，请重新登录')
        logout()
      }
    }
  }

  /**
   * 初始化用户状态
   * 应用启动时调用，检查本地存储的token并获取用户信息
   */
  const initUserState = async () => {
    if (UserService.isLoggedIn()) {
      await fetchUserInfo()
    }
  }

  /**
   * 更新用户信息
   * @param newUserInfo 新的用户信息
   */
  const updateUserInfo = (newUserInfo: Partial<UserInfo>) => {
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
    points,
    isAdmin,
    
    // 动作
    login,
    register,
    logout,
    emailLogin,
    fetchUserInfo,
    initUserState,
    updateUserInfo
  }
}, {
  persist: {
    pick: ['userInfo']
  }
})
