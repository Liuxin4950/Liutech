# LiuTech AI 接口文档

> 核对日期：2026-09-09。以当前工作树的 Controller、DTO、Service、Mapper 和安全配置为依据。
> 本文描述现有 HTTP 接口及 SSE 协议；示例 ID、文本和模型配置仅用于说明格式。线上配置可能不同。

## 1. 接入与认证

- 本地直连服务：`http://localhost:8081`；下文路径已包含 `/ai`，不要重复拼接。
- 经仓库 Nginx 配置访问：同源 `/ai/**` 原样转发到 AI 服务；模型管理使用 `/ai/admin/models/**`。
- JSON 请求发送 `Content-Type: application/json`；流式接口发送 `Accept: text/event-stream`，普通接口不要强制使用此 Accept。
- 路径目前没有 `/v1` 或 `/v2` 前缀。四个聊天/写作端点返回 `X-LiuTech-AI-Route: legacy-chat`，这只是现有路由标记。
- AI 服务不签发登录 Token。向主后端 `POST /user/login` 发送 `{"username":"你的用户名","password":"你的密码"}`，从主后端 `{code,message,data:{token}}` 响应中取 `data.token`。主后端本地端口为 8080，经 Nginx 的路径为 `/api/user/login`。

需要认证时携带：

```http
Authorization: Bearer <token>
```

| 权限 | 接口 |
| --- | --- |
| 游客可访问 | 聊天 `/ai/chat`、`/ai/chat/stream`，状态 `/ai/status`，公开模型 `/ai/models/**` |
| 登录用户 | 历史记录、清空记忆、会话管理 |
| 管理员 | 写作 `/ai/writing`、`/ai/writing/stream`，模型管理 `/ai/admin/models/**` |

服务端先验证 JWT 签名，再通过主后端 `/user/current` 校验用户身份与当前角色，用户状态缓存 60 秒。两服务的 `JWT_SECRET` 必须一致。无效 Token 在公开聊天端点会按游客处理；在受保护端点返回 401。不能用请求里的用户 ID 或自称管理员替代认证。

**响应没有单一成功包裹格式**：聊天返回 `ChatResponse`，部分列表直接返回数组，模型名和状态直接返回文本，部分管理操作成功为空体。不要对所有成功响应统一读取 `data` 或统一调用 `response.json()`。错误处理见第 7 节。

## 2. 聊天与写作

### 2.1 路由与会话行为

| 方法与路径 | 响应 | 行为 |
| --- | --- | --- |
| `POST /ai/chat` | JSON `ChatResponse` | 看板娘聊天，一次返回完整文本 |
| `POST /ai/chat/stream` | SSE | 看板娘聊天，逐步返回文本及可选语音 |
| `POST /ai/writing` | JSON `ChatResponse` | 管理员写作，一次返回完整文本 |
| `POST /ai/writing/stream` | SSE | 管理员写作，支持字段回写和工具进度 |

- 游客聊天不创建会话、不保存消息；`conversationId` 忽略，使用 `tempMessages` 维持临时上下文。
- 登录聊天：不传 `conversationId` 则自动创建会话；传入时校验会话属主，并读取服务端历史。用户消息和助手回复会持久化。
- 写作不保存会话或聊天消息，多轮上下文仍由 `tempMessages` 提供。传入的 `conversationId` 可被回传，但不代表新建了持久化会话。
- 模式由 URL 决定，携带 `draft` 不会让聊天端点自动切换为写作。写作返回内容用于前端编辑器，不执行文章保存或发布。

### 2.2 公共请求体

四个端点使用同一个 `ChatRequest`：

