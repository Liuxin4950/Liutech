import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

// 评论相关接口类型定义
export interface Comment {
  id?: number
  postId: number
  userId: number
  content: string
  parentId?: number
  createdAt?: string
  updatedAt?: string
  deletedAt?: string
  // 关联字段
  postTitle?: string
  user?: {
    id: number
    username: string
    email?: string
    avatarUrl?: string
  }
  children?: Comment[]
}

export interface CommentListParams {
  page?: number
  size?: number
  postId?: number
  userId?: number
  status?: string
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
 * 评论管理服务
 * 对应后端 CommentsAdminController
 *
 * @author 刘鑫
 */
export class CommentsService {
  private static readonly BASE_URL = '/admin/comments'

  /**
   * 分页查询评论列表
   */
  static async getCommentList(params: CommentListParams = {}): Promise<ApiResponse<PageResult<Comment>>> {
    return get<PageResult<Comment>>(this.BASE_URL, params)
  }

  /**
   * 软删除评论
   */
  static async deleteComment(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 批量软删除评论
   */
  static async batchDeleteComments(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch`, ids)
  }

  /**
   * 恢复已删除的评论
   */
  static async restoreComment(id: number): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/restore`)
  }

  /**
   * 彻底删除评论（物理删除）
   */
  static async permanentDeleteComment(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}/permanent`)
  }

  /**
   * 批量彻底删除评论（物理删除）
   */
  static async batchPermanentDeleteComments(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch/permanent`, ids)
  }
}

// 导出默认实例
export default CommentsService

