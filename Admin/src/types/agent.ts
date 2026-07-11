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

export interface WritingDraftPayload {
  title?: string
  summary?: string
  contentHtml?: string
  categoryId?: number
  categoryName?: string
  tagIds?: number[]
  tagNames?: string[]
  suggestedCategoryName?: string
  suggestedTagNames?: string[]
  coverPrompt?: string
  notes?: string
  checks?: string[]
  htmlSafe?: boolean
}

export interface TempMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AgentChatRequest {
  message: string
  conversationId?: number
  context?: Record<string, unknown>
  draft?: AdminArticleDraftSnapshot
  tempMessages?: TempMessage[]
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
  preview?: AdminArticleDraftSnapshot | WritingDraftPayload | Record<string, unknown>
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

/**
 * 工具事件负载（tool-start / tool-result）。
 */
export interface ToolEventPayload {
  toolName: string
  displayName: string
  inputSummary?: string
  success?: boolean
  durationMs?: number
  resultSummary?: string
  errorMessage?: string
}

export interface WritingDraftEventPayload extends WritingDraftPayload {}

export interface FieldUpdatePayload {
  title?: string
  summary?: string
  contentHtml?: string
  categoryId?: number
  categoryName?: string
  tagIds?: number[]
  tagNames?: string[]
  suggestedCategoryName?: string
  suggestedTagNames?: string[]
}

/**
 * data 事件负载。
 */
export interface DataPayload {
  content: string
}