| 字段 | 类型 | 必填 | 当前约束与用途 |
| --- | --- | --- | --- |
| `message` | string | 是 | 非空白，最多 20000 字符 |
| `temperature` | number | 否 | 合法范围 0～1；越界时忽略该值并读取配置 |
| `maxTokens` | integer | 否 | 正数且不超过服务端策略上限；越界时忽略，仓库配置上限为 8192 |
| `context` | object | 否 | 页面与写作上下文，见下文 |
| `tempMessages` | object[] | 否 | 游客/写作历史，按输入顺序取最后 7 条；登录聊天不使用 |
| `conversationId` | integer / null | 否 | 登录聊天的会话 ID；省略时新建 |
| `ttsEnabled` | boolean | 否 | 仅流式接口且显式为 `true` 才尝试语音合成；默认关闭，同步接口不生成音频 |
| `draft` | object | 否 | 写作时的草稿快照，聊天端点不注入此快照 |

`tempMessages[]` 每项为 `{role,content}`：两者都必须非空白，`content` 最多 20000 字符。建议仅传 `user` / `assistant`；服务端将规范化后的 `assistant` 作为助手历史，其他角色均作为用户历史，`system` 不会获得系统指令权限。

**模型选择**完全由服务端决定：优先启用的数据库默认模型，否则回退应用配置。请求 DTO 没有 `model`、`mode`、`chatType`、`enableTts`、`lastSeq` 字段，不应依赖这些字段产生效果。`temperature` / `maxTokens` 的常规优先级为合法请求值 → 启用模型的数据库配置 → 底层默认值。

写作流式额外处理参数：温度未解析到值时使用 0.3；`maxTokens` 已有值时取它与 `spring.ai.writing-max-tokens` 的较小值，否则使用该写作配置（仓库为 32768）。同步写作没有这层额外处理，不能假定两种写作接口的默认输出上限完全相同。

**已使用的 context 字段**：

| 字段 | 当前含义 |
| --- | --- |
| `page: "post-detail"` + `postId` | 服务端读取对应文章作为上下文；不是旧例中的 `articleId` |
| `page: "home"` / `"about"` | 注入站点资料；问题含站点相关关键词时也可能注入 |
| `recommendations` | 推荐历史数组；取最近有效组的 `type/reason/posts[{id,title}]`，最多引用 3 篇，用于后续追问 |
| `requestedFields` | 写作草稿上下文选取，支持 `title/summary/content/category/tags`（也识别 `tag`）；空、含 `check` 或含完整五类字段时注入全部 |

`context.requestedFields` 控制送入模型的草稿资料，**不是服务端对返回字段的权限限制**。上下文不能声明或更改用户身份。

**draft 字段**均可选：`postId: integer`、`title: string`、`content: string`、`summary: string`、`categoryId: integer`、`tagIds: integer[]`、`status: string`。`content` 是编辑器当前正文；注入提示词时只取前 6000 字符。请求是 `content`，字段回写事件是 `contentHtml`，两者不要混用。

聊天示例（登录时可另传 `conversationId`）：

```json
{
  "message": "请解释这篇文章的核心思路",
  "context": {"page": "post-detail", "postId": 123},
  "temperature": 0.6,
  "maxTokens": 4096,
  "ttsEnabled": false
}
```

写作示例（发送到 `/ai/writing/stream`，需管理员 Token）：

```json
{
  "message": "根据当前正文改一个更准确的标题",
  "ttsEnabled": false,
  "context": {"requestedFields": ["title", "content"]},
  "draft": {
    "postId": 123,
    "title": "原始标题",
    "content": "<p>当前正在编辑的文章正文。</p>",
    "summary": "原摘要",
    "categoryId": 1,
    "tagIds": [2, 3],
    "status": "draft"
  },
  "tempMessages": [
    {"role": "user", "content": "标题保持简洁，不用夸张表达"},
    {"role": "assistant", "content": "会按这个要求修改。"}
  ]
}
```

### 2.3 同步响应

成功响应示例：

