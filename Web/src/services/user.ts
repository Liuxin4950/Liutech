import { get, post, put } from './api'

// 登录请求参数接口
interface LoginRequest {
  username: string
  password: string
}

// 邮箱验证码登录请求参数接口
interface EmailLoginRequest {
  email: string
}

// 邮箱验证码登录验证参数接口
interface EmailLoginVerifyRequest {
  email: string
  code: string
}

// 忘记密码请求参数接口
interface ForgotPasswordRequest {
  email: string
}

// 重置密码请求参数接口
interface ResetPasswordRequest {
  email: string
  code: string
  newPassword: string
}

// 注册请求参数接口
export interface RegisterRequest {
  username: string
  email: string
  code: string
  password: string
  nickname?: string
}

// 修改密码请求参数接口
interface ChangePasswordRequest {
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
  role?: string  // 用户角色：user/admin
  status?: number
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}

// 个人资料统计信息接口
interface ProfileStats {
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
  favoriteCount?: number  // 收藏文章数量
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
interface LoginResponse {
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
      // 添加时间戳防止缓存
      const response = await get<UserInfo>('/user/current', { _t: Date.now() })
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
    // 清除可能存在的用户缓存
    localStorage.removeItem('user')
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

  /**
   * 获取网站作者资料
   * @returns Promise<ProfileInfo>
   */
  static async getAuthorProfile(): Promise<ProfileInfo> {
    try {
      const response = await get<ProfileInfo>('/user/author/profile')
      return response.data
    } catch (error) {
      console.error('获取网站作者资料失败', error)
      throw error
    }
  }

  /**
   * 忘记密码 - 发送验证码
   */
  static async sendForgotPasswordCode(email: string) {
    return await post<null>('/user/forgot-password', { email })
  }

  /**
   * 注册 - 发送邮箱验证码
   */
  static async sendRegisterCode(email: string) {
    return await post<null>('/user/register/send-code', { email })
  }

  /**
   * 忘记密码 - 重置密码
   */
  static async resetPassword(data: ResetPasswordRequest) {
    return await post<null>('/user/reset-password', data)
  }

  /**
   * 邮箱登录 - 发送验证码
   */
  static async sendEmailLoginCode(email: string) {
    return await post<null>('/user/login/email/send', { email })
  }

  /**
   * 邮箱登录 - 验证码校验
   */
  static async verifyEmailLogin(data: EmailLoginVerifyRequest): Promise<LoginResponse> {
    try {
      const response = await post<LoginResponse>('/user/login/email/verify', data)
      if (response.data.token) {
        localStorage.setItem('token', response.data.token)
      }
      return response.data
    } catch (error) {
      console.error('邮箱验证码登录失败', error)
      throw error
    }
  }
}

// 导出便捷方法
export const {
  getUserStats,
  getProfile,
  getAuthorProfile
} = UserService

// 新增方法导出
export const sendForgotPasswordCode = UserService.sendForgotPasswordCode
export const sendRegisterCode = UserService.sendRegisterCode
export const resetPassword = UserService.resetPassword
export const sendEmailLoginCode = UserService.sendEmailLoginCode

export default UserService
