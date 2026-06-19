import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

/**
 * 图片相关接口类型定义
 */
export interface Image {
  id: number
  fileName: string
  fileUrl: string
  filePath: string
  fileHash: string
  fileSize: number
  mimeType: string
  extension: string
  width: number
  height: number
  uploaderId: number
  uploaderUsername?: string
  usageCount: number
  status: number
  createdAt: string
  updatedAt: string
  deletedAt: string | null
  createdBy: number
  updatedBy: number
}

export interface ImageListParams {
  page?: number
  size?: number
  fileName?: string
  mimeType?: string
  status?: number
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
 * 图片管理服务
 * 对应后端 ImagesAdminController
 *
 * @author 刘鑫
 */
export class ImagesService {
  private static readonly BASE_URL = '/admin/images'

  /**
   * 分页查询图片列表
   */
  static async getImageList(params: ImageListParams = {}): Promise<ApiResponse<PageResult<Image>>> {
    return get<PageResult<Image>>(this.BASE_URL, params)
  }

  /**
   * 软删除图片
   */
  static async deleteImage(id: number): Promise<ApiResponse<any>> {
    return del<any>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 批量软删除图片
   */
  static async batchDeleteImages(ids: number[]): Promise<ApiResponse<any>> {
    return post<any>(`${this.BASE_URL}/batch`, ids)
  }

  /**
   * 恢复已删除的图片
   */
  static async restoreImage(id: number): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/restore`)
  }

  /**
   * 彻底删除图片（物理删除，同时删除文件系统中的文件）
   */
  static async permanentDeleteImage(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}/permanent`)
  }

  /**
   * 批量彻底删除图片（物理删除）
   */
  static async batchPermanentDeleteImages(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch/permanent`, ids)
  }

  /**
   * 查询孤立图片（usage_count = 0）
   */
  static async getOrphanImages(): Promise<ApiResponse<Image[]>> {
    return get<Image[]>(`${this.BASE_URL}/orphans`)
  }

  /**
   * 清理孤立图片
   */
  static async cleanupOrphanImages(): Promise<ApiResponse<number>> {
    return post<number>(`${this.BASE_URL}/cleanup-orphans`)
  }

  /**
   * 图片引用对账（重置 usage_count 并重新统计）
   */
  static async reconcileUsage(): Promise<ApiResponse<ImageUsageReconcileResult>> {
    return post<ImageUsageReconcileResult>(`${this.BASE_URL}/reconcile-usage`)
  }
}

export interface ImageUsageReconcileResult {
  resetRows: number
  referencedPaths: number
  updatedImages: number
  missingImages: number
}

export default ImagesService
