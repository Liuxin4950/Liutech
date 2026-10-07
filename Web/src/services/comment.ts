import { get, post } from './api'

// 用户信息接口
interface UserInfo {
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
  authorType?: 'HUMAN' | 'BOT'
  botId?: number
  bot?: { id: number; name: string; avatarUrl?: string }
  user?: UserInfo | null
  children: Comment[]
}

// 创建评论请求接口
export interface CreateCommentRequest {
  mentionedBotIds?: number[]
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
  static async getTreeComments(postId: number, signal?: AbortSignal): Promise<Comment[]> {
    const response = await get<Comment[]>(`/comments/post/${postId}/tree`, {}, { signal })
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
}
