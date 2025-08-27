/**
 * 用户状态管理
 * 使用 Pinia 管理用户登录状态和用户信息
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { UserService, type User, type RegisterRequest } from '../services/user'
import { handleApiError, showError } from '../utils/errorHandler'

export const useUserStore = defineStore('user', () => {
  // 状态
  const userInfo = ref<User | null>(null)
  const isLoading = ref(false)

  // 计算属性
  const isLoggedIn = computed(() => {
    return !!userInfo.value && !!localStorage.getItem('token')
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
      const response = await UserService.login({ username, password })
      if (response.code === 200) {
        // 保存token到localStorage
        if (response.data.token) {
          localStorage.setItem('token', response.data.token)
        }
        await fetchUserInfo()
        return true
      } else {
        throw new Error(response.message || '登录失败')
      }
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
      const response = await UserService.register(registerData)
      if (response.code === 200) {
        // 注册成功后可以选择自动登录或跳转到登录页
        return true
      } else {
        throw new Error(response.message || '注册失败')
      }
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
    localStorage.removeItem('token')
    userInfo.value = null
  }

  /**
   * 获取用户信息
   */
  const fetchUserInfo = async () => {
    if (!localStorage.getItem('token')) {
      userInfo.value = null
      return
    }

    try {
      const response = await UserService.getCurrentUser()
      if (response.code === 200) {
        userInfo.value = response.data
      } else {
        throw new Error(response.message || '获取用户信息失败')
      }
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
    if (localStorage.getItem('token')) {
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