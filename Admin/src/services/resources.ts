import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

// 资源相关接口类型定义
export interface Resource {
  id?: number
  name: string
  description?: string
  fileUrl?: string
  externalLink?: string
  resourceType?: string
  purchasedNote?: string
  uploaderId?: number
  downloadType?: number
  pointsNeeded?: number
  createdAt?: string
  updatedAt?: string
  deletedAt?: string
  uploaderUsername?: string
}

export interface DownloadLog {
  id?: number
  userId?: number
  resourceId?: number
  pointsUsed?: number
  downloadedAt?: string
  createdAt?: string
  updatedAt?: string
  deletedAt?: string
  username?: string
  resourceName?: string
}

export interface ResourceListParams {
  page?: number
  size?: number
  name?: string
  resourceType?: string
  downloadType?: number
  includeDeleted?: boolean
}

export interface DownloadLogListParams {
  page?: number
  size?: number
  userId?: number
  resourceId?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/**
 * 资源管理服务
 * 对应后端 ResourcesAdminController
 *
 * @author 刘鑫
 */
export class ResourcesService {
  private static readonly BASE_URL = '/admin/resources'

  /**
   * 分页查询资源列表
   */
  static async getResourceList(params: ResourceListParams = {}): Promise<ApiResponse<PageResult<Resource>>> {
    return get<PageResult<Resource>>(this.BASE_URL, params)
  }

  /**
   * 根据ID查询资源详情
   */
  static async getResourceById(id: number): Promise<ApiResponse<Resource>> {
    return get<Resource>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 创建资源
   */
  static async createResource(resource: Resource): Promise<ApiResponse<string>> {
    return post<string>(this.BASE_URL, resource)
  }

  /**
   * 更新资源
   */
  static async updateResource(id: number, resource: Resource): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}`, resource)
  }

  /**
   * 删除资源
   */
  static async deleteResource(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 批量删除资源
   */
  static async batchDeleteResources(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch`, ids)
  }

  /**
   * 恢复已删除的资源
   */
  static async restoreResource(id: number): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/restore`)
  }

  /**
   * 彻底删除资源（物理删除）
   */
  static async permanentDeleteResource(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}/permanent`)
  }

  /**
   * 批量彻底删除资源（物理删除）
   */
  static async batchPermanentDeleteResources(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch/permanent`, ids)
  }

  /**
   * 分页查询下载记录
   */
  static async getDownloadLogs(params: DownloadLogListParams = {}): Promise<ApiResponse<PageResult<DownloadLog>>> {
    return get<PageResult<DownloadLog>>(`${this.BASE_URL}/downloads`, params)
  }
}

export default ResourcesService