```json
{
  "success": true,
  "message": "你好！",
  "model": "deepseek-ai/DeepSeek-V3.2",
  "processingTime": 1250,
  "responseLength": 3,
  "emotion": null,
  "action": null,
  "conversationId": 123,
  "mode": "user"
}
```

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `success` | boolean | 本次业务是否成功，不能只检查 HTTP 状态 |
| `message` | string | 完整回复或错误说明 |
| `model` | string / null | 实际使用的模型名 |
| `processingTime` | integer / null | 毫秒 |
| `responseLength` | integer / null | 回复字符数，不是 Token 数 |
| `conversationId` | integer / null | 游客为 null；登录聊天为实际会话 ID |
| `mode` | string / null | `guest` 或 `user`；同步写作成功时也为 `user` |
| `emotion`、`action` | string / null | 保留字段，当前成功响应构造器不赋值 |

同步写作不提供 SSE 的 `field-update` 结构化回写。写作服务内部捕获到的失败返回 **HTTP 200 + `success:false`**，并附 `message/model/processingTime`；请求校验、认证失败仍使用对应 HTTP 错误。

## 3. SSE 流式协议

### 3.1 线格式与生命周期

流式接口使用 **POST + JSON 请求体**，返回 `text/event-stream`。`event:` 是事件名，`data:` 是该事件的 JSON 负载；没有额外的 `{event,data}` 或 `{payload,...}` 外层。

```text
event: start
data: {"conversationId":123,"model":"deepseek-ai/DeepSeek-V3.2","mode":"user"}

event: data
data: {"content":"你好！","conversationId":123}

event: article-results
data: {"items":[],"reason":"我找到这些文章，可以直接点开阅读。"}

event: complete
data: {"conversationId":123,"responseLength":3,"mode":"user","ttsEnabled":false}

```

以上仅展示基本线格式，分段时还可能穿插表情、工具、字段或音频事件。每个事件以空行结束；网络分片与事件边界无关。客户端应累积缓冲区，解析完整事件后再 `JSON.parse`。

### 3.2 事件表

下表列出 `data:` 的实际字段；`?` 表示可缺省，标为可空的值可能为 null。

| event | JSON 负载 | 含义 |
| --- | --- | --- |
| `start` | `{conversationId,model,mode}` | 开始；聊天 mode 为 `guest/user`，写作为 `writing`；conversationId 可空 |
| `heartbeat` | `{conversationId,timestamp}` | 首次及后续间隔均为 15 秒；timestamp 为 Unix 毫秒 |
| `data` | `{content,conversationId}` | content 为文本增量，按顺序拼接；没有 seq |
| `avatar-cue` | `{seq,conversationId,expression,motion,intensity,durationMs,text}` | Live2D 表情提示；expression 为字符串，intensity 为数值，durationMs 为毫秒；当前 motion 为 null |
| `audio` | `{seq,text,audioUrl,conversationId}` | 单段音频地址；按 seq 对齐文本与播放次序 |
| `audio-skip` | `{seq,text,reason,conversationId}` | 该段合成失败/无音频；reason 为 `empty-audio-url` 或异常类名 |
| `tool-start` | `{toolName,displayName,inputSummary?}` | 写作工具开始，字段为字符串 |
| `tool-result` | `{toolName,displayName,durationMs,success,resultSummary?,errorMessage?}` | 写作工具完成；耗时毫秒，success 为布尔值，成功/失败分别带摘要/错误 |
| `field-update` | 第 3.3 节的字段对象 | 写作字段回写，不含 conversationId |
| `article-results` | `{items,reason}` | 正常文本完成前发送，items 可以为空 |
| `complete` | `{conversationId,responseLength,mode,ttsEnabled}` | **文本完成**；mode 按登录态返回 `guest/user`，写作这里也为 `user` |
| `audio-complete` | `{conversationId,timedOut,segments}` | 存在 TTS 任务时的音频收尾；timedOut 为布尔值，segments 为本轮已编号文本段总数 |
| `error` | `{conversationId,error}` | 本轮失败；读取 `error` 字符串，不是 `message` 或 `code` |

