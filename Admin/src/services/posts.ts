import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

// 文章相关接口类型定义
export interface Post {
  id?: number
  title: string
  content: string
  summary?: string
  coverImage?: string
  thumbnail?: string
  categoryId?: number
  authorId?: number
  tagIds?: number[]
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface PostListItem {
  id: number
  title: string
  content?: string
  summary?: string
  category?: {
    id: number
    name: string
  }
  author?: {
    id: number
    username: string
    avatarUrl?: string
  }
  tags?: Array<{
    id: number
    name: string
  }>
  commentCount?: number
  coverImage?: string
  thumbnail?: string
  viewCount?: number
  likeCount?: number
  favoriteCount?: number
  likeStatus?: number
  favoriteStatus?: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface PostDetail {
  id: number
  title: string
  content: string
  summary?: string
  coverImage?: string
  thumbnail?: string
  categoryId?: number
  category?: {
    id: number
    name: string
  }
  author?: {
    id: number
    username: string
    avatarUrl?: string
  }
  tags?: Array<{
    id: number
    name: string
  }>
  viewCount?: number
  likeCount?: number
  favoriteCount?: number
  commentCount?: number
  likeStatus?: number
  favoriteStatus?: number
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
  static async getPostById(id: number): Promise<ApiResponse<PostDetail>> {
    return get<PostDetail>(`${this.BASE_URL}/${id}`)
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