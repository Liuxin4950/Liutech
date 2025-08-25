/**
 * 认证相关工具函数
 * @author 刘鑫
 * @date 2025-01-15
 */

/**
 * 检查用户是否已登录
 * @returns {boolean} 是否已登录
 */
export const isLoggedIn = (): boolean => {
  const token = localStorage.getItem('token')
  return !!token
}

/**
 * 获取当前用户token
 * @returns {string | null} 用户token
 */
export const getToken = (): string | null => {
  return localStorage.getItem('token')
}

/**
 * 获取当前用户ID
 * @returns {string | null} 用户ID
 */
export const getCurrentUserId = (): string | null => {
  return localStorage.getItem('userId')
}

/**
 * 清除用户认证信息
 */
export const clearAuth = (): void => {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('username')
}

/**
 * 设置用户认证信息
 * @param token 用户token
 * @param userId 用户ID
 * @param username 用户名
 */
export const setAuth = (token: string, userId: string, username: string): void => {
  localStorage.setItem('token', token)
  localStorage.setItem('userId', userId)
  localStorage.setItem('username', username)
}

/**
 * 登录拦截检查
 * @param callback 登录后的回调函数
 * @param showModal 显示登录弹窗的函数
 * @returns {boolean} 是否通过检查
 */
export const requireAuth = (callback?: () => void, showModal?: (message?: string) => void): boolean => {
  if (isLoggedIn()) {
    callback?.()
    return true
  } else {
    showModal?.()
    return false
  }
}