import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

// 公告相关接口类型定义
export interface Announcement {
  id?: number
  title: string
  content: string
  type: number
  priority: number
  status: number
  startTime?: string
  endTime?: string
  isTop: number
  viewCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface AnnouncementListItem {
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
  createdAt: string
  updatedAt: string
  deletedAt?: string | null
  isValid: boolean
}

export interface AnnouncementListParams {
  page?: number
  size?: number
  status?: number
  type?: number
  includeDeleted?: boolean
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/**
 * 公告管理服务
 * 对应后端 AnnouncementsController 的管理端接口
 * 
 * @author 刘鑫
 * @date 2025-09-02
 */
export class AnnouncementsService {
  private static readonly BASE_URL = '/announcements'
  private static readonly ADMIN_BASE_URL = '/announcements/admin'

  /**
   * 分页查询公告列表（管理端）
   */
  static async getAnnouncementList(params: AnnouncementListParams = {}): Promise<ApiResponse<PageResult<AnnouncementListItem>>> {
    return get<PageResult<AnnouncementListItem>>(`${this.ADMIN_BASE_URL}/list`, params)
  }

  /**
   * 根据ID查询公告详情
   */
  static async getAnnouncementById(id: number): Promise<ApiResponse<AnnouncementListItem>> {
    return get<AnnouncementListItem>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 创建公告
   */
  static async createAnnouncement(data: Omit<Announcement, 'id' | 'createdAt' | 'updatedAt' | 'viewCount'>): Promise<ApiResponse<number>> {
    return post<number>(this.BASE_URL, data)
  }

  /**
   * 更新公告
   */
  static async updateAnnouncement(id: number, data: Partial<Announcement>): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.BASE_URL}/${id}`, data)
  }

  /**
   * 删除公告
   */
  static async deleteAnnouncement(id: number): Promise<ApiResponse<boolean>> {
    return del<boolean>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 批量删除公告
   */
  static async batchDeleteAnnouncements(ids: number[]): Promise<ApiResponse<boolean>> {
    return del<boolean>(`${this.BASE_URL}/batch`, { data: ids })
  }

  /**
   * 更新公告状态
   */
  static async updateAnnouncementStatus(id: number, status: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.BASE_URL}/${id}/status`, { status })
  }

  /**
   * 批量更新公告状态
   */
  static async batchUpdateAnnouncementStatus(ids: number[], status: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.BASE_URL}/batch/status`, { ids, status })
  }

  /**
   * 恢复已删除的公告
   */
  static async restoreAnnouncement(id: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.BASE_URL}/${id}/restore`)
  }

  /**
   * 置顶/取消置顶公告
   */
  static async toggleAnnouncementTop(id: number, isTop: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.BASE_URL}/${id}/top`, { isTop })
  }
}

export default AnnouncementsService