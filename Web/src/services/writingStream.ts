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
 * 事件集合与后端 StreamingChatService 严格对齐：
 *   start / data* / tool-start* / tool-result* / field-update* / article-results? / complete / error
 * （heartbeat / avatar-cue / audio / audio-skip / audio-complete 属于看板娘链路，本客户端忽略）
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
 * data 事件负载（正文流式分片）
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
 * field-update 事件负载（AI 回写编辑器字段）
 */
export interface WritingFieldUpdatePayload {
  title?: string
  summary?: string
  contentHtml?: string
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
  /** data 事件：正文分片 */
  onData?: (content: string) => void
  /** article-results 事件：AI 引用的文章列表 */
  onArticles?: (items: WritingArticleItem[], payload: WritingArticleResultsPayload) => void
  /** tool-start 事件：某个工具开始执行 */
  onToolStart?: (payload: WritingToolEventPayload) => void
  /** tool-result 事件：某个工具执行结束 */
  onToolResult?: (payload: WritingToolEventPayload) => void
  /** field-update 事件：AI 回写编辑器字段 */
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
 * - 流读完却没收到 complete/error → 视作连接中断，onError('连接中断，请重试') 后照常 onComplete，
 *   让页面结束 loading 状态。
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

  // 记录是否收到终态事件，用于识别"流被中途掐断"
  let receivedComplete = false
  let receivedError = false

  await readSseStream(response.body, {
    onEvent: (event: ParsedSseEvent) => {
      dispatchEvent(event, handlers, {
        onComplete: () => { receivedComplete = true },
        onError: () => { receivedError = true }
      })
    },
    onParseError: (_rawData: string, error: unknown) => {
      // 单帧坏掉不影响整轮：跳过并留痕，便于线上排查
      console.warn('[写作助手] 忽略无法解析的 SSE 事件', error)
    }
  })

  if (!receivedComplete && !receivedError) {
    handlers.onError?.('连接中断，请重试')
    handlers.onComplete?.({})
  }
}

/**
 * 把解析后的事件分发到对应回调
 *
 * @param event 解析结果（payload 已剥 envelope）
 * @param handlers 回调集合
 * @param flags 终态标记回调，供调用方判断流是否正常收尾
 */
function dispatchEvent(
  event: ParsedSseEvent,
  handlers: WritingStreamHandlers,
  flags: { onComplete: () => void; onError: () => void }
): void {
  // payload 已由解析层剥掉 envelope；这里按事件名收窄类型（服务端 contract 保证结构一致）
  const payload = event.payload as Record<string, unknown> | null

  switch (event.event) {
    case 'start':
      // payload 为空时给空对象，调用方读 payload.model 不会炸
      handlers.onStart?.((event.payload as WritingStartPayload) || {})
      break

    case 'data': {
      const dataPayload = event.payload as WritingDataPayload | null
      handlers.onData?.(dataPayload?.content || '')
      break
    }

    case 'article-results': {
      const articlePayload = event.payload as WritingArticleResultsPayload | null
      handlers.onArticles?.(articlePayload?.items || [], articlePayload as WritingArticleResultsPayload)
      break
    }

    case 'tool-start':
      handlers.onToolStart?.(event.payload as WritingToolEventPayload)
      break

    case 'tool-result':
      handlers.onToolResult?.(event.payload as WritingToolEventPayload)
      break

    case 'field-update':
      handlers.onFieldUpdate?.(event.payload as WritingFieldUpdatePayload)
      break

    case 'complete':
      handlers.onComplete?.(event.payload as WritingCompletePayload)
      flags.onComplete()
      break

    case 'error': {
      const errorPayload = payload as WritingErrorPayload | null
      handlers.onError?.(resolveEventErrorMessage(errorPayload), errorPayload?.code)
      flags.onError()
      break
    }

    default:
      // heartbeat / avatar-cue / audio 等看板娘链路事件：写作助手不需要，直接忽略
      break
  }
}
