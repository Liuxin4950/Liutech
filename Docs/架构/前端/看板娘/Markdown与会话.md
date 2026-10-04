# Markdown 渲染与会话历史

> 本文档描述 AI 回复的 Markdown 流式渲染、代码高亮、安全净化，以及会话历史侧边栏的管理。
> 本文统一定义聊天的身份恢复与浏览器持久化；消息状态与发送流程见[状态与消息流](状态与消息流.md)。

## Markdown 渲染

[`MarkdownRenderer.vue`](../../../../Web/src/components/MarkdownRenderer.vue) + [`useMarkdown.ts`](../../../../Web/src/composables/useMarkdown.ts) 负责 markdown -> HTML。

### 渲染流程

```
props.content 变化（流式 chunk 或完整内容）
  └─ renderedContent (computed)
      ├─ processMarkdown(content, isStreaming)
      │   ├─ isStreaming: processStreamingMarkdown  补全未闭合标记 + marked + DOMPurify
      │   └─ 否则: marked.parse + DOMPurify.sanitize
      └─ isStreaming ? appendStreamingCaret(html) : html   流式追加光标
  └─ watch(renderedContent) -> highlightCodeBlocks          高亮代码块
```

### 流式补全标记

流式内容可能未闭合（如代码块 ``` 只有一半），直接解析会渲染错乱。`processStreamingMarkdown` 补全：

```ts
if (``` 数量为奇数) content += '\n```'      // 代码块
if (` 数量为奇数) content += '`'             // 行内代码
if (** 数量为奇数) content += '**'           // 加粗
if (单 * 数量为奇数) content += '*'          // 斜体（排除 ** 中的 *）
```

补全后 `marked.parse` + `DOMPurify.sanitize`。

### 代码高亮

两层高亮：

1. **marked renderer.code**：解析时用 `hljs.highlight(code, { language })` 高亮，返回 `<pre><code class="hljs language-xxx">`
2. **highlightCodeBlocks**：`watch(renderedContent)` 后遍历 `pre code`，跳过已有 `hljs` class 的（避免重复高亮）

语言检测：`hljs.getLanguage(language)` 有效则用，否则 `plaintext`。

### 安全净化

DOMPurify 配置白名单：

- **允许标签**：h1-h6, p, br, strong, em, ul, ol, li, blockquote, code, pre, a, img, table 等
- **禁止标签**：script, object, embed, iframe, form, input, button
- **禁止属性**：onclick, onload, onerror, onmouseover
- **禁止 data-* 属性**

### 流式光标

`appendStreamingCaret` 在最后一个块级元素（p/li/blockquote/h/pre/code 等）内追加 `<span class="streaming-caret">`，CSS 闪烁动画。流式结束（`isStreaming=false`）时 watch 移除所有光标。

### 链接处理

`renderer.link`：
- 内部链接（`/` 开头）：`<a href="/xxx">`，点击走 Vue Router（`onContentClick` 拦截）
- 外部链接：`target="_blank" rel="noopener noreferrer"`
- 非 http(s)/mailto/相对路径：丢弃链接只留文本

## 性能：保持 computed 同步

**不要用 rAF/setTimeout 节流流式渲染**。实测 rAF 节流会"一顿一顿"（chunk 合并到下一帧 + 单次 marked.parse 耗时长掉帧），computed 每 chunk 同步更新虽然频繁但平滑。

性能优化方向（如需）：减少单次解析耗时，而非节流次数：
- 流式时跳过代码高亮（renderer.code 不调 hljs），完成后再高亮
- 增量渲染或 Web Worker（改动大）

## 会话历史

[`useConversationManager.ts`](../../../../Web/src/composables/useConversationManager.ts) 管理会话列表，[`AiChat.vue`](../../../../Web/src/components/AiChat.vue) 渲染侧边栏。

### 侧边栏结构

```
history-sidebar (expanded && isAuthenticated)
  ├─ history-header: "会话历史" + 关闭按钮
  └─ history-content (可滚动)
      ├─ loading / empty / conversation-list
      └─ conversation-item
          ├─ conversation-info: 标题 + 消息数 + 时间
          └─ conversation-actions: 更多按钮 + 下拉菜单（重命名/删除）
```

### 会话项交互

| 操作 | 触发 | 处理 |
| --- | --- | --- |
| 点击会话项 | `@click` item | `loadConversation(id)` 加载消息 |
| 点击"更多"按钮 | `@click.stop` more-btn | `toggleConversationMenu(id)` 切换菜单 |
| 重命名 | 菜单"重命名" | `startEditTitle` -> input 替换标题 -> blur/enter `saveTitle` |
| 删除 | 菜单"删除" | `showConfirm` 确认 -> `deleteConversation` |
| 点击菜单外 | document click | `closeConversationMenu`（`handleMenuClickOutside`） |

**menuOpenId**：记录当前打开菜单的会话 ID，点击更多按钮 toggle，点击外部关闭。

### 列表刷新

`toggleHistorySidebar` 每次打开都 `loadConversations`（之前只在列表为空时加载，导致发消息后重开看到的 messageCount 是旧值）。

### loadConversation

```
loadConversation(id)
  ├─ 捕获当前已验证用户 ID
  ├─ ConversationService.messages(id, 1, 100)   分页取消息
  ├─ 返回时再次核对用户 ID；身份不同则丢弃
  ├─ chatStore.clearHistory()
  ├─ chatStore.conversationId = id
  ├─ forEach msg: addUserMessage / addAiMessage
  └─ showHistorySidebar = false                  关闭侧边栏
```

## 会话 API

[`conversation.ts`](../../../../Web/src/services/conversation.ts)，走 AI 服务（`ServiceType.AI`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `list(type, page, size)` | `GET /ai/conversations` | 会话列表（按 lastMessageAt 倒序） |
| `create(type, title)` | `POST /ai/conversations` | 创建会话，返回 id |
| `messages(id, page, size)` | `GET /ai/conversations/{id}/messages` | 从最近消息开始分页，页内正序 |
| `rename(id, title)` | `PUT /ai/conversations/{id}/rename` | 重命名 |
| `remove(id)` | `DELETE /ai/conversations/{id}` | 删除（先删消息再删会话） |

**Conversation 字段**：`id, userId, title, status, messageCount, lastMessageAt`。前端展示后端返回的计数，不依据本地消息数量修改会话列表计数。

## 身份与持久化

身份入口在 [`user.ts:44`](../../../../Web/src/stores/user.ts) 的 `getAuthenticatedUserId()`：只在 `verifiedToken` 等于当前 token 时返回主后端已确认的用户 ID。`authenticatedUserId` 不持久化；启动时即使有 token 和缓存 `userInfo`，仍需 `fetchUserInfo()` 确认当前身份。用户信息响应返回时重新核对 token，旧 token 的响应不恢复用户。

[`chat.ts:134`](../../../../Web/src/stores/chat.ts) 使用三个内部归属：`guest`、`pending`、`user:<id>`。`pending` 不加载或写入聊天缓存，也不发送聊天；取得真实身份后才恢复对应分桶。

| 数据 | 登录用户 `localStorage` | 游客 `sessionStorage` |
| --- | --- | --- |
| 消息与会话 ID | `liutech-chat-history:user:<id>` | `liutech-chat-history-guest`，会话 ID 为 null |
| 回复模式 | `liutech-chat-mode:user:<id>` | `liutech-chat-mode-guest` |
| 语音偏好 | `liutech-chat-tts-enabled:user:<id>` | `liutech-chat-tts-enabled-guest` |

`saveToStorage` 防抖 500ms，消息监听使用 `id:length:streaming:thinking:articles` 签名。恢复时清除 `isStreaming`/`isThinking`、按时间排序，并根据已有负数消息 ID 重置临时计数器，避免刷新后 ID 重复。旧的无用户归属缓存不能安全迁移，初始化时清除；服务端会话仍可由历史侧栏加载。

```text
auth.ts setToken/removeToken 发出 liutech-auth-change
  或 storage 事件、已验证用户状态变化
  → chatStore.syncIdentity()
      → 按旧 owner 保存，取消旧防抖任务
      → 增加 identityGeneration，取消 SSE/普通 HTTP 与 TTS
      → 清空当前消息、会话 ID、错误及等待状态
      → 进入新 owner，恢复其独立分桶
```

[`syncIdentity`](../../../../Web/src/stores/chat.ts) 的身份代次还用于拒绝旧请求的晚到正文、推荐、会话 ID 和终态。游客 `tempMessages` 仅从当前游客消息构造，不能包含退出用户的历史。

会话侧栏监听身份变化，清空列表、打开状态和菜单；列表和消息加载返回时核对请求前后的已验证用户 ID，不能把旧账号结果放回新账号页面。删除与重命名仍由会话接口执行服务端权限校验。

## 陷阱与约束

- **流式渲染不要节流**：computed 同步最平滑，rAF 会卡顿
- **DOMPurify 不能跳过**：模型输出、检索正文和工具结果都可能包含不可信 HTML
- **会话列表每次打开刷新**：避免 messageCount 旧值
- **菜单点击外部关闭**：`@click.stop` 在更多按钮和菜单内，document 监听点击其他区域
- **重命名 input 要 `@click.stop`**：防止点击 input 触发 loadConversation

- 身份隔离回归见[chatIdentity.test.ts](../../../../Web/src/__tests__/chatIdentity.test.ts)：覆盖退出后游客上下文、账号切换、旧响应、普通请求取消与刷新后消息 ID。
