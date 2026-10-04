# SSE 流式与心跳机制

> 本文描述看板娘和写作助手的前端事件协议、成功与失败终态、请求取消和心跳保活。服务端模型预算与生命周期见[AI 服务总览](../../后端/AI服务/总览.md)。

## 文件地图

| 文件 | 职责 |
| --- | --- |
| [`Web/src/services/sse.ts`](../../../../Web/src/services/sse.ts) | `readSseStream` 读流、分帧；`parseSseEventText` 解析 JSON 与 envelope |
| [`Web/src/services/aiStream.ts`](../../../../Web/src/services/aiStream.ts) | 看板娘事件分发、错误提示与 `AbortController` |
| [`Web/src/services/writingStream.ts`](../../../../Web/src/services/writingStream.ts) | 写作事件、HTTP 错误与成功/错误终态；Admin 同名镜像 |
| [`Web/src/services/writingReview.ts`](../../../../Web/src/services/writingReview.ts) | 写作暂存、成功标记与采纳守卫；Admin 同名镜像 |
| [`Web/src/stores/chat.ts`](../../../../Web/src/stores/chat.ts) | 消息与音频消费、身份代次及旧响应隔离 |
| [`LiuTech-AI/StreamingChatService.java`](../../../../LiuTech-AI/src/main/java/chat/liuxin/ai/service/StreamingChatService.java) | SSE 通道、心跳、上游订阅与收尾 |
| [`nginx/conf.d/ai-proxy.include`](../../../../nginx/conf.d/ai-proxy.include) | AI 请求反向代理 |

## 连接与分帧

```text
浏览器 fetch POST
  → /ai/chat/stream 或 /ai/writing/stream
  → nginx → AI 服务 → 上游模型
  ← text/event-stream，逐帧到达
  → readSseStream → parseSseEventText → 业务客户端
```

主站经过 CDN，Admin 使用自己的入口；部署拓扑与代理约束见[部署运维](../../运维/部署运维/总览.md)。浏览器使用 `fetch`，因为请求需要 POST、JSON 请求体和可选 `Authorization` 头。

服务端发送裸 JSON payload，每条事件用空行分隔：

```text
event: start
data: {"conversationId":7,"model":"configured-model","mode":"user"}

event: data
data: {"content":"你好","conversationId":7}

event: complete
data: {"conversationId":7,"responseLength":2,"mode":"user"}
```

[`sse.ts:181`](../../../../Web/src/services/sse.ts) 用 `TextDecoder(..., { stream: true })` 保留半个 UTF-8 字符，兼容 LF/CRLF 帧边界，缓存半帧直到下一块或 EOF。多行 `data:` 合并后解析。坏 JSON 帧返回 `invalid`，客户端跳过并留痕；若关键终态因此缺失，EOF 会被判为未完整结束。

解析器也接受 `contractVersion=1` envelope 并剥壳；业务客户端收到的都是解析后的 payload，不能各自重写分帧。

## 事件契约

| 事件 | 消费方 | 前端行为 |
| --- | --- | --- |
| `start` | 聊天、写作 | 携带 `conversationId`、本轮真实 `model` 与 `mode`；聊天建立会话，写作显示实际模型 |
| `data` | 聊天、写作 | 聊天追加回答；写作追加生成文本，不写入原稿 |
| `article-results` | 聊天、写作 | 展示已返回的文章列表和阅读入口 |
| `tool-start` / `tool-result` | 写作 | 展示真实工具调用、结果与耗时 |
| `field-update` | 写作 | 聚合到待采纳预览，不能触发即时覆盖表单 |
| `avatar-cue` | 聊天 | 表情入队，解除思考状态 |
| `audio` / `audio-skip` | 聊天 | 对应 seq 的音频或跳过标记入队 |
| `audio-complete` | 聊天 | 解除等待音频状态 |
| `heartbeat` | 传输保活 | 聊天透传后不渲染；写作客户端忽略 |
| `complete` | 聊天、写作 | 本轮模型内容完整成功；写作进入可采纳状态 |
| `error` | 聊天、写作 | 展示 `message` 或 `error` 文案；写作不可采纳 |