`article-results` 当前从回复中的 `[标题](/post/ID)` 提取链接，按 ID 去重，最多 8 篇。`items` 是 `PostSummaryDTO[]`，当前仅填充 `id` 和 `title`，其他 DTO 字段可能为空；不能假定附带完整文章资料或 URL，详情链接可由 ID 组成 `/post/{id}`。

### 3.3 写作字段回写

`field-update` 仅包含本次非 null 字段：

| 字段 | 类型 | 用法 |
| --- | --- | --- |
| `title`、`summary` | string | 替换对应字段 |
| `contentHtml` | string | 累计正文 HTML 快照，覆盖当前正文，不要当文本增量拼接 |
| `categoryId`、`categoryName` | integer、string | 已有分类信息 |
| `tagIds`、`tagNames` | integer[]、string[] | 已有标签信息 |
| `suggestedCategoryName`、`suggestedTagNames` | string、string[] | 建议新分类/标签名称，不表示已经创建 |

前端按字段合并，缺省字段保留。`applyArticleUpdate` 工具主要返回标题、摘要、分类和标签；正文从模型输出的 HTML 文本中提取并可能多次推送快照。并非每轮一定有字段回写，也不保证一次事件包含全部字段。

`tool-start` / `tool-result` 和 `field-update` 用于展示进度、更新编辑状态；工具成功不代表文章已保存或发布。

### 3.4 语音、结束与中断

- `ttsEnabled=false` 仍可收到 `avatar-cue`。`seq` 每轮从 1 开始，用于表情/音频分段对齐，不是所有 SSE 事件的全局序号。
- 开启 TTS 后，音频可能在 `complete` 前或后到达，也可能乱序；使用 `seq` 排序。纯符号等不可播报段可能直接跳过，不保证每个 seq 都有音频事件。
- `complete` 只结束文本。存在 TTS 任务时继续读取连接，等 `audio` / `audio-skip` 和 `audio-complete`；没有实际 TTS 任务时直接关闭，不发 `audio-complete`。
- `audio-complete.timedOut=true` 表示音频等待超时；`segments` 是文本段数，不是成功音频数量。单段 TTS 失败不使整轮文本失败。
- 仓库 `spring.ai.sse-timeout` 为 300000 毫秒。超时或网络中断不保证有 `error`；未收到 `complete` 就 EOF 应视作未完成，不能把连接关闭等同成功。
- 登录聊天的模型流错误会尝试保存部分回复（status=3）和错误占位，再发送 `error`；游客和写作不保存消息。客户端断开、超时或 I/O 错误不保证部分回复落库。
- 当前无取消、去重或断点续传 HTTP 接口，也未处理 `lastSeq` / `Last-Event-ID`。客户端 Abort 只能结束本地请求，不能保证上游生成停止。自动重发 POST 可能再次生成、落库，不应视为续传。

语音由 AI 服务调用主后端 `/tts/status` 和 `/tts/speech` 代理生成；内部使用 `X-TTS-Internal-Token`，其值来自两服务一致的 `TTS_PROXY_INTERNAL_TOKEN`，不要下发到浏览器。`audioUrl` 可能是完整 URL 或主后端相对路径（如 `/tts/audio/**`），应按实际后端/站点地址解析，不能一律拼在 8081 后。

### 3.5 fetch 客户端示例

原生 `EventSource` 不能按旧文档中的写法设置 POST、JSON body 和自定义 Authorization。下面用 `fetch` 读取完整事件，处理 UTF-8 跨分片、LF/CRLF、HTTP JSON 错误和未完成断流；不自动重试。此示例返回文本，音频播放和编辑器字段合并由 `onEvent` 处理。

