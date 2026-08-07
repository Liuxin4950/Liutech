import { get, post, put, del } from './api'
import type { ApiResponse } from './api'
import type { PageResult } from './types'

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
  seriesId?: number | null
  seriesSort?: number
  status: 'draft' | 'published' | 'archived'
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
  series?: {
    id: number
    name: string
    sort?: number
  } | null
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
  deletedAt?: string | null
}

export interface PostDetail {
  id: number
  title: string
  content: string
  summary?: string
  coverImage?: string
  thumbnail?: string
  categoryId?: number
  seriesId?: number | null
  seriesSort?: number
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
  series?: {
    id: number
    name: string
    description?: string
    coverImage?: string
    sort?: number
    totalCount?: number
  } | null
  seriesCatalog?: Array<{
    id: number
    title: string
    sort: number
    current: boolean
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
  seriesId?: number
  includeDeleted?: boolean
}

/** 收藏某篇文章的用户信息 */
export interface PostFavoriteUser {
  userId: number
  username: string
  nickname?: string
  avatarUrl?: string
  favoriteTime: string
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
  static async createPost(postData: Post): Promise<ApiResponse<Post>> {
    return post<Post>(this.BASE_URL, postData as any)
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
    return post<string>(`${this.BASE_URL}/batch`, ids)
  }

  /**
   * 更新文章状态
   */
  static async updatePostStatus(id: number, status: string): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/status`, null, { params: { status } })
  }

  /**
   * 批量更新文章状态
   * 后端 PostsAdminController 使用 @RequestParam 接收 status，ids 通过 @RequestBody 接收
   */
  static async batchUpdatePostStatus(ids: number[], status: string): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/batch/status?status=${status}`, ids)
  }

  /**
   * 恢复已删除的文章
   */
  static async restorePost(id: number): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/restore`)
  }

  /**
   * 批量恢复已删除的文章
   */
  static async batchRestorePosts(ids: number[]): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/batch/restore`, ids)
  }

  /**
   * 彻底删除文章（物理删除）
   */
  static async permanentDeletePost(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}/permanent`)
  }

  /**
   * 批量彻底删除文章（物理删除）
   */
  static async batchPermanentDeletePosts(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch/permanent`, ids)
  }

  /**
   * 分页查询收藏该文章的用户列表
   */
  static async getPostFavoriteUsers(postId: number, params: { page?: number; size?: number } = {}): Promise<ApiResponse<PageResult<PostFavoriteUser>>> {
    return get<PageResult<PostFavoriteUser>>(`${this.BASE_URL}/${postId}/favorites`, params)
  }
}

// 导出默认实例
export default PostsService
