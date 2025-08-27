import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

// 分类相关接口类型定义
export interface Category {
  id?: number
  name: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface CategoryListParams {
  page?: number
  size?: number
  name?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/**
 * 分类管理服务
 * 对应后端 CategoriesAdminController
 * 
 * @author 刘鑫
 */
export class CategoriesService {
  private static readonly BASE_URL = '/admin/categories'

  /**
   * 分页查询分类列表
   */
  static async getCategoryList(params: CategoryListParams = {}): Promise<ApiResponse<PageResult<Category>>> {
    return get<PageResult<Category>>(this.BASE_URL, params)
  }

  /**
   * 根据ID查询分类详情
   */
  static async getCategoryById(id: number): Promise<ApiResponse<Category>> {
    return get<Category>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 创建分类
   */
  static async createCategory(category: Category): Promise<ApiResponse<string>> {
    return post<string>(this.BASE_URL, category)
  }

  /**
   * 更新分类
   */
  static async updateCategory(id: number, category: Category): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}`, category)
  }

  /**
   * 删除分类
   */
  static async deleteCategory(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 批量删除分类
   */
  static async batchDeleteCategories(ids: number[]): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/batch`, { data: ids })
  }
}

// 导出默认实例
export default CategoriesService