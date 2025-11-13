import { get, post, put, del, ServiceType } from './api'

export interface Conversation {
  id: number
  userId: string
  type: string
  title?: string
  status: number
  messageCount: number
  lastMessageAt?: string
}

export interface ChatMessageItem {
  id: number
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
}

export const ConversationService = {
  async list(type?: string, page: number = 1, size: number = 20): Promise<Conversation[]> {
    const res = await get<Conversation[]>('/ai/conversations', { type, page, size }, { serviceType: ServiceType.AI })
    return res as unknown as Conversation[]
  },
  async create(type: string = 'general', title?: string): Promise<number> {
    const res = await post('/ai/conversations', null as any, { params: { type, title }, serviceType: ServiceType.AI } as any)
    const obj: any = res
    return obj?.conversationId || 0
  },
  async messages(id: number, page: number = 1, size: number = 50): Promise<ChatMessageItem[]> {
    const res = await get<ChatMessageItem[]>(`/ai/conversations/${id}/messages`, { page, size }, { serviceType: ServiceType.AI })
    return res as unknown as ChatMessageItem[]
  },
  async rename(id: number, title: string): Promise<void> {
    await put(`/ai/conversations/${id}/rename`, null as any, { params: { title }, serviceType: ServiceType.AI } as any)
  },
  async archive(id: number): Promise<void> {
    await put(`/ai/conversations/${id}/archive`, null as any, { serviceType: ServiceType.AI } as any)
  },
  async remove(id: number): Promise<void> {
    await del(`/ai/conversations/${id}`, { serviceType: ServiceType.AI } as any)
  }
}

export default ConversationService
