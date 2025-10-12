import { get, post } from './api'

// 分类接口类型定义
export interface Category {
  id: number
  name: string
  description?: string
  postCount?: number
}

// 创建分类请求接口
export interface CreateCategoryRequest {
  name: string
  description?: string
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

  /**
   * 创建新分类
   */
  static async createCategory(data: CreateCategoryRequest): Promise<Category> {
    const response = await post('/categories', data)
    return response.data
  }
}