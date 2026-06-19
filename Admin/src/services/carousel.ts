import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

/**
 * 轮播图接口类型定义（管理端）
 */
export interface Carousel {
  id?: number
  title: string
  imageUrl: string
  linkUrl?: string
  sortOrder: number
  status: number
  statusName: string
  deleteStatus: string
  createdAt?: string
  updatedAt?: string
  deletedAt?: string | null
}

export interface CarouselListParams {
  page?: number
  current?: number
  size?: number
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

export interface CarouselFormData {
  title: string
  imageUrl: string
  linkUrl?: string
  sortOrder?: number
  status?: number
}

/**
 * 轮播图管理服务
 * 对应后端 CarouselAdminController 的管理端接口
 */
export class CarouselService {
  private static readonly ADMIN_BASE_URL = '/admin/carousels'

  /**
   * 分页查询轮播图列表（管理端）
   */
  static async getCarouselList(params: CarouselListParams = {}): Promise<ApiResponse<PageResult<Carousel>>> {
    return get<PageResult<Carousel>>(this.ADMIN_BASE_URL, params)
  }

  /**
   * 根据ID查询轮播图详情
   */
  static async getCarouselById(id: number): Promise<ApiResponse<Carousel>> {
    return get<Carousel>(`${this.ADMIN_BASE_URL}/${id}`)
  }

  /**
   * 创建轮播图
   */
  static async createCarousel(data: CarouselFormData): Promise<ApiResponse<number>> {
    return post<number>(this.ADMIN_BASE_URL, data)
  }

  /**
   * 更新轮播图
   */
  static async updateCarousel(id: number, data: Partial<CarouselFormData>): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_BASE_URL}/${id}`, data)
  }

  /**
   * 删除轮播图
   */
  static async deleteCarousel(id: number): Promise<ApiResponse<boolean>> {
    return del<boolean>(`${this.ADMIN_BASE_URL}/${id}`)
  }

  /**
   * 批量删除轮播图
   */
  static async batchDeleteCarousels(ids: number[]): Promise<ApiResponse<boolean>> {
    return post<boolean>(`${this.ADMIN_BASE_URL}/batch`, ids)
  }

  /**
   * 更新轮播图状态
   */
  static async updateCarouselStatus(id: number, status: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_BASE_URL}/${id}/status`, { status })
  }

  /**
   * 更新轮播图排序
   */
  static async updateCarouselSort(id: number, sortOrder: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_BASE_URL}/${id}/sort`, { sortOrder })
  }

  /**
   * 恢复已删除的轮播图
   */
  static async restoreCarousel(id: number): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.ADMIN_BASE_URL}/${id}/restore`)
  }

  /**
   * 彻底删除轮播图（物理删除）
   */
  static async permanentDeleteCarousel(id: number): Promise<ApiResponse<boolean>> {
    return del<boolean>(`${this.ADMIN_BASE_URL}/${id}/permanent`)
  }

  /**
   * 批量彻底删除轮播图（物理删除）
   */
  static async batchPermanentDeleteCarousels(ids: number[]): Promise<ApiResponse<boolean>> {
    return post<boolean>(`${this.ADMIN_BASE_URL}/batch/permanent`, ids)
  }
}

export default CarouselService
