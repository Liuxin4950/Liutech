/**
 * 用户状态管理
 * 使用 Pinia 管理用户登录状态和用户信息
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { UserService, type UserInfo, type RegisterRequest } from '../services/user.ts'
import { handleApiError, showError } from '../utils/errorHandler'

export const useUserStore = defineStore('user', () => {
  // 状态
  const userInfo = ref<UserInfo | null>(null)
  const isLoading = ref(false)

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
      handleApiError(error)
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
      const userData = await UserService.register(registerData)
      userInfo.value = userData
      return true
    } catch (error) {
      handleApiError(error)
      return false
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
   * 获取用户信息
   */
  const fetchUserInfo = async () => {
    if (!UserService.isLoggedIn()) {
      userInfo.value = null
      return
    }

    try {
      const userData = await UserService.getCurrentUser()
      userInfo.value = userData
    } catch (error) {
      console.error('获取用户信息失败:', error)
      showError('获取用户信息失败，请重新登录')
      // 如果获取用户信息失败，可能是token过期，清除登录状态
      logout()
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
    
    // 动作
    login,
    register,
    logout,
    fetchUserInfo,
    initUserState,
    updateUserInfo
  }
})