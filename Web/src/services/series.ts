import { get, post } from './api'

// 文章系列接口类型定义
export interface PostSeries {
  id: number
  name: string
  description?: string
  coverImage?: string
  postCount?: number
  createdAt?: string
  updatedAt?: string
}

/**
 * 文章系列服务类
 * 对应后端 PostSeriesController（公开接口）
 */
export class SeriesService {
  /**
   * 获取所有系列列表（含已发布文章数）
   */
  static async getSeriesList(): Promise<PostSeries[]> {
    const response = await get('/series')
    return response.data
  }

  /**
   * 根据ID获取系列详情
   */
  static async getSeriesById(id: number): Promise<PostSeries> {
    const response = await get(`/series/${id}`)
    return response.data
  }

  /**
   * 创建新系列（管理员）
   */
  static async createSeries(data: { name: string; description?: string; coverImage?: string }): Promise<PostSeries> {
    const response = await post('/series', data)
    return response.data
  }
}

export default SeriesService