`complete` 不代表文章已经保存或发布；写作持久化仍由文章编辑器执行。看板娘收到文本 `complete` 后继续读取音频事件，文本结束与音频队列结束不同，详见[TTS与表情](TTS与表情.md)。

## 成功、断流与截断

[`writingStream.ts:264`](../../../../Web/src/services/writingStream.ts) 区分三类结果：

| 结果 | 客户端处理 | 写作原稿 |
| --- | --- | --- |
| 收到 `complete`，且此前无错误或取消 | 标记成功，结束生成后可明确采纳 | 采纳前保持原稿 |
| 收到 `error`，包括输出达到上限或正文校验失败 | 调用 `onError`，后续字段和终态不再分发 | 部分结果仅供查看，不可应用 |
| EOF 未收到 `complete/error`，或读取失败 | 报告连接中断；不能伪造 `onComplete` | 不可应用 |

HTTP 非 2xx 或无 body 会 reject；403/429 有身份/限流文案，其余优先采用后端 JSON `message`。组件在 `finally` 释放 loading，不靠伪造成功事件收尾。

看板娘断流时保留已收到的回答并显示错误；尚无正文时移除空占位。写作的完整采纳、草稿冲突与整轮撤销见[后台写作助手](../后台管理/总览.md#写作助手)。

## 请求取消与手动重试

- 聊天每次只提交一次 POST。没有续传或幂等协议，不发送 `lastSeq`，也不自动重复原问题。
- `AiStream.cancel()` 中止当前 fetch；旧请求的 `finally` 只清理自己的 controller，不清理新请求。
- 写作组件每轮创建 `AbortController`，传给 `streamWritingAssistant`；“停止生成”和组件卸载都 abort。取消不允许采纳，即使预览已有部分字段。
- 身份切换同时取消 SSE、普通 HTTP 和 TTS；旧回调再通过身份代次守卫隔离。身份恢复规则见[Markdown与会话](Markdown与会话.md#身份与持久化)。
- 重试必须由用户发起新请求，不把重复 POST 当作断点续传。前端取消与服务端停止上游订阅是各自的生命周期出口，服务端规则见[AI 服务总览](../../后端/AI服务/总览.md)。

## 心跳保活

`StreamingChatService` 的聊天和写作入口均在 `start` 后启动心跳，首个延迟与发送间隔均为 15 秒，事件携带 `conversationId` 与 `timestamp`。终止通道时停止心跳任务。

```text
上游模型思考或工具执行，没有正文数据
  → AI 服务定时发送 heartbeat
  → CDN / nginx / 浏览器链路保持数据流动
  → 前端读取但不加入回答或写作预览
```

心跳只处理连接的空闲保活，不能证明模型成功，也不能修复上游输出截断。间隔应短于实际部署链路中最严格的空闲超时；更换代理/CDN 后需要重新核对。代理的配置边界统一见[网络封装](../../设计/网络封装/总览.md#陷阱与约束)。

## 分层排查与回归

1. 浏览器 Network 区分 HTTP 失败与“200 响应已建立但 body 中断”，检查是否收到 `complete/error`。
2. 对比源站直连与经代理/CDN的同一个入口，核对 heartbeat 是否持续到达。
3. 对照 nginx 输出字节与 AI 服务终态日志，确定中断发生在哪一层；不要用单次请求推断模型参数与瞬时故障的因果。
4. 查看错误是否为输入预算、输出截断、正文校验、超时或用户取消；配置和服务端判断以[AI 服务文档](../../后端/AI服务/总览.md)为准。

受控协议回归见[sseStream.test.ts](../../../../Web/src/__tests__/sseStream.test.ts)；消息状态见[chatStream.test.ts](../../../../Web/src/__tests__/chatStream.test.ts)；写作采纳和取消见[writingReview.test.ts](../../../../Web/src/__tests__/writingReview.test.ts)、[writingAssistant.test.ts](../../../../Web/src/__tests__/writingAssistant.test.ts)。这些测试不证明实际 CDN、模型或音频服务的可用性。
