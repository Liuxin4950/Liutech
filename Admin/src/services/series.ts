import { get, post, put, del } from './api'
import type { ApiResponse } from './api'
import type { PageResult } from './types'

// 文章系列相关接口类型定义
export interface PostSeries {
  id?: number
  name: string
  description?: string
  coverImage?: string
  postCount?: number
  creatorUsername?: string
  createdAt?: string
  updatedAt?: string
  deletedAt?: string | null
}

export interface PostSeriesListParams {
  page?: number
  size?: number
  name?: string
  includeDeleted?: boolean
}

// 系列内文章排序项（拖拽排序用）
export interface SeriesPostOrder {
  postId: number
  seriesSort: number
}

/**
 * 文章系列管理服务
 * 对应后端 PostSeriesAdminController
 *
 * @author 刘鑫
 */
export class PostSeriesService {
  private static readonly BASE_URL = '/admin/series'

  /** 分页查询系列列表 */
  static async getSeriesList(params: PostSeriesListParams = {}): Promise<ApiResponse<PageResult<PostSeries>>> {
    return get<PageResult<PostSeries>>(this.BASE_URL, params)
  }

  /** 根据ID查询系列详情 */
  static async getSeriesById(id: number): Promise<ApiResponse<PostSeries>> {
    return get<PostSeries>(`${this.BASE_URL}/${id}`)
  }

  /** 创建系列 */
  static async createSeries(series: PostSeries): Promise<ApiResponse<string>> {
    return post<string>(this.BASE_URL, series)
  }

  /** 更新系列 */
  static async updateSeries(id: number, series: PostSeries): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}`, series)
  }

  /** 删除系列（软删除） */
  static async deleteSeries(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}`)
  }

  /** 批量删除系列 */
  static async batchDeleteSeries(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch`, ids)
  }

  /** 恢复已删除的系列 */
  static async restoreSeries(id: number): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/restore`)
  }

  /** 彻底删除系列（物理删除） */
  static async permanentDeleteSeries(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}/permanent`)
  }

  /** 批量彻底删除系列（物理删除） */
  static async batchPermanentDeleteSeries(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch/permanent`, ids)
  }

  /** 拖拽排序：批量更新系列内文章排序 */
  static async updatePostsOrder(seriesId: number, items: SeriesPostOrder[]): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${seriesId}/posts-order`, items as any)
  }
}

export default PostSeriesService
