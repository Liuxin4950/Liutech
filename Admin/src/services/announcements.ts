import type { Dayjs } from 'dayjs'
import { get, post, put, del, axiosInstance } from './api'
import type { ApiResponse } from './api'
import type { PageResult } from './types'

// 公告相关接口类型定义
export interface Announcement {
  id?: number
  title: string
  content: string
  type: number
  priority: number
  status: number
  startTime?: string|Dayjs
  endTime?: string|Dayjs
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
  current?: number
  size?: number
  status?: number
  type?: number
  includeDeleted?: boolean
  keyword?: string
}

/**
 * 公告管理服务
 * 对应后端 AnnouncementsAdminController（/admin/announcements）的管理端接口
 *
 * @author 刘鑫
 * @date 2025-09-02
 */
export class AnnouncementsService {
  private static readonly ADMIN_URL = '/admin/announcements'

  /**
   * 分页查询公告列表（管理端）
   */
  static async getAnnouncementList(params: AnnouncementListParams = {}): Promise<ApiResponse<PageResult<AnnouncementListItem>>> {
    return get<PageResult<AnnouncementListItem>>(this.ADMIN_URL, params)
  }

  /**
   * 根据ID查询公告详情
   */
  static async getAnnouncementById(id: number): Promise<ApiResponse<AnnouncementListItem>> {
    return get<AnnouncementListItem>(`${this.ADMIN_URL}/${id}`)
  }

  /**
   * 创建公告
   */
  static async createAnnouncement(data: Omit<Announcement, 'id' | 'createdAt' | 'updatedAt' | 'viewCount'>): Promise<ApiResponse<number>> {
    return post<number>(this.ADMIN_URL, data)
  }

  /**
   * 更新公告
   */
  static async updateAnnouncement(id: number, data: Partial<Announcement>): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_URL}/${id}`, data)
  }

  /**
   * 删除公告
   */
  static async deleteAnnouncement(id: number): Promise<ApiResponse<boolean>> {
    return del<boolean>(`${this.ADMIN_URL}/${id}`)
  }

  /**
   * 批量删除公告
   */
  static async batchDeleteAnnouncements(ids: number[]): Promise<ApiResponse<boolean>> {
    return post<boolean>(`${this.ADMIN_URL}/batch`, ids)
  }

  /**
   * 彻底删除公告（物理删除，不可恢复）
   */
  static async permanentDeleteAnnouncement(id: number): Promise<ApiResponse<boolean>> {
    return del<boolean>(`${this.ADMIN_URL}/${id}/permanent`)
  }

  /**
   * 批量彻底删除公告（物理删除，不可恢复）
   */
  static async batchPermanentDeleteAnnouncements(ids: number[]): Promise<ApiResponse<boolean>> {
    return post<boolean>(`${this.ADMIN_URL}/batch/permanent`, ids)
  }

  /**
   * 更新公告状态
   */
  static async updateAnnouncementStatus(id: number, status: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_URL}/${id}/status`, { status })
  }

  /**
   * 批量更新公告状态
   */
  static async batchUpdateAnnouncementStatus(ids: number[], status: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_URL}/batch/status`, { ids, status })
  }

  /**
   * 恢复已删除的公告
   */
  static async restoreAnnouncement(id: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_URL}/${id}/restore`)
  }

  /**
   * 置顶/取消置顶公告
   */
  static async toggleAnnouncementTop(id: number, isTop: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_URL}/${id}/top`, { isTop })
  }

  /**
   * 批量置顶/取消置顶公告
   */
  static async batchToggleAnnouncementTop(ids: number[], isTop: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_URL}/batch/top`, { ids, isTop })
  }

  /**
   * 导出公告数据为Excel
   *
   * 走统一 axios 实例：自动注入 token、复用超时与错误处理，不再自行拼 baseURL。
   * blob 响应不是标准 { code, message, data } 结构，拦截器会把它包装成标准格式，
   * 真实 Blob 位于 data 层（与 Web 端资源下载 post.ts 的解包约定一致）。
   */
  static async exportAnnouncements(params: AnnouncementListParams = {}): Promise<Blob> {
    const response = await axiosInstance.post(`${this.ADMIN_URL}/export`, params, {
      responseType: 'blob'
    })
    // 兼容拦截器包装：有 data 层取 data 层，否则直接用响应体
    const rawData: any = (response.data as any)?.data ?? response.data
    return rawData as Blob
  }

  /**
   * 导入公告数据从Excel
   *
   * FormData 交由 axios 自动设置 multipart 边界（实例不预设 Content-Type）。
   */
  static async importAnnouncements(file: File): Promise<ApiResponse<{ success: number; failed: number; errors?: string[] }>> {
    const formData = new FormData()
    formData.append('file', file)

    return post<{ success: number; failed: number; errors?: string[] }>(
      `${this.ADMIN_URL}/import`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
  }
}

export default AnnouncementsService
