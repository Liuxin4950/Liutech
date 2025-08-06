import { get } from './api'

// 标签接口类型定义
export interface Tag {
  id: number
  name: string
  postCount: number
}

// 标签服务类
export class TagService {
  /**
   * 获取所有标签列表
   */
  static async getTags(): Promise<Tag[]> {
    const response = await get('/tags')
    return response.data
  }

  /**
   * 获取热门标签列表
   */
  static async getHotTags(limit: number = 20): Promise<Tag[]> {
    const response = await get('/tags/hot', { limit })
    return response.data
  }

  /**
   * 根据ID获取标签详情
   */
  static async getTagById(id: number): Promise<Tag> {
    const response = await get(`/tags/${id}`)
    return response.data
  }

  /**
   * 根据文章ID获取标签列表
   */
  static async getTagsByPostId(postId: number): Promise<Tag[]> {
    const response = await get(`/tags/post/${postId}`)
    return response.data
  }

  /**
   * 根据名字搜索标签
   */
  static async searchTagsByName(name: string): Promise<Tag[]> {
    const response = await get('/tags/search', { name })
    return response.data
  }
}