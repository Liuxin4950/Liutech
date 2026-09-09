/**
 * 登录凭证（JWT token）统一读写入口
 *
 * 约定：全项目只有本文件直接访问 localStorage 里的 token，
 * 拦截器、路由守卫、store、页面一律调用这里的函数。
 *
 * 为什么单独抽出来：此前 token 读写散落在 12 处（api.ts / aiClient.ts / agent.ts /
 * router / stores/user.ts / 403.vue），键名与存储方式一旦要调整就得逐处找，容易漏改。
 * 收敛到一处后，换存储策略（sessionStorage、加密存储等）只改本文件。
 *
 * @author 刘鑫
 */

/** localStorage 中存放 JWT 的键名（与 Web 端保持一致） */
const TOKEN_KEY = 'token'

/** 读取当前 token，未登录返回 null */
export const getToken = (): string | null => localStorage.getItem(TOKEN_KEY)

/** 写入 token（登录成功后调用） */
export const setToken = (token: string): void => localStorage.setItem(TOKEN_KEY, token)

/** 清除 token（退出登录 / 会话失效时调用） */
export const removeToken = (): void => localStorage.removeItem(TOKEN_KEY)

/**
 * 是否已登录
 *
 * 仅判断本地是否存在 token；token 是否真实有效由后端鉴权决定，
 * 前端不以它为安全边界。
 */
export const isLoggedIn = (): boolean => !!getToken()
