import { get } from './api'
import type { ApiResponse } from './api'

/**
 * 仪表盘统计相关接口类型定义
 */
export interface DashboardStats {
  // 基础统计
  basicStats: BasicStats
  // 文章状态分布
  postStatusDistribution: StatusDistribution[]
  // 文章发布趋势
  postTrend: TrendData[]
  // 用户注册趋势
  userTrend: TrendData[]
  // 热门文章TOP
  topPosts: PostRank[]
  // 活跃用户TOP
  topAuthors: UserRank[]
}

export interface BasicStats {
  postCount: number
  publishedPostCount: number
  draftPostCount: number
  userCount: number
  categoryCount: number
  tagCount: number
  commentCount: number
  totalViews: number
}

export interface StatusDistribution {
  status: string
  displayName: string
  count: number
  percentage: number
}

export interface TrendData {
  date: string
  count: number
}

export interface PostRank {
  id: number
  title: string
  viewCount: number
  likeCount: number
  commentCount: number
}

export interface UserRank {
  id: number
  username: string
  nickname: string
  postCount: number
}

const ADMIN_DASHBOARD_URL = '/admin/dashboard'

/**
 * 获取仪表盘统计数据
 */
export async function getDashboardStats(): Promise<ApiResponse<DashboardStats>> {
  return get<DashboardStats>(`${ADMIN_DASHBOARD_URL}/stats`)
}

// 导出默认对象（兼容导入方式）
export default {
  getDashboardStats
}
