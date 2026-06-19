import { get, post } from './api'
import type { ApiResponse } from './api'
import type { PageResult } from './types'

// 积分流水记录
export interface PointsTransaction {
  id: number
  userId: number
  username: string
  transactionType: string
  amount: number
  balanceAfter: number
  sourceType: string
  sourceId: number
  description: string
  createdAt: string
}

// 签到记录
export interface UserCheckin {
  id: number
  userId: number
  username: string
  checkinDate: string
  pointsEarned: number
  consecutiveDays: number
  createdAt: string
  updatedAt: string
}

// 积分统计
export interface PointsStats {
  totalIssued: number
  totalConsumed: number
  totalBalance: number
}

// 积分流水查询参数
export interface TransactionListParams {
  page?: number
  size?: number
  userId?: number
  transactionType?: string
  startTime?: string
  endTime?: string
}

// 签到记录查询参数
export interface CheckinListParams {
  page?: number
  size?: number
  userId?: number
  startDate?: string
  endDate?: string
}

// 积分调整请求
export interface PointsAdjustRequest {
  userId: number
  amount: number
  description?: string
}

/**
 * 积分管理服务
 */
export class PointsService {
  private static readonly BASE_URL = '/admin/points'

  /**
   * 分页查询积分流水
   */
  static async getTransactionList(params: TransactionListParams = {}): Promise<ApiResponse<PageResult<PointsTransaction>>> {
    return get<PageResult<PointsTransaction>>(`${this.BASE_URL}/transactions`, params)
  }

  /**
   * 管理员手动调整积分
   */
  static async adjustPoints(data: PointsAdjustRequest): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/adjust`, data)
  }

  /**
   * 分页查询签到记录
   */
  static async getCheckinList(params: CheckinListParams = {}): Promise<ApiResponse<PageResult<UserCheckin>>> {
    return get<PageResult<UserCheckin>>(`${this.BASE_URL}/checkins`, params)
  }

  /**
   * 获取积分统计信息
   */
  static async getPointsStats(): Promise<ApiResponse<PointsStats>> {
    return get<PointsStats>(`${this.BASE_URL}/stats`)
  }
}

export default PointsService
