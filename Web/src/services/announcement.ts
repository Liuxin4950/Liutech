import { get } from './api'

// 公告接口类型定义
export interface Announcement {
  id: number
  title: string
  content: string
  type: number
  typeName: string
  priority: number
  priorityName: string
  status: number
  statusName: string
  startTime?: string
  endTime?: string
  isTop: number
  viewCount: number
  createdAt?: string
  updatedAt?: string
  isValid: boolean
}

// 分页响应接口
export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
  hasNext?: boolean
  hasPrevious?: boolean
}

// 注意：管理端相关的请求接口已移除
// 用户端只需要查询公告，不需要创建/更新接口

/**
 * 公告服务类
 */
export class AnnouncementService {
  /**
   * 分页查询有效公告（前台用户）
   * @param current 当前页
   * @param size 每页大小
   * @returns 公告分页数据
   */
  static async getValidAnnouncements(current: number = 1, size: number = 10): Promise<PageResponse<Announcement>> {
    try {
      const response = await get('/announcements/list', { current, size })
      return response.data
    } catch (error) {
      console.error('获取有效公告失败:', error)
      throw error
    }
  }

  /**
   * 获取置顶公告列表
   * @param limit 限制数量
   * @returns 置顶公告列表
   */
  static async getTopAnnouncements(limit: number = 5): Promise<Announcement[]> {
    try {
      const response = await get('/announcements/top', { limit })
      return response.data
    } catch (error) {
      console.error('获取置顶公告失败:', error)
      throw error
    }
  }

  /**
   * 获取最新公告列表
   * @param limit 限制数量
   * @returns 最新公告列表
   */
  static async getLatestAnnouncements(limit: number = 10): Promise<Announcement[]> {
    try {
      const response = await get('/announcements/latest', { limit })
      return response.data
    } catch (error) {
      console.error('获取最新公告失败:', error)
      throw error
    }
  }

  /**
   * 根据ID获取公告详情
   * @param id 公告ID
   * @returns 公告详情
   */
  static async getAnnouncementById(id: number): Promise<Announcement> {
    try {
      const response = await get(`/announcements/${id}`)
      return response.data
    } catch (error) {
      console.error('获取公告详情失败:', error)
      throw error
    }
  }

  // 注意：当前前端为用户端，不包含管理功能
  // 管理端接口（创建、更新、删除公告）已移除
}