```javascript
async function streamAi({
  baseUrl = 'http://localhost:8081',
  path = '/ai/chat/stream',
  token,
  request,
  signal,
  onEvent = () => {}
}) {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream'
  };
  if (token) headers.Authorization = 'Bearer ' + token;
  const response = await fetch(baseUrl + path, {
    method: 'POST', headers, body: JSON.stringify(request), signal
  });
  if (!response.ok) {
    const raw = await response.text();
    let message = raw;
    try { message = JSON.parse(raw).message || raw; } catch {}
    throw new Error(message || 'HTTP ' + response.status);
  }
  if (!response.headers.get('content-type')?.includes('text/event-stream')
      || !response.body) {
    throw new Error('响应不是 SSE 事件流');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '', text = '', textComplete = false;
  function dispatch(block) {
    let event = 'message';
    const data = [];
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (!data.length) return;
    const payload = JSON.parse(data.join('\n'));
    if (event === 'error') throw new Error(payload.error || 'AI 生成失败');
    if (event === 'data') text += payload.content || '';
    if (event === 'complete') textComplete = true;
    onEvent(event, payload);
  }
  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true });
      let boundary;
      while ((boundary = /\r?\n\r?\n/.exec(buffer)) !== null) {
        const block = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary[0].length);
        dispatch(block);
      }
      if (done) break; // complete 后继续读，保留后续音频事件
    }
    if (!textComplete) throw new Error('流已中断，未收到文本完成事件');
    return text;
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

const controller = new AbortController();
const reply = await streamAi({
  request: { message: '你好', ttsEnabled: false },
  signal: controller.signal,
  onEvent(event, payload) {
    if (event === 'data') console.log(payload.content);
    if (event === 'start') console.log('会话 ID：', payload.conversationId);
  }
});
console.log(reply);
// UI 的取消按钮可以调用 controller.abort()。
```

同源 Nginx 环境将 `baseUrl` 设为空字符串；写作改为 `path: '/ai/writing/stream'` 并提供管理员 `token`。示例不会因为 `complete` 提前关闭连接，但返回文本不代表所有音频都合成成功。

## 4. 历史记录与会话

### 4.1 全局历史和清空

| 方法与路径 | 参数 | 返回与行为 |
| --- | --- | --- |
| `GET /ai/chat/history` | query：`page=1`、`size=20` | 返回下方分页对象；跨当前用户所有会话，含归档会话，按 `createdAt DESC,id DESC` |
| `DELETE /ai/chat/memory` | 无请求体 | `ChatResponse`：`success=true,message="聊天记忆已清空"`；物理删除当前用户全部会话及消息，含归档会话 |

全局历史实际分页：`page<1` 修正为 1；`size<1` 修正为 20；`size>100` 限为 100。

```json
{
  "success": true,
  "message": null,
  "data": [],
  "page": 1,
  "size": 20,
  "total": 0,
  "totalPages": 0,
  "userId": "1001",
  "timestamp": 1788912000000
}
```

`totalPages=ceil(total/size)`，`timestamp` 为 Unix 毫秒。历史/清空方法内部捕获到的异常可能返回 HTTP 200 + `success:false`；客户端还需检查该字段。

### 4.2 会话接口

全部要求登录且只允许操作自己的会话；指定会话不存在返回 404，访问其他用户会话返回 403。`{id}` 为整数。创建/重命名用 query 参数（表单参数也由 `@RequestParam` 接收），不是 JSON 请求体；URL 中的标题应编码。

| 方法与路径 | 参数 | 返回与行为 |
| --- | --- | --- |
| `GET /ai/conversations` | query：`type?`、`page=1`、`size=20` | 裸 `AiConversation[]`；排除归档，`updatedAt DESC,id DESC` |
| `POST /ai/conversations` | query/form：`type?`、`title?` | `ChatResponse`：`success=true,message="会话创建成功",conversationId` |
| `GET /ai/conversations/{id}/messages` | query：`page=1`、`size=50` | 裸 `AiChatMessage[]`；`seqNo ASC,id ASC` |
| `PUT /ai/conversations/{id}/rename` | 必填 query/form：`title` | `ChatResponse`：`success=true,message="会话重命名成功"` |
| `PUT /ai/conversations/{id}/archive` | 无请求体 | `ChatResponse`：`success=true,message="会话已归档"`；置 status=9，保留消息 |
| `DELETE /ai/conversations/{id}` | 无请求体 | `ChatResponse`：`success=true,message="会话已删除"`；物理删除会话及其消息 |

