/**
 * SSE（Server-Sent Events）解析 —— 前端唯一实现
 *
 * 为什么单独成文件：
 * Web 与 Admin 都要消费同一个 AI 服务（LiuTech-AI）的 SSE 流，此前各自实现了一份解析，
 * 协议理解已经分叉：`start` 事件丢掉 conversationId、`error` 事件读错键名导致后端文案被吞。
 * 这里把「字节流 → 事件对象」这一段收敛成唯一实现，两个前端共用同一套语义。
 *
 * 为什么是两个文件（Web 与 Admin 各一份）：
 * 两个前端是彼此独立的 Vite 根，各自有自己的 package.json 与 Docker 构建上下文，
 * 当前无法共享 npm 包。因此本文件在 `Web/src/services/` 与 `Admin/src/services/` 各存一份，
 * 且必须逐字节一致 —— 由 `scripts/check-mirrored-modules.mjs` 在 CI 中校验，漂移即构建失败。
 * 本文件不依赖任何应用内模块（不 import '@/…'、不 import '../…'），
 * 将来迁移到 workspace 共享包时整体搬走即可。
 *
 * 后端真实帧格式（LiuTech-AI/src/main/java/…/service/SseEmitterHelper.java）：
 *   event:事件名\n
 *   data:单行 JSON\n
 *   \n
 * 注意：后端**不发送** `contractVersion` envelope，前端保留 envelope 兼容分支是为了后端将来升级；
 * 当前线上走的是「裸 payload」形态（payload 直接就是事件数据）。
 *
 * @author 刘鑫
 */

/**
 * SSE 协议版本号
 *
 * 后端若在某天升级为 envelope 包装（{ contractVersion, event, payload }），
 * 会带上这个字段，前端据此决定是否剥壳。当前后端未发送该字段。
 */
export const CONTRACT_VERSION = 1

/**
 * SSE Envelope 根结构（后端 contractVersion=1 时的外层包装）
 */
export interface SseEnvelope<T = unknown> {
  /** 协议版本，目前恒为 1 */
  contractVersion: number
  /** 事件名，与 SSE 帧的 event 字段一致 */
  event: string
  /** 服务端任务 id（envelope 形态下存在） */
  taskId?: number
  /** 会话 id（envelope 形态下存在） */
  conversationId?: number
  /** 服务端时间戳（envelope 形态下存在） */
  timestamp?: string
  /** 真正的业务负载 */
  payload: T
}

/**
 * 解析后的单个 SSE 事件
 */
export interface ParsedSseEvent {
  /** 事件名，来自 SSE 帧的 event 字段（缺省为空字符串） */
  event: string
  /**
   * 业务负载
   *
   * 已经剥掉 envelope：裸 payload 形态下是服务端原始对象，
   * envelope 形态下是 envelope.payload。调用方不需要关心协议形态。
   */
  payload: unknown
  /** envelope 原始对象；裸 payload 形态下为 null */
  envelope: SseEnvelope | null
}

/**
 * 单帧解析结果
 *
 * 刻意区分「空帧」与「坏帧」：空帧（心跳分隔、末尾空行）直接忽略，
 * 坏帧要交给调用方决定是记日志、上报还是中断 —— 解析层不替调用方做决定。
 */
export type SseParseOutcome =
  | { status: 'ok'; event: ParsedSseEvent }
  | { status: 'empty' }
  | { status: 'invalid'; rawData: string; error: unknown }

/**
 * 流式读取回调集合
 */
export interface SseStreamHandlers {
  /** 每解析出一个事件调用一次 */
  onEvent: (event: ParsedSseEvent) => void
  /**
   * 单帧解析失败（JSON 非法）时调用
   *
   * 不传则静默跳过该帧：一条坏帧不应该让整条流停摆。
   */
  onParseError?: (rawData: string, error: unknown) => void
}

/**
 * 判断一个值是否为 envelope 结构
 *
 * @param value 待判断的值
 * @returns 是否为 contractVersion 匹配的 envelope
 */
