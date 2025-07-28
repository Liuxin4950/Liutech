import { get } from './api'

// 分类接口类型定义
export interface Category {
  id: number
  name: string
  description?: string
  postCount?: number
}

// 分类服务类
export class CategoryService {
  /**
   * 获取所有分类列表
   */
  static async getCategories(): Promise<Category[]> {
    const response = await get('/categories')
    return response.data
  }

  /**
   * 根据ID获取分类详情
   */
  static async getCategoryById(id: number): Promise<Category> {
    const response = await get(`/categories/${id}`)
    return response.data
  }
}