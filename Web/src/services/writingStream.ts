/**
 * 写作助手流式客户端核心 —— 前端唯一实现
 *
 * 覆盖范围：`POST {AI服务}/writing/stream`（Web 前台内置写作助手与 Admin 写作助手共用同一端点）。
 *
 * 为什么单独成文件：
 * 此前 Web 的 `services/adminAgent.ts` 与 Admin 的 `services/agent.ts` 各写了一份
 * 同端点的流式客户端，行为已经分叉：Admin 有 429 限流提示、会把 error 事件的 code 交给上层，
 * Web 没有；Web 的 403 文案更贴合写作助手场景。这里取两者之长合成一份。
 *
 * 为什么是两个文件（Web 与 Admin 各一份）：
 * 两个前端是彼此独立的 Vite 根，各自有自己的 package.json 与 Docker 构建上下文，
 * 当前无法共享 npm 包。本文件在两处各存一份且必须逐字节一致，
 * 由 `scripts/check-mirrored-modules.mjs` 在 CI 中校验。
 * 除了同目录的 `./sse`，本文件不依赖任何应用内模块（baseURL 与 token 由调用方注入），
 * 将来迁移到 workspace 共享包时整体搬走即可。
 *
 * 统一写作事件：writing-event 包含 version/requestId/sequence/timestamp/type/data。
 * 旧 start/data/tool/field-update 协议仅为兼容保留，单轮不得混用。
 *
 * @author 刘鑫
 */
import { readSseStream } from './sse'
import type { ParsedSseEvent } from './sse'

/**
 * 文章结果条目
 *
 * 字段来源：LiuTech-AI `dto/PostSummaryDTO`（article-results 事件的 items 元素）。
 * 注意：主流程只填充 id / title，其余字段是否出现取决于后端当时的组装路径，
 * 因此除 id、title 外的字段一律可选 —— 前端不要假设它们一定有值。
 */
export interface WritingArticleItem {
  id: number
  title: string
  summary?: string
  categoryName?: string
  authorName?: string
  tags?: string[]
  viewCount?: number
  likeCount?: number
  createdAt?: string
  /** Web 前台文章详情地址 */
  url?: string
  /** Admin 后台文章地址 */
  adminUrl?: string
  /** 文章状态，公开查询默认 published */
  status?: string
  /** 推荐/搜索原因（部分链路填充） */
  reason?: string
  /** 结果来源：search / latest / hot / recommend 等 */
  source?: string
}

/**
 * article-results 事件负载
 */
export interface WritingArticleResultsPayload {
  source?: string
  query?: string
  reason?: string
  items: WritingArticleItem[]
}

/**
 * 模型答复分片；只有 proposal 建议可以进入编辑器。
 */
export interface WritingDataPayload {
  content: string
  conversationId?: number
}

/**
 * start 事件负载
 *
 * 后端在受理请求后先发这个事件，`model` 是这一轮真实使用的模型名，
 * 管理端进度展示可以据此显示"正在用哪个模型"，取不到时不要编。
 */
export interface WritingStartPayload {
  conversationId?: number
  /** 本轮真实使用的模型名 */
  model?: string
  /** 会话模式：writing / guest / user */
  mode?: string
  baseRevision?: string
  requestId?: string
}

export type WritingActivityStage = 'reading_draft' | 'thinking' | 'reading_article' | 'reading_categories'
  | 'reading_tags' | 'editing_content' | 'updating_fields' | 'validating' | 'ready'

export interface WritingActivityPayload {
  activityId: string
  stage: WritingActivityStage
  status: 'running' | 'completed' | 'failed' | 'cancelled'
  message: string
  startedAt?: number
  finishedAt?: number
  durationMs?: number
  toolName?: string
}

export interface WritingContentPatch {
  baseRevision: string
  edits: Array<{ before: string; after: string }>
}

