import { get, post } from './api'

// 用户信息接口
export interface UserInfo {
  id: number
  username: string
  avatarUrl?: string
}

// 评论接口
export interface Comment {
  id: number
  postId: number
  content: string
  parentId?: number
  createdAt: string
  user: UserInfo
  children: Comment[]
}

// 分页响应接口
export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
  hasNext?: boolean
  hasPrevious?: boolean
}

// 创建评论请求接口
export interface CreateCommentRequest {
  postId: number
  content: string
  parentId?: number
}

// 评论服务类
export class CommentService {
  /**
   * 获取文章的树形评论结构
   * @param postId 文章ID
   * @returns 树形评论列表
   */
  static async getTreeComments(postId: number): Promise<Comment[]> {
    const response = await get<Comment[]>(`/comments/post/${postId}/tree`)
    return response.data
  }

  /**
   * 分页获取文章评论
   * @param postId 文章ID
   * @param page 页码
   * @param size 每页大小
   * @returns 分页评论列表
   */
  static async getComments(
    postId: number, 
    page: number = 1, 
    size: number = 20
  ): Promise<PageResponse<Comment>> {
    const response = await get<PageResponse<Comment>>(
      `/comments/post/${postId}`,
      { page, size }
    )
    return response.data
  }

  /**
   * 创建评论
   * @param commentData 评论数据
   * @returns 创建的评论
   */
  static async createComment(commentData: CreateCommentRequest): Promise<Comment> {
    const response = await post<Comment>('/comments', commentData)
    return response.data
  }

  /**
   * 获取评论数量
   * @param postId 文章ID
   * @returns 评论数量
   */
  static async getCommentCount(postId: number): Promise<number> {
    const response = await get<number>(`/comments/post/${postId}/count`)
    return response.data
  }

  /**
   * 获取子评论
   * @param parentId 父评论ID
   * @returns 子评论列表
   */
  static async getChildComments(parentId: number): Promise<Comment[]> {
    const response = await get<Comment[]>(`/comments/${parentId}/children`)
    return response.data
  }
}