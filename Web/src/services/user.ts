import { get, post, put } from './api'

// 登录请求参数接口
export interface LoginRequest {
  username: string
  password: string
}

// 注册请求参数接口
export interface RegisterRequest {
  username: string
  email: string
  password: string
  avatar?: string
  nickname?: string
}

// 修改密码请求参数接口
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

// 更新个人资料请求参数接口
export interface UpdateProfileRequest {
  email?: string
  avatarUrl?: string
  nickname?: string
  bio?: string
}

// 用户信息接口
export interface UserInfo {
  id?: number
  username: string
  email: string
  avatarUrl?: string
  nickname?: string
  bio?: string
  points: number
  status?: number
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}

// 个人资料统计信息接口
export interface ProfileStats {
  posts: number
  comments: number
  views: number
}

// 个人资料信息接口
export interface ProfileInfo {
  name: string
  title: string
  avatar: string
  bio: string
  stats: ProfileStats
}

// 用户统计信息接口
export interface UserStats {
  id?: number
  username: string
  email: string
  avatarUrl?: string
  nickname?: string
  bio?: string
  points: number
  status?: number
  lastLoginAt?: string
  createdAt?: string
  commentCount: number
  postCount: number
  draftCount: number
  viewCount: number
  lastCommentAt?: string
  lastPostAt?: string
}

// 签到响应接口
export interface CheckinResponse {
  pointsEarned: number
  totalPoints: number
  consecutiveDays: number
  checkinDate: string
}

// 签到状态接口
export interface CheckinStatus {
  hasCheckedInToday: boolean
  consecutiveDays: number
  lastCheckinDate?: string
  totalCheckins: number
}

// 登录响应数据接口
export interface LoginResponse {
  token: string
}

/**
 * 用户服务类
 */
export class UserService {
  /**
   * 用户登录
   * @param data 登录数据
   * @returns Promise<LoginResponse>
   */
  static async login(data: LoginRequest): Promise<LoginResponse> {
    try {
      const response = await post<LoginResponse>('/user/login', data)
      
      // 保存 token 到本地存储
      if (response.data.token) {
        localStorage.setItem('token', response.data.token)
      }
      
      return response.data
    } catch (error) {
      console.error('登录失败', error)
      throw error
    }
  }

  /**
   * 用户注册
   * @param data 注册数据
   * @returns Promise<UserInfo>
   */
  static async register(data: RegisterRequest): Promise<UserInfo> {
    try {
      const response = await post<UserInfo>('/user/register', data)
      return response.data
    } catch (error) {
      console.error('注册失败', error)
      throw error
    }
  }

  /**
   * 获取当前用户信息
   * @returns Promise<UserInfo>
   */
  static async getCurrentUser(): Promise<UserInfo> {
    try {
      const response = await get<UserInfo>('/user/current')
      return response.data
    } catch (error) {
      console.error('获取用户信息失败', error)
      throw error
    }
  }

  /**
   * 修改密码
   * @param data 修改密码数据
   * @returns Promise<void>
   */
  static async changePassword(data: ChangePasswordRequest): Promise<void> {
    try {
      await put<null>('/user/password', data)
    } catch (error) {
      console.error('修改密码失败', error)
      throw error
    }
  }

  /**
   * 更新个人资料
   * @param data 更新资料数据
   * @returns Promise<UserInfo>
   */
  static async updateProfile(data: UpdateProfileRequest): Promise<UserInfo> {
    try {
      const response = await put<UserInfo>('/user/profile', data)
      return response.data
    } catch (error) {
      console.error('更新个人资料失败', error)
      throw error
    }
  }

  /**
   * 用户登出
   */
  static logout(): void {
    localStorage.removeItem('token')
    // 可以在这里添加其他清理逻辑
  }

  /**
   * 检查是否已登录
   * @returns boolean
   */
  static isLoggedIn(): boolean {
    const token = localStorage.getItem('token')
    return !!token
  }

  /**
   * 获取存储的 token
   * @returns string | null
   */
  static getToken(): string | null {
    return localStorage.getItem('token')
  }

  /**
   * 获取用户统计信息
   * @returns Promise<UserStats>
   */
  static async getUserStats(): Promise<UserStats> {
    try {
      const response = await get<UserStats>('/user/stats')
      return response.data
    } catch (error) {
      console.error('获取用户统计信息失败', error)
      throw error
    }
  }

  /**
   * 每日签到
   * @returns Promise<CheckinResponse>
   */
  static async checkin(): Promise<CheckinResponse> {
    try {
      const response = await post<CheckinResponse>('/user/checkin', {})
      return response.data
    } catch (error) {
      console.error('签到失败', error)
      throw error
    }
  }

  /**
   * 获取签到状态
   * @returns Promise<CheckinStatus>
   */
  static async getCheckinStatus(): Promise<CheckinStatus> {
    try {
      const response = await get<CheckinStatus>('/user/checkin/status')
      return response.data
    } catch (error) {
      console.error('获取签到状态失败', error)
      throw error
    }
  }

  /**
   * 获取个人资料信息
   * @returns Promise<ProfileInfo>
   */
  static async getProfile(): Promise<ProfileInfo> {
    try {
      const response = await get<ProfileInfo>('/user/profile')
      return response.data
    } catch (error) {
      console.error('获取个人资料失败', error)
      throw error
    }
  }
}

// 导出便捷方法
export const {
  login,
  register,
  getCurrentUser,
  changePassword,
  updateProfile,
  logout,
  isLoggedIn,
  getToken,
  getUserStats,
  getProfile,
  checkin,
  getCheckinStatus
} = UserService

export default UserService