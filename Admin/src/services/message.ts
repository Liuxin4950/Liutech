import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

// 留言相关接口类型定义
export interface Message {
  id?: number
  nickname: string
  email: string
  content: string
  status: number // 0待审核，1已审核，2已拒绝
  reply?: string
  repliedAt?: string
  repliedBy?: number
  createdAt?: string
  updatedAt?: string
  deletedAt?: string
}

export interface MessageListParams {
  page?: number
  size?: number
  nickname?: string
  status?: number
  includeDeleted?: boolean
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export interface ReviewRequest {
  status: number // 1通过，2拒绝
}

export interface ReplyRequest {
  reply: string
}

/**
 * 留言管理服务
 * 对应后端 MessagesAdminController
 */
export class MessagesService {
  private static readonly BASE_URL = '/admin/messages'

  /**
   * 分页查询留言列表
   */
  static async getMessageList(params: MessageListParams = {}): Promise<ApiResponse<PageResult<Message>>> {
    return get<PageResult<Message>>(this.BASE_URL, params)
  }

  /**
   * 根据ID查询留言详情
   */
  static async getMessageById(id: number): Promise<ApiResponse<Message>> {
    return get<Message>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 审核留言（通过/拒绝）
   */
  static async reviewMessage(id: number, status: number): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/review`, { status })
  }

  /**
   * 回复留言
   */
  static async replyMessage(id: number, reply: string): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/reply`, { reply })
  }

  /**
   * 删除留言（软删除）
   */
  static async deleteMessage(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}`)
  }

  /**
   * 批量删除留言（软删除）
   */
  static async batchDeleteMessages(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch`, ids)
  }

  /**
   * 恢复已删除的留言
   */
  static async restoreMessage(id: number): Promise<ApiResponse<string>> {
    return put<string>(`${this.BASE_URL}/${id}/restore`)
  }

  /**
   * 彻底删除留言（物理删除）
   */
  static async permanentDeleteMessage(id: number): Promise<ApiResponse<string>> {
    return del<string>(`${this.BASE_URL}/${id}/permanent`)
  }

  /**
   * 批量彻底删除留言（物理删除）
   */
  static async batchPermanentDeleteMessages(ids: number[]): Promise<ApiResponse<string>> {
    return post<string>(`${this.BASE_URL}/batch/permanent`, ids)
  }
}

// 导出默认实例
export default MessagesService