export interface WritingEventEnvelope {
  version: 1
  requestId: string
  sequence: number
  timestamp: number
  type: 'started' | 'activity' | 'delta' | 'proposal' | 'references' | 'completed' | 'failed' | 'heartbeat'
  data: Record<string, unknown>
}

/**
 * tool-start / tool-result 事件负载
 */
export interface WritingToolEventPayload {
  /** 工具名（后端注册名） */
  toolName: string
  /** 面向用户展示的工具名 */
  displayName: string
  /** 入参摘要，后端已做脱敏与截断 */
  inputSummary?: string
  /** 是否执行成功，tool-start 事件不携带 */
  success?: boolean
  /** 耗时（毫秒），由后端按真实起止时间计算 */
  durationMs?: number
  /** 工具开始时间（epoch 毫秒），仅 tool-start 携带 */
  startedAt?: number
  /** 工具结束时间（epoch 毫秒），仅 tool-result 携带 */
  finishedAt?: number
  /** 结果摘要 */
  resultSummary?: string
  /** 失败原因 */
  errorMessage?: string
}

/**
 * proposal / 旧 field-update 负载（待用户采纳的字段建议）
 */
export interface WritingFieldUpdatePayload {
  title?: string
  summary?: string
  contentHtml?: string
  contentPatch?: WritingContentPatch
  categoryId?: number
  categoryName?: string
  tagIds?: number[]
  tagNames?: string[]
  suggestedCategoryName?: string
  suggestedTagNames?: string[]
  /**
   * 本次真实写入的字段名数组（如 ["title","tagNames"]，正文增量写入时是 ["content"]）。
   *
   * 与上面那些"值字段"的区别：值字段是写进去的内容，fields 是"写了哪些字段"的清单，
   * 由后端按实际写入情况生成。老后端可能没有这个字段，调用方需按 payload 里出现的键自行推断，
   * 不要回退到固定步骤。
   */
  fields?: string[]
}

/**
 * complete 事件负载
 */
export interface WritingCompletePayload {
  taskId?: number
  conversationId?: number
  responseLength?: number
  mode?: string
  ttsEnabled?: boolean
}

/**
 * error 事件负载
 *
 * 后端 `SseEmitterHelper.safeSendError` 把面向用户的文案放在 `error` 键，
 * 其它路径可能用 `message`；两个键都读，避免又出现"后端文案被吞、用户只看到兜底提示"。
 */
export interface WritingErrorPayload {
  code?: string
  message?: string
  error?: string
  stage?: string
  conversationId?: number
}

/**
 * 写作助手流式回调集合
 *
 * 除 onStart 外全部可选：调用方只关心自己页面需要的事件。
 */
export interface WritingStreamHandlers {
  /** start 事件：后端已受理并建立会话，payload 里有本轮真实模型名 */
  onStart?: (payload: WritingStartPayload) => void
  onActivity?: (payload: WritingActivityPayload) => void
  /** delta / 旧 data：模型答复分片，不作为编辑器增量写入 */
  onData?: (content: string) => void
  /** article-results 事件：AI 引用的文章列表 */
  onArticles?: (items: WritingArticleItem[], payload: WritingArticleResultsPayload) => void
  /** tool-start 事件：某个工具开始执行 */
  onToolStart?: (payload: WritingToolEventPayload) => void
  /** tool-result 事件：某个工具执行结束 */
  onToolResult?: (payload: WritingToolEventPayload) => void
  /** proposal / 旧 field-update：待采纳建议 */
  onFieldUpdate?: (payload: WritingFieldUpdatePayload) => void
  /**
   * error 事件或连接异常
   *
   * @param message 面向用户的文案（优先取后端原文）
   * @param code 后端错误码，连接异常时为空
   */
  onError?: (message: string, code?: string) => void
  /** complete 事件：本轮结束 */
  onComplete?: (payload: WritingCompletePayload) => void
}

/**
 * 发起流式请求所需参数
 *
 * baseURL 与 token 由调用方注入，本文件不读环境变量、不读 localStorage，
 * 这样两个前端可以各自沿用自己已有的配置与凭证入口。
 */
