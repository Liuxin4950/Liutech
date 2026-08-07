/**
 * AI 服务共享类型定义
 * 从 ai.ts 提取，解决 ai.ts <-> aiStream.ts 循环依赖
 */

/**
 * AI聊天请求接口
 */
export interface AiChatRequest {
  message: string
  /** 前端上下文，便于后端提示词决策，例如 { page: 'post-detail', articleId: 123 } */
  context?: Record<string, any>
  conversationId?: number
  tempMessages?: Array<{
    role: 'user' | 'assistant' | 'system'
    content: string
  }>
  /** 温度参数，控制回复的随机性 */
  temperature?: number
  /** 最大token数 */
  maxTokens?: number
  /**
   * 聊天类型
   * - 'chat': 看板娘聊天（注册 BlogMcpTools，可搜索/推荐文章）
   * - 'writing': 写作助手（注册 WritingTools，可调用分类/标签工具）
   */
  chatType?: 'chat' | 'writing'
  /**
   * 是否启用语音推理（由前端开关决定）
   * - true：后端会尝试把流式文本分段并触发 TTS，额外推送 audio 事件
   * - false：只返回文本，不做 TTS 推理
   */
  ttsEnabled?: boolean

  /**
   * 断线重连时携带的最后收到的事件 seq，供后端去重/续传。
   * 仅在 SSE 重连场景下由 AiStream 内部填充，业务调用无需传入。
   */
  lastSeq?: number
}
