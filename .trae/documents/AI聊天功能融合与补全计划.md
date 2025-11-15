## 融合度评估

- 后端 `AiChatServiceImpl.processChat` 与前端 `AiChat.vue` 的普通聊天已打通：`POST /ai/chat`
  返回 `ChatResponse(message/action/metadata/conversationId)`，前端按动作 `navigate|interact|search|show|none`
  分发，路由与操作映射一致。
- 上下文融合良好：前端在文章详情页传入 `{ page:'post-detail', postId }`，后端据此通过 `GET /posts/{id}` 拉取文章并增强提示词，且
  ID 字段兼容 `postId|articleId`。
- 安全门控到位：后端仅在“明确指令”触发动作，前端对点赞/收藏再二次校验（正则意图保护）。
- 会话与记忆：后端自动创建会话并落库消息，提供会话列表与会话消息接口；前端有完整聊天页面与聊天历史页面。
- 健康检查：前端调用 `GET /ai/status`，后端响应正常；清空记忆接口存在。

## 缺失与可补充项

1. 流式聊天（SSE/Flux）未落地：后端未实现 `/ai/chat/stream`，前端无流式消费逻辑。
2. 前端交互 API 未接：`AiChat.vue` 的 `likePost/favoritePost` 仅日志，未调用后端 `POST /posts/{id}/like|favorite`。
3. 清空记忆未接线：`Ai.clearChatMemory()` 调用在组件中被注释，UI 仅本地清空。
4. 模型参数未透传：后端未使用 `ChatRequest.temperature|maxTokens`，`SiliconFlowChatClient` 固定模型/温度。
5. 错误处理一致化：前端按 HTTP 状态码分支，但后端统一返回 `ChatResponse.error(...)`；需统一前后端错误语义与展示。
6. 情绪展示：后端已返回 `emotion`，前端未显示，可用于增强 UX。
7. 状态心跳：60s 定时检查被注释，可开启并防抖。
8. 历史体验：`AiChat.vue` 不加载当前会话历史，可提供“展开最近 N 条”。

## 技术实现方案

### 后端

- 新增 `/ai/chat/stream`（SSE 或 Reactor Flux）：
    - 入口：`AiChatController.stream(ChatRequest)` → 返回 `SseEmitter` 或 `Flux<ServerSentEvent<String>>`。
    - 过程：按“user→start→data→complete|error”事件序列推送；仅在 `complete|error` 时落库 assistant，保持现有入库策略。
    - 结构化输出：沿用 `ensureValidStructuredOutput`，对不合规 JSON 二次修复；动作门控保持一致。
- 透传模型参数：
    - 在 `AiChatServiceImpl` 将 `request.model|temperature|maxTokens`
      传入 `SiliconFlowChatClient.chat(messages, options)`；客户端实现支持可选覆盖默认模型与温度。
- 日志与稳健性：
    - 统一使用 `log.debug/info/error`（移除 `System.out.println`），对 `fetchArticleContent` 增加降级与超时控制。

### 前端

- 服务层：在 `services/ai.ts` 增加 `chatStream(req)`，基于 `EventSource` 或 `fetch+ReadableStream`；保留现有 `chat()`。
- 组件：
    - `AiChat.vue` 接入流式：
        - 发送时创建一条空 AI 消息，增量填充 `content`，用 `isStreaming` 指示。
        - 收到 `complete` 后再分发动作，保持当前行为一致。
    - 对接交互 API：
        - `likePost(postId)` 与 `favoritePost(postId)` 调用 `PostService.likePost|favoritePost`，失败时回滚并提示。
    - 清空记忆：
        - 启用 `Ai.clearChatMemory()` 调用后端，成功后本地清空并重置会话态。
    - 情绪展示：
        - 在消息气泡或头部展示 `resp.emotion` 的图标/色彩（如 `happy|neutral|sad`）。
    - 状态心跳：
        - 恢复 60s 心跳检查，网络离线时暂停；提供手动刷新按钮（保留现有）。
    - 可选：在弹窗加入“加载最近历史”按钮，调用 `ConversationService.messages(conversationId)`。

## 验证与风险

- 兼容性：SSE 在企业代理环境下可能被中断，提供回退到普通 `chat()`；跨域需确保 `@CrossOrigin`、Nginx
  反代正确设置 `Cache-Control` 与 `Connection`。
- 一致性：动作触发仅在 `complete` 后执行，避免半截文本触发误操作；前端交互失败时要回滚 store 状态。
- 压测：对流式与普通模式分别压测，确认 `RetryTemplate` 的退避策略与限流；记录耗时与响应长度指标。

## 交付项（文件级）

- 后端：
    - `AiChatController`：新增 `@GetMapping("/chat/stream")` 与方法实现；
    - `AiChatServiceImpl`：支持参数透传与流式过程；
    - `SiliconFlowChatClient`：支持可选 `model/temperature/maxTokens`；
- 前端：
    - `services/ai.ts`：新增 `chatStream`；
    - `components/AiChat.vue`：流式渲染、情绪展示、接线清空记忆与交互 API；
    - `services/post.ts`：被调用，无需改动（仅在组件接入）。

请确认以上方案，收到确认后我将按上述清单逐项落地实现，并在每一步完成后提供可验证结果与必要演示链接。
