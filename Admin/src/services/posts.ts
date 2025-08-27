import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

// 文章相关接口类型定义
export interface Post {
  id?: number
  title: string
  content: string
  summary?: string
  categoryId?: number
  authorId?: number
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface PostListItem {
  id: number
  title: string
  summary?: string
  categoryName?: string
  authorName?: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface PostListParams {
  page?: number
  size?: number
  title?: string
  categoryId?: number
  status?: string
  authorId?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/**
 * 文章管理服务
 * 对应后端 PostsAdminController
 * 
 * @author 刘鑫
 */
export class PostsService {
  private static readonly BASE_URL = '/admin/posts'

  /**
   * 分页查询文章列表
   */
  static async getPostList(params: PostListParams = {}): Promise<ApiResponse<PageResult<PostListItem>>> {
    return get<PageResult<PostListItem>>(this.BASE_URL, params)
  }

  /**
   * 根据ID查询文章详情
   */
  static async getPostById(id: number): Promise<ApiResponse<Post>> {
    return get<Post>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 创建文章
   */
  static async createPost(postData: Post): Promise<ApiResponse<string>> {
    return post<string>(this.BASE_URL, postData as any)
  }

  /**
   * 更新文章
   */
  static async updatePost(id: number, post: Post): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}`, post)
  }

  /**
   * 删除文章
   */
  static async deletePost(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 批量删除文章
   */
  static async batchDeletePosts(ids: number[]): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/batch`, { data: ids })
  }

  /**
   * 更新文章状态
   */
  static async updatePostStatus(id: number, status: string): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/status`, null, { params: { status } })
  }

  /**
   * 批量更新文章状态
   */
  static async batchUpdatePostStatus(ids: number[], status: string): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/batch/status`, ids, { params: { status } })
  }
}

// 导出默认实例
export default PostsService