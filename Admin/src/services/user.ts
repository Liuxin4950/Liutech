import { post, get, put, del } from './api'
import type { ApiResponse } from './api'

// 用户相关接口类型定义
export interface User {
  id: number
  username: string
  email: string
  nickname?: string
  avatar?: string
  status: number  // 用户状态：0禁用，1正常
  role?: string
  passwordHash?: string
  createdAt?: string
  updatedAt?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginResponse {
  token: string
}

export interface UserListParams {
  page?: number
  size?: number
  username?: string
  email?: string
  status?: number  // 用户状态：0禁用，1正常
  role?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/**
 * 用户服务
 */
export class UserService {
  private static readonly ADMIN_BASE_URL = '/admin/users'
  /**
   * 用户登录
   */
  static async login(data: LoginRequest): Promise<ApiResponse<LoginResponse>> {
    return post<LoginResponse>('/user/login', data)
  }

  /**
   * 用户注册
   */
  static async register(data: RegisterRequest): Promise<ApiResponse<string>> {
    return post<string>('/user/register', data)
  }

  /**
   * 获取当前用户信息
   */
  static async getCurrentUser(): Promise<ApiResponse<User>> {
    return get<User>('/user/current')
  }

  /**
   * 更新用户信息
   */
  static async updateProfile(data: Partial<User>): Promise<ApiResponse<string>> {
    return put<string>('/user/profile', data)
  }

  // === 管理端用户管理接口 ===

  /**
   * 分页查询用户列表（管理端）
   */
  static async getUserList(params: UserListParams = {}): Promise<ApiResponse<PageResult<User>>> {
    return get<PageResult<User>>(this.ADMIN_BASE_URL, params)
  }

  /**
   * 根据ID查询用户详情（管理端）
   */
  static async getUserById(id: number): Promise<ApiResponse<User>> {
    return get<User>(`${this.ADMIN_BASE_URL}/${id}`)
  }

  /**
   * 创建用户（管理端）
   */
  static async createUser(user: User): Promise<ApiResponse<string>> {
    return post<string>(this.ADMIN_BASE_URL, user)
  }

  /**
   * 更新用户（管理端）
   */
  static async updateUser(id: number, user: User): Promise<ApiResponse<string>> {
    return put<string>(`${this.ADMIN_BASE_URL}/${id}`, user)
  }

  /**
   * 删除用户（管理端）
   */
  static async deleteUser(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.ADMIN_BASE_URL}/${id}`)
  }

  /**
   * 批量删除用户（管理端）
   */
  static async batchDeleteUsers(ids: number[]): Promise<ApiResponse<string>> {
    return del<string>(`${this.ADMIN_BASE_URL}/batch`, { data: ids })
  }

  /**
   * 更新用户状态（管理端）
   */
  static async updateUserStatus(id: number, enabled: boolean): Promise<ApiResponse<string>> {
    return put<string>(`${this.ADMIN_BASE_URL}/${id}/status`, null, { params: { enabled } })
  }

}

// 导出便捷方法
export const {
  login,
  register,
  getCurrentUser,
  updateProfile,
  getUserList,
  getUserById,
  createUser,
  updateUser,
  deleteUser,
  batchDeleteUsers,
  updateUserStatus
} = UserService

export default UserService