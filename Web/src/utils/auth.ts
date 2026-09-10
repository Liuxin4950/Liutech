/**
 * 登录凭证（JWT token）统一读写入口
 *
 * 约定：本项目只有本文件直接访问 localStorage 里的 token，
 * 拦截器、路由守卫、store、页面一律调用这里的函数。
 *
 * 为什么单独抽出来：此前 token 读写散落在 api.ts / user.ts / aiStream.ts /
 * adminAgent.ts / router / 文章详情页等处，键名或存储策略一旦要改就得逐处找，容易漏改。
 * 收敛到一处后，换存储策略（sessionStorage、加密存储等）只改本文件。
 *
 * 与 Admin 端 `Admin/src/utils/auth.ts` 保持同一套函数名与语义，两端写法一致。
 *
 * @author 刘鑫
 * @date 2025-01-15
 */

/** localStorage 中存放 JWT 的键名（与 Admin 端保持一致） */
const TOKEN_KEY = 'token'

/**
 * 获取当前用户token
 * @returns {string | null} 用户token
 */
export const getToken = (): string | null => {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 写入 token（登录成功后调用）
 * @param token JWT
 */
export const setToken = (token: string): void => {
  localStorage.setItem(TOKEN_KEY, token)
}

/**
 * 清除 token（退出登录 / 会话失效时调用）
 */
export const removeToken = (): void => {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 检查用户是否已登录
 *
 * 仅判断本地是否存在 token；token 是否真实有效由后端鉴权决定，
 * 前端不以它为安全边界。
 *
 * @returns {boolean} 是否已登录
 */
export const isLoggedIn = (): boolean => {
  return !!getToken()
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
