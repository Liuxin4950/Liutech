import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

// 标签相关接口类型定义
export interface Tag {
  id?: number
  name: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface TagListParams {
  page?: number
  size?: number
  name?: string
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
 * 标签管理服务
 * 对应后端 TagsAdminController
 * 
 * @author 刘鑫
 */
export class TagsService {
  private static readonly BASE_URL = '/admin/tags'

  /**
   * 分页查询标签列表
   */
  static async getTagList(params: TagListParams = {}): Promise<ApiResponse<PageResult<Tag>>> {
    return get<PageResult<Tag>>(this.BASE_URL, params)
  }

  /**
   * 根据ID查询标签详情
   */
  static async getTagById(id: number): Promise<ApiResponse<Tag>> {
    return get<Tag>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 创建标签
   */
  static async createTag(tag: Tag): Promise<ApiResponse<string>> {
    return post<string>(this.BASE_URL, tag)
  }

  /**
   * 更新标签
   */
  static async updateTag(id: number, tag: Tag): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}`, tag)
  }

  /**
   * 删除标签
   */
  static async deleteTag(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 批量删除标签
   */
  static async batchDeleteTags(ids: number[]): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/batch`, { data: ids })
  }

  /**
   * 恢复已删除的标签
   */
  static async restoreTag(id: number): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/restore`)
  }
}

// 导出默认实例
export default TagsService