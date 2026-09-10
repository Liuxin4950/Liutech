/**
 * 写作助手服务（Web 前台内置版）
 *
 * 只做三件事：取 AI 服务地址、取 token、把请求交给流式核心。
 * 协议解析、HTTP 错误文案、事件分发都在 `services/writingStream.ts`（前端唯一实现，
 * 与 `Admin/src/services/writingStream.ts` 逐字节一致，由 CI 校验）。
 *
 * 与 Admin 的差异仅限「请求体类型」与「调用方页面」：
 * Web 前台的文章草稿分类 id 可能是字符串（富文本编辑器回填），因此这里放宽为 number | string。
 * 事件负载类型统一使用 writingStream 的 `Writing*` 定义，本文件按既有名字重新导出，
 * 避免组件层大面积改 import。
 *
 * @author 刘鑫
 */
import { getServiceBaseURL, ServiceType } from '@/services/serviceConfig'
import { getToken } from '@/utils/auth'
import { streamWritingAssistant } from './writingStream'
import type {
  WritingArticleItem,
  WritingArticleResultsPayload,
  WritingFieldUpdatePayload,
  WritingStreamHandlers,
  WritingToolEventPayload
} from './writingStream'

// ==================== 协议类型再导出（保持既有导入名） ====================
export type ToolEventPayload = WritingToolEventPayload
export type FieldUpdatePayload = WritingFieldUpdatePayload
export type ArticleResultItem = WritingArticleItem
export type ArticleResultsPayload = WritingArticleResultsPayload

/**
 * 文章草稿快照
 *
 * Web 侧 `categoryId` 允许字符串：编辑器未选中分类时可能回填空串或字符串 id。
 */
export interface AdminArticleDraftSnapshot {
  postId?: number | null
  title?: string
  content?: string
  summary?: string
  categoryId?: number | string
  tagIds?: number[]
  status?: string
  coverImage?: string
  thumbnail?: string
}

/**
 * 写作计划步骤（前端展示用的计划骨架）
 */
export interface AgentPlanStep {
  key: string
  title: string
  status: string
}

/**
 * 多轮对话中的临时消息
 */
export interface TempMessage {
  role: string
  content: string
}

/**
 * 写作助手请求体
 */
export interface AdminAgentRequest {
  message: string
  conversationId?: number
  context?: Record<string, unknown>
  draft?: AdminArticleDraftSnapshot
  tempMessages?: TempMessage[]
}

/**
 * 写作助手回调集合
 *
 * 与 `WritingStreamHandlers` 结构一致，单独声明是为了让本文件继续对外暴露
 * `AdminAgentHandlers` 这个名字（既有组件与组合式函数都按它引用）。
 */
export type AdminAgentHandlers = WritingStreamHandlers

/**
 * 写作助手服务
 */
export class AdminAgentService {
  /**
   * 发起写作助手流式请求
   *
   * @param request 请求体（消息 + 草稿 + 上下文）
   * @param handlers 事件回调
   * @param signal 可选取消信号
   */
  static async stream(
    request: AdminAgentRequest,
    handlers: AdminAgentHandlers,
    signal?: AbortSignal
  ): Promise<void> {
    await streamWritingAssistant({
      baseUrl: getServiceBaseURL(ServiceType.AI),
      token: getToken(),
      request,
      handlers,
      signal
    })
  }
}
