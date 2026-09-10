/**
 * 写作助手服务（Admin 端）
 *
 * 只做三件事：取 AI 服务地址、取 token、把请求交给流式核心。
 * 协议解析、HTTP 错误文案、事件分发都在 `services/writingStream.ts`（前端唯一实现，
 * 与 `Web/src/services/writingStream.ts` 逐字节一致，由 `scripts/check-mirrored-modules.mjs` 校验）。
 *
 * 此前本文件与 Web 的 `services/adminAgent.ts` 是同一端点的两份独立实现，
 * 行为已经分叉（本文件有 429 提示、Web 没有；Web 的 403 文案更贴合写作助手）。
 * 合并后取两者之长，两端文案与错误语义完全一致。
 *
 * @author 刘鑫
 */
import type { AgentChatRequest } from '../types/agent'
import { getAiBaseUrl } from './serviceConfig'
import { getToken } from '../utils/auth'
import { streamWritingAssistant } from './writingStream'
import type {
  WritingCompletePayload,
  WritingStreamHandlers
} from './writingStream'

/**
 * 写作助手回调集合
 *
 * 与 `WritingStreamHandlers` 结构一致，单独声明是为了继续对外暴露
 * `AgentStreamHandlers` 这个名字（既有组件按它引用）。
 */
export type AgentStreamHandlers = WritingStreamHandlers

/** complete 事件负载（保持既有导入名） */
export type AgentCompletePayload = WritingCompletePayload

/**
 * 写作助手服务
 */
export class AgentService {
  /**
   * 发起写作助手流式请求
   *
   * @param request 请求体（消息 + 草稿 + 上下文）
   * @param handlers 事件回调
   * @param signal 可选取消信号
   */
  static async stream(
    request: AgentChatRequest,
    handlers: AgentStreamHandlers,
    signal?: AbortSignal
  ): Promise<void> {
    await streamWritingAssistant({
      baseUrl: getAiBaseUrl(),
      token: getToken(),
      request,
      handlers,
      signal
    })
  }
}

export default AgentService
