export interface AdminArticleDraftSnapshot {
  postId?: number | null
  title?: string
  content?: string
  summary?: string
  categoryId?: number
  tagIds?: number[]
  status?: string
  coverImage?: string
  thumbnail?: string
}

export interface AgentChatRequest {
  message: string
  conversationId?: number
  context?: Record<string, unknown>
  draft?: AdminArticleDraftSnapshot
}

export interface AgentPlanStep {
  key: string
  title: string
  status: string
}

export interface ArticleResultItem {
  id: number
  title: string
  summary?: string
  status?: string
  categoryName?: string
  tagNames?: string[]
  createdAt?: string
  url?: string
  adminUrl?: string
  reason?: string
  source?: string
}

export interface ArticleResultsPayload {
  source?: string
  query?: string
  reason?: string
  items: ArticleResultItem[]
}

export interface ConfirmationRequiredPayload {
  actionId: number
  actionType: string
  title: string
  description: string
  preview?: AdminArticleDraftSnapshot | Record<string, unknown>
  riskLevel?: string
}

export interface AgentActionResult {
  success: boolean
  message: string
  actionId: number
  actionType?: string
  status?: string
  target?: unknown
}

export interface AgentCompletePayload {
  taskId?: number
  conversationId?: number
}

export interface AgentErrorPayload {
  code?: string
  message?: string
  stage?: string
}

export interface AgentStartPayload {
  taskId?: number
  conversationId?: number
  intent?: string
  role?: 'guest' | 'user' | 'admin' | string
  capabilities?: string[]
}
