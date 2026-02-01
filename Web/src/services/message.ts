import { get, post } from './api'

// 留言接口
export interface Message {
  id: number
  nickname: string
  email: string
  content: string
  status: number
  reply?: string
  repliedAt?: string
  createdAt: string
}

// 创建留言请求接口
export interface CreateMessageRequest {
  nickname: string
  email: string
  content: string
}

// 留言服务类
export class MessageService {
  /**
   * 获取公开留言列表
   */
  static async getPublicMessages(): Promise<Message[]> {
    const response = await get<Message[]>('/messages/public')
    return response.data
  }

  /**
   * 提交留言
   */
  static async createMessage(messageData: CreateMessageRequest): Promise<Message> {
    const response = await post<Message>('/messages', messageData)
    return response.data
  }
}