export interface WritingStreamOptions {
  /** AI 服务根地址（已含 /ai 后缀，与 serviceConfig 的返回值一致） */
  baseUrl: string
  /** JWT；未登录传 null */
  token: string | null
  /** 请求体（各端请求类型略有差异，由调用方保证结构） */
  request: unknown
  /** 事件回调 */
  handlers: WritingStreamHandlers
  /** 可选取消信号 */
  signal?: AbortSignal
}

/** HTTP 状态码对应的用户提示（两台前端共用同一套文案） */
const HTTP_ERROR_MESSAGES: Record<number, string> = {
  429: '请求过于频繁，请稍后再试',
  403: '当前身份不能使用管理员写作助手'
}

/** 兜底错误文案前缀，后接 HTTP 状态码 */
const FALLBACK_ERROR_PREFIX = '写作助手请求失败：'

/**
 * 从错误响应里解析面向用户的文案
 *
 * 后端参数校验（400）等会返回 `{ code, message, data }`，其中 message 是可直接展示的原文；
 * 读不到 JSON（网关错误页等）时退回状态码提示。
 *
 * @param response 非 2xx 响应
 * @returns 用户可读的错误文案
 */
async function resolveHttpErrorMessage(response: Response): Promise<string> {
  const known = HTTP_ERROR_MESSAGES[response.status]
  if (known) return known

  try {
    const body = await response.json()
    if (body && typeof body.message === 'string' && body.message) {
      return body.message
    }
  } catch {
    // 响应体不是 JSON（如网关错误页），走状态码兜底
  }

  return `${FALLBACK_ERROR_PREFIX}${response.status}`
}

/**
 * 从 error 事件负载里取出面向用户的文案
 *
 * @param payload error 事件负载
 * @returns 文案，取不到时给通用提示
 */
function resolveEventErrorMessage(payload: WritingErrorPayload | null): string {
  if (!payload) return '写作助手执行失败'
  return payload.message || payload.error || '写作助手执行失败'
}

/**
 * 发起写作助手流式请求
 *
 * 返回 Promise 的语义：
 * - HTTP 层失败（非 2xx / 无响应体）→ reject，调用方 catch 后自行提示；
 * - 流内 error 事件 → 不 reject，通过 handlers.onError 上报（与既有实现一致，避免页面同时弹两个提示）；
 * - 流读完却没收到 complete/error → 只上报连接中断，绝不能伪造成功完成事件。
 *
 * @param options baseURL / token / 请求体 / 回调
 */