**当前实现限制**：

- 会话列表与会话消息列表虽接收 `page/size`，当前 `MemoryService` 使用 `.last(false, "LIMIT ...")`，没有实际应用 SQL 分页；不能依赖 size 限制条数，也没有 total。全局 `/ai/chat/history` 不受此问题影响。
- `type` 在创建和列表中均未使用，不能用于区分聊天/写作或筛选。
- 创建仅在 `title` 缺省/null 时用“新会话”；空字符串不会自动替换。重命名未声明非空白/长度校验。
- 归档仅让会话从列表隐藏；属主仍可访问其消息。当前没有恢复归档接口。

### 4.3 数据字段

`AiConversation`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id`、`userId` | integer、string | 会话 ID、属主 ID |
| `title` | string | 标题 |
| `status` | integer | 新会话为 0，归档为 9 |
| `messageCount` | integer | 消息数，新建为 0 |
| `createdAt`、`updatedAt`、`lastMessageAt` | string / null | 日期时间，如 `2026-09-09T10:30:00`；新会话 lastMessageAt 可空 |

完整 `AiChatMessage` 字段为 `id,conversationId,userId,role,content,seqNo,model,tokens,metadata,status,createdAt,updatedAt`。ID/seqNo/tokens/status 为整数，userId 为字符串；metadata 是 JSON **字符串**，可空；时间为日期时间字符串。

`/ai/chat/history` 返回完整消息对象；`/ai/conversations/{id}/messages` 目前仅查询 `id/role/content/createdAt/seqNo`，其他字段可空，不要依赖其中的 model、tokens、conversationId 等必定有值。

当前正常写入主要为 `role=user/assistant`、`status=1`；异常/模型流中断使用 `status=3`。实体注释还列出 0/2，但当前写入流程未实现对应分类。历史查询不按 status 过滤。`tokens` 可空，不能直接据此作为准确计费用量。

## 5. 模型接口

### 5.1 公开查询

| 方法与路径 | 响应 |
| --- | --- |
| `GET /ai/models/default` | 裸文本模型名；无数据库默认时返回 Controller 固定兜底值 `deepseek-ai/DeepSeek-V3.2` |
| `GET /ai/models/enabled` | 裸 `ModelConfigDTO[]`；只含启用模型，按 `sortOrder ASC` |

公开列表用于展示，不能通过聊天请求选择其中任意模型。默认查询只按数据库默认标志读取；实际聊天模型策略还会检查启用状态，并从应用配置回退。两处回退来源不同，异常配置或修改应用默认值后，公开默认查询与实际所用模型可能不同。

`ModelConfigDTO` 示例（公开/管理列表使用相同 DTO）：

```json
{
  "id": 1,
  "modelName": "deepseek-ai/DeepSeek-V3.2",
  "displayName": "DeepSeek-V3.2",
  "provider": "siliconflow",
  "isEnabled": true,
  "isDefault": false,
  "sortOrder": 0,
  "maxTokens": 4096,
  "temperature": 0.6,
  "description": "模型说明"
}
```

DTO 不包含 API Key、baseUrl 或创建/更新时间。

### 5.2 管理接口

以下全部要求管理员。Controller 同时映射 `/admin/models` 与 `/ai/admin/models`；经站点代理时使用下表的 `/ai/admin/models`，避免进入主后端管理路由。

| 方法与路径 | 参数 | 成功响应 |
| --- | --- | --- |
| `GET /ai/admin/models/list` | 无 | 裸 DTO 数组，含禁用模型；未保证排序 |
| `GET /ai/admin/models/enabled` | 无 | 裸启用 DTO 数组 |
| `GET /ai/admin/models/default` | 无 | DTO 对象或 null；不是公开接口的模型名文本，也不做配置回退 |
| `GET /ai/admin/models/{id}` | path：整数 id | DTO |
| `POST /ai/admin/models` | JSON `ModelConfigRequest` | 新建 DTO |
| `PUT /ai/admin/models/{id}` | path id + JSON `ModelConfigRequest` | 更新后的 DTO |
| `DELETE /ai/admin/models/{id}` | path id | HTTP 200，空响应体 |
| `PUT /ai/admin/models/{id}/default` | path id | HTTP 200，空响应体 |
| `PUT /ai/admin/models/{id}/toggle` | path id + 必填 query `enabled=true/false` | HTTP 200，空响应体 |
| `GET /ai/admin/models/usage/today` | 无 | 裸 `[{model,usageCount}]` 数组 |

`ModelConfigRequest`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `modelName`、`displayName`、`provider` | string | 是 | 非空白；provider 未做枚举校验 |
| `isEnabled` | boolean | 是 | 不允许 null |
| `sortOrder` | integer | 否 | 新增/更新省略或 null 都设为 0 |
| `maxTokens` | integer | 否 | DTO 未设数值范围校验；聊天时仍受模型策略约束 |
| `temperature` | number | 否 | DTO 未设数值范围校验 |
| `description` | string | 否 | 描述 |

新增/更新可以使用上方 DTO 示例去掉 `id/isDefault` 后的 JSON。PUT 仍要求四个必填字段，不能当 PATCH；不要依赖省略/null 可选字段清空原值。

- 新建模型默认 `isDefault=false`，设置默认走专门接口；目标必须启用，设置时会清除其他默认标记。
- 删除默认模型、通过 toggle 禁用默认模型会失败；普通 PUT 更新路径没有相同的禁用保护，不能假定默认模型永远处于启用状态。
- 不存在 ID、模型名重复等业务请求异常统一返回第 7 节的 HTTP 400 格式，外部 message 通常为“输入内容有误，请检查”，不是服务内部原因原文。
- `usage/today` 按数据库 `CURDATE()` 统计当日已落库且 model 非空的 assistant 消息数，按 usageCount 倒序；不包含未落库的游客/写作请求，未过滤异常状态，删除历史也会改变统计。它不是所有 API 调用数或 Token 数。
- 当前没有模型测试、刷新清单、连通性测试 HTTP 接口。

## 6. 状态接口

`GET /ai/status` 公开访问，返回裸文本：

```text
服务可用，用户ID: null
```

Token 被认可时 null 替换为当前用户 ID。此接口只说明 AI Web 服务可响应及当前认证状态，不执行模型推理或 TTS 探测。Spring Boot 另暴露 `GET /actuator/health`，属于直连服务的健康检查端点，不应自行拼成 `/ai/actuator/health`。

## 7. 错误与限流

普通异常/安全拦截通常返回对应 HTTP 状态及：

```json
{"success": false, "message": "未登录或Token已失效", "code": 401}
```

| HTTP 状态 | 当前场景 |
| --- | --- |
| 400 | Bean Validation 失败（message 为首条字段错误）；AI RequestException（message 为“输入内容有误，请检查”） |
| 401 | 受保护端点未登录或 Token 未被认可 |
| 403 | 非管理员访问管理功能，或访问其他用户会话 |
| 404 | 指定会话不存在 |
| 408 | 被归类为 AI 超时异常 |
| 429 | 聊天/写作请求超过本地角色限流 |
| 500 | 未细分 AI 异常或普通系统异常 |
| 503 | 被归类为 AI 连接失败或模型不可用 |

不要假定所有参数格式/绑定错误都有专门的 400 映射；当前全局处理器只对明确处理的异常保证上述结果。

**429 是独立格式，code 为字符串**：

```json
{"success": false, "message": "请求过于频繁，请稍后再试", "code": "RATE_LIMITED"}
```

仓库默认限流开启，60 秒窗口内游客 20 次、登录用户 60 次、管理员 120 次。计数在 AI 进程内：登录按用户身份，游客按客户端 IP；四个聊天/写作端点共用身份配额，模型/状态/会话查询不在此拦截器范围。运行时配置可覆盖这些数值。

客户端处理顺序：先检查 HTTP 状态，再区分文本/JSON/SSE；JSON 业务响应还需检查 `success`，已建立的 SSE 则处理 `error` 事件和无完成事件的断流。写作、历史、清空等内部捕获分支可在 HTTP 200 返回业务失败，错误 code 也不总是数字。

## 8. 维护依据

更新接口时先核对下列实现，不能仅复制旧注释或前端兼容类型：

- 路由与绑定：[AiChatController](src/main/java/chat/liuxin/ai/controller/AiChatController.java)、[AiConversationController](src/main/java/chat/liuxin/ai/controller/AiConversationController.java)、[AiModelController](src/main/java/chat/liuxin/ai/controller/AiModelController.java)、[AiModelAdminController](src/main/java/chat/liuxin/ai/controller/admin/AiModelAdminController.java)。
- 请求/响应：[ChatRequest](src/main/java/chat/liuxin/ai/dto/ChatRequest.java)、[ChatResponse](src/main/java/chat/liuxin/ai/dto/ChatResponse.java)、[ChatHistoryResponse](src/main/java/chat/liuxin/ai/dto/ChatHistoryResponse.java)、[ModelConfigRequest](src/main/java/chat/liuxin/ai/dto/ModelConfigRequest.java)、[ModelConfigDTO](src/main/java/chat/liuxin/ai/dto/ModelConfigDTO.java)。
- 流式协议：[StreamingChatService](src/main/java/chat/liuxin/ai/service/StreamingChatService.java)、[SseEmitterHelper](src/main/java/chat/liuxin/ai/service/SseEmitterHelper.java)、[WritingToolEventSink](src/main/java/chat/liuxin/ai/service/WritingToolEventSink.java)、[FieldUpdatePayload](src/main/java/chat/liuxin/ai/dto/FieldUpdatePayload.java)。
- 上下文与同步行为：[PromptService](src/main/java/chat/liuxin/ai/service/PromptService.java)、[AiChatServiceImpl](src/main/java/chat/liuxin/ai/service/impl/AiChatServiceImpl.java)、[草稿 DTO](src/main/java/chat/liuxin/ai/dto/AdminArticleDraftSnapshot.java)。
- 持久化与统计：[MemoryService](src/main/java/chat/liuxin/ai/service/MemoryService.java)、[AiChatMessageMapper](src/main/java/chat/liuxin/ai/mapper/AiChatMessageMapper.java)、[AiModelConfigService](src/main/java/chat/liuxin/ai/service/AiModelConfigService.java)。
- 鉴权与错误：[SecurityConfig](src/main/java/chat/liuxin/ai/infra/config/SecurityConfig.java)、[JWT Filter](src/main/java/chat/liuxin/ai/infra/filter/JwtAuthenticationFilter.java)、[AiModelPolicy](src/main/java/chat/liuxin/ai/infra/security/AiModelPolicy.java)、[限流拦截器](src/main/java/chat/liuxin/ai/infra/security/AiRateLimitInterceptor.java)、[GlobalExceptionHandler](src/main/java/chat/liuxin/ai/infra/exception/GlobalExceptionHandler.java)。
- 配置与代理：[application.yml](src/main/resources/application.yml)、[AI 代理](../nginx/conf.d/ai-proxy.include)、[TtsClient](src/main/java/chat/liuxin/ai/common/client/TtsClient.java)、[跨服务规范](../Docs/架构/跨服务规范.md)。
