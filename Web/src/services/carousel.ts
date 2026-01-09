import { get } from './api'
import type { ApiResponse } from './api'

/**
 * 轮播图接口类型定义
 */
export interface Carousel {
  id?: number
  title: string
  imageUrl: string
  linkUrl?: string
  sortOrder: number
  status: number
  createdAt?: string
  updatedAt?: string
  deletedAt?: string | null
}

/**
 * 轮播图服务
 * 对应后端 CarouselController 的前台接口
 */
export class CarouselService {
  private static readonly BASE_URL = '/carousels'

  /**
   * 获取启用的轮播图列表（前台展示）
   */
  static async getActiveCarousels(): Promise<ApiResponse<Carousel[]>> {
    return get<Carousel[]>(this.BASE_URL)
  }
}

export default CarouselService