export function isSseEnvelope(value: unknown): value is SseEnvelope {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as SseEnvelope
  return candidate.contractVersion === CONTRACT_VERSION && typeof candidate.event === 'string'
}

/**
 * 解析一帧 SSE 文本
 *
 * 处理规则（对齐 WHATWG SSE 规范中本项目用到的部分）：
 * - 以空行分隔的帧由调用方切分，这里只处理单帧内部的多行；
 * - `event:` 取事件名，`data:` 可多行（按行拼接，行间用 \n 还原）；
 * - 以 `:` 开头的是注释行，`id:` / `retry:` 等字段当前用不到，一律忽略；
 * - `data` 为空 → empty；JSON 解析失败 → invalid，不抛异常。
 *
 * @param eventText 单帧文本（不含分隔用的空行）
 * @returns ok / empty / invalid 三种结果
 */
export function parseSseEventText(eventText: string): SseParseOutcome {
  const lines = eventText.split(/\r?\n/)
  let eventName = ''
  const dataLines: string[] = []

  for (const line of lines) {
    // 空行与注释行（以冒号开头）不携带数据
    if (line === '' || line.startsWith(':')) continue

    if (line.startsWith('event:')) {
      eventName = line.substring(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.substring(5).trim())
    }
    // 其它字段（id:、retry:）本项目未使用，忽略
  }

  if (dataLines.length === 0) {
    return { status: 'empty' }
  }

  const rawData = dataLines.join('\n')

  let parsed: unknown
  try {
    parsed = JSON.parse(rawData)
  } catch (error) {
    return { status: 'invalid', rawData, error }
  }

  // envelope 形态：剥壳后把内部 payload 作为业务负载
  if (isSseEnvelope(parsed)) {
    return {
      status: 'ok',
      event: {
        event: parsed.event,
        payload: parsed.payload,
        envelope: parsed
      }
    }
  }

  // 裸 payload 形态（当前线上形态）：事件名取自 event: 行
  return {
    status: 'ok',
    event: {
      event: eventName,
      payload: parsed,
      envelope: null
    }
  }
}

/**
 * 持续读取响应体，按帧解析并回调
 *
 * 只负责「读流 + 切帧 + 解析」，不碰 HTTP 错误、不碰重连、不碰事件分发：
 * 这些属于各调用方的业务语义（看板娘要重连，写作助手不要）。
 *
 * @param body 响应体可读流（调用方需自行确认 response.body 非空）
 * @param handlers 事件回调与可选的解析失败回调
 * @returns 流读取完毕（或被取消、出错）后 resolve / reject
 */
export async function readSseStream(
  body: ReadableStream<Uint8Array>,
  handlers: SseStreamHandlers
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  /**
   * 处理缓冲区里已经完整的帧，保留最后一段（可能被网络从中间切断）
   */
  const drainBuffer = (flush: boolean): void => {
    // 帧之间用空行分隔，兼容 \n\n 与 \r\n\r\n
    const frames = buffer.split(/\r?\n\r?\n/)
    // 最后一段通常是不完整的帧，留到下次；流结束时才当作完整帧处理
    buffer = flush ? '' : (frames.pop() ?? '')

    for (const frame of frames) {
      if (frame.trim() === '') continue
      const outcome = parseSseEventText(frame)
      if (outcome.status === 'ok') {
        handlers.onEvent(outcome.event)
      } else if (outcome.status === 'invalid') {
        handlers.onParseError?.(outcome.rawData, outcome.error)
      }
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      // stream: true 保证多字节字符被网络切断时不会被解码成乱码
      buffer += decoder.decode(value, { stream: true })
      drainBuffer(false)
    }

    // 冲刷解码器里可能残留的半个字符，再处理缓冲区里的最后一帧
    buffer += decoder.decode()
    drainBuffer(true)
  } finally {
    // 释放读锁，避免调用方复用响应体时报 "locked to a reader"；
    // 取消场景下锁可能已失效，这里的失败不影响主流程，因此单独兜住。
    try {
      reader.releaseLock()
    } catch {
      // ignore
    }
  }
}
