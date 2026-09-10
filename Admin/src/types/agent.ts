/**
 * 写作助手相关类型（Admin 端）
 *
 * 分两块：
 * 1. **请求体类型**：各端请求结构略有差异（如 Admin 的分类 id 是数字，Web 允许字符串），
 *    因此留在各端自己的 types 文件里，由 `AdminArticleDraftSnapshot` 等接口定义。
 * 2. **事件负载类型**：属于两端共用的流式协议，唯一定义在 `services/writingStream.ts`
 *    （与 `Web/src/services/writingStream.ts` 逐字节一致）。这里按既有名字重新导出，
 *    使组件层不必因为协议收敛而改 import。
 *
 * @author 刘鑫
 */

// ==================== 共用协议类型（再导出） ====================
export type {
  WritingArticleItem as ArticleResultItem,
  WritingArticleResultsPayload as ArticleResultsPayload,
  WritingCompletePayload as AgentCompletePayload,
  WritingErrorPayload as AgentErrorPayload,
  WritingDataPayload as DataPayload,
  WritingFieldUpdatePayload as FieldUpdatePayload,
  WritingToolEventPayload as ToolEventPayload
} from '../services/writingStream'

// ==================== 请求体类型 ====================

/**
 * 文章草稿快照
 */
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

/**
 * 多轮对话中的临时消息
 */
export interface TempMessage {
  role: 'user' | 'assistant'
  content: string
}

/**
 * 写作助手请求体
 */
export interface AgentChatRequest {
  message: string
  conversationId?: number
  context?: Record<string, unknown>
  draft?: AdminArticleDraftSnapshot
  tempMessages?: TempMessage[]
}

/**
 * 写作计划步骤（前端展示用的计划骨架）
 */
export interface AgentPlanStep {
  key: string
  title: string
  status: string
}