export async function streamWritingAssistant(options: WritingStreamOptions): Promise<void> {
  const { baseUrl, token, request, handlers, signal } = options

  const response = await fetch(`${baseUrl}/writing/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(request),
    signal
  })

  if (!response.ok || !response.body) {
    throw new Error(await resolveHttpErrorMessage(response))
  }

  let terminal = false
  let protocol: 'unified' | 'legacy' | null = null
  let requestId = ''
  let sequence = 0
  let baseRevision = ''
  const activities = new Map<string, WritingActivityPayload>()
  const fail = (message = '写作事件异常，本轮修改不能应用，请重试') => {
    if (terminal) return
    terminal = true
    handlers.onError?.(message, 'WRITING_PROTOCOL_ERROR')
  }

  try {
    await readSseStream(response.body, {
    onEvent: (event: ParsedSseEvent) => {
      if (signal?.aborted || terminal) return
      try {
        if (event.event === 'writing-event') {
          if (protocol === 'legacy') throw new Error('mixed protocol')
          protocol = 'unified'
          const envelope = validateWritingEnvelope(event.payload)
          if (envelope.sequence !== sequence + 1 || (requestId && envelope.requestId !== requestId)) throw new Error('invalid ordering')
          if (!requestId && envelope.type !== 'started') throw new Error('missing start')
          if (requestId && envelope.type === 'started') throw new Error('duplicate start')
          requestId = envelope.requestId
          sequence = envelope.sequence
          const data = envelope.data
          switch (envelope.type) {
            case 'started':
              if (!textValue(data.baseRevision) || !textValue(data.model) || data.mode !== 'writing') throw new Error('invalid start')
              baseRevision = data.baseRevision as string
              handlers.onStart?.({ ...data, requestId } as WritingStartPayload)
              break
            case 'activity': {
              const activity = validateActivity(data)
              const previous = activities.get(activity.activityId)
              if (previous && (previous.stage !== activity.stage || previous.status !== 'running')) throw new Error('invalid activity transition')
              activities.set(activity.activityId, activity)
              handlers.onActivity?.(activity)
              break
            }
            case 'delta':
              if (typeof data.content !== 'string') throw new Error('invalid delta')
              handlers.onData?.(data.content)
              break
            case 'proposal': {
              const proposal = validateProposal(data)
              if (proposal.contentPatch && proposal.contentPatch.baseRevision !== baseRevision) throw new Error('stale patch')
              handlers.onFieldUpdate?.(proposal)
              break
            }
            case 'references': {
              const references = validateReferences(data)
              handlers.onArticles?.(references.items, references)
              break
            }
            case 'completed':
              if ([...activities.values()].some(activity => activity.status === 'running')) throw new Error('unfinished activity')
              terminal = true
              handlers.onComplete?.(data as WritingCompletePayload)
              break
            case 'failed':
              if (!textValue(data.message) && !textValue(data.error)) throw new Error('invalid failure')
              terminal = true
              handlers.onError?.(resolveEventErrorMessage(data as WritingErrorPayload), typeof data.code === 'string' ? data.code : undefined)
              break
            case 'heartbeat': break
          }
          return
        }
        if (!LEGACY_EVENTS.has(event.event)) throw new Error('unknown event')
        if (protocol === 'unified') throw new Error('mixed protocol')
        protocol = 'legacy'
        dispatchLegacyEvent(event, handlers, () => { terminal = true })
      } catch {
        // 字段或补丁帧丢失会使建议不完整，不能在后续 complete 时允许采纳。
        terminal = false
        fail()
      }
    },
    onParseError: () => { if (!signal?.aborted) fail() },
    shouldStop: () => terminal || !!signal?.aborted
    })
  } catch (error) {
    // 已收到确定终态后，传输层的关闭异常不能把成功结果改判成失败。
    if (!terminal && !signal?.aborted) throw error
  }

  if (!terminal && !signal?.aborted) handlers.onError?.('连接中断，请重试')
}

const LEGACY_EVENTS = new Set(['start', 'data', 'tool-start', 'tool-result', 'field-update', 'article-results', 'complete', 'error', 'heartbeat'])
const STAGES = new Set<WritingActivityStage>(['reading_draft', 'thinking', 'reading_article', 'reading_categories', 'reading_tags', 'editing_content', 'updating_fields', 'validating', 'ready'])
const STATES = new Set(['running', 'completed', 'failed', 'cancelled'])
const EVENT_TYPES = new Set(['started', 'activity', 'delta', 'proposal', 'references', 'completed', 'failed', 'heartbeat'])
const objectValue = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const textValue = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0

function validateWritingEnvelope(value: unknown): WritingEventEnvelope {
  if (!objectValue(value) || value.version !== 1 || !textValue(value.requestId) || value.requestId.length > 128
    || !Number.isSafeInteger(value.sequence) || (value.sequence as number) < 1
    || typeof value.timestamp !== 'number' || !Number.isFinite(value.timestamp) || value.timestamp < 0
    || typeof value.type !== 'string' || !EVENT_TYPES.has(value.type) || !objectValue(value.data)) throw new Error('invalid envelope')
  return value as unknown as WritingEventEnvelope
}

function validateActivity(value: Record<string, unknown>): WritingActivityPayload {
  if (!textValue(value.activityId) || !STAGES.has(value.stage as WritingActivityStage)
    || !STATES.has(value.status as string) || !textValue(value.message)) throw new Error('invalid activity')
  for (const key of ['startedAt', 'finishedAt', 'durationMs']) {
    if (value[key] !== undefined && (typeof value[key] !== 'number' || !Number.isFinite(value[key]) || (value[key] as number) < 0)) throw new Error('invalid timing')
  }
  if (value.toolName !== undefined && typeof value.toolName !== 'string') throw new Error('invalid tool')
  return value as unknown as WritingActivityPayload
}

function validateProposal(value: Record<string, unknown>): WritingFieldUpdatePayload {
  const allowed = new Set(['title', 'summary', 'contentHtml', 'contentPatch', 'categoryId', 'categoryName', 'tagIds', 'tagNames', 'suggestedCategoryName', 'suggestedTagNames', 'fields'])
  if (Object.keys(value).some(key => !allowed.has(key))) throw new Error('unknown proposal field')
  for (const key of ['title', 'summary', 'contentHtml', 'categoryName', 'suggestedCategoryName']) {
    if (value[key] !== undefined && typeof value[key] !== 'string') throw new Error('invalid field')
  }
  if (value.categoryId !== undefined && (!Number.isSafeInteger(value.categoryId) || (value.categoryId as number) <= 0)) throw new Error('invalid category')
  for (const key of ['tagNames', 'suggestedTagNames', 'fields']) {
    if (value[key] !== undefined && (!Array.isArray(value[key]) || !(value[key] as unknown[]).every(item => typeof item === 'string'))) throw new Error('invalid names')
  }
  if (value.tagIds !== undefined && (!Array.isArray(value.tagIds) || !value.tagIds.every(item => Number.isSafeInteger(item) && item > 0))) throw new Error('invalid tags')
  if (value.contentPatch !== undefined) {
    const patch = value.contentPatch
    if (!objectValue(patch) || !textValue(patch.baseRevision) || !Array.isArray(patch.edits) || patch.edits.length < 1 || patch.edits.length > 32
      || !patch.edits.every(edit => objectValue(edit) && textValue(edit.before) && typeof edit.after === 'string')
      || value.contentHtml !== undefined) throw new Error('invalid patch')
  }
  if (!Object.keys(value).some(key => key !== 'fields')) throw new Error('empty proposal')
  return value as WritingFieldUpdatePayload
}

function validateReferences(value: Record<string, unknown>): WritingArticleResultsPayload {
  if (!Array.isArray(value.items) || !value.items.every(item => objectValue(item) && Number.isSafeInteger(item.id) && (item.id as number) > 0 && textValue(item.title))) throw new Error('invalid references')
  return value as unknown as WritingArticleResultsPayload
}

/** 仅用于旧服务兼容；同一请求一旦选择协议，禁止混入另一种事件。 */
function dispatchLegacyEvent(event: ParsedSseEvent, handlers: WritingStreamHandlers, finish: () => void): void {
  if (!objectValue(event.payload)) throw new Error('invalid legacy payload')
  const payload = event.payload
  switch (event.event) {
    case 'start': handlers.onStart?.(payload as WritingStartPayload); break
    case 'data':
      if (typeof payload.content !== 'string') throw new Error('invalid delta')
      handlers.onData?.(payload.content)
      break
    case 'article-results': {
      const references = validateReferences(payload)
      handlers.onArticles?.(references.items, references)
      break
    }
    case 'tool-start': handlers.onToolStart?.(payload as unknown as WritingToolEventPayload); break
    case 'tool-result': handlers.onToolResult?.(payload as unknown as WritingToolEventPayload); break
    case 'field-update': handlers.onFieldUpdate?.(validateProposal(payload)); break
    case 'complete': finish(); handlers.onComplete?.(payload as WritingCompletePayload); break
    case 'error': finish(); handlers.onError?.(resolveEventErrorMessage(payload), typeof payload.code === 'string' ? payload.code : undefined); break
    case 'heartbeat': break
  }
}
