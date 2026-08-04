# Markdown 渲染与会话历史

> 本文档描述 AI 回复的 Markdown 流式渲染、代码高亮、安全净化，以及会话历史侧边栏的管理。
> 持久化（localStorage/sessionStorage）见 [状态与消息流](状态与消息流.md)。

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
- 流式时跳过 DOMPurify（AI 内容相对可信，但有 XSS 风险，需评估）
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
  ├─ ConversationService.messages(id, 1, 100)   取最近 100 条消息
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
| `messages(id, page, size)` | `GET /ai/conversations/{id}/messages` | 分页消息（倒序） |
| `rename(id, title)` | `PUT /ai/conversations/{id}/rename` | 重命名 |
| `remove(id)` | `DELETE /ai/conversations/{id}` | 删除（先删消息再删会话） |

**Conversation 字段**：`id, userId, title, status, messageCount, lastMessageAt`。`messageCount` 由后端 `touchConversation` 每次存消息时累加（历史会话可能不准，需后端 `COUNT` 实时统计修复）。

## 陷阱与约束

- **流式渲染不要节流**：computed 同步最平滑，rAF 会卡顿
- **DOMPurify 不能跳过**：除非明确评估 XSS 风险可接受
- **会话列表每次打开刷新**：避免 messageCount 旧值
- **菜单点击外部关闭**：`@click.stop` 在更多按钮和菜单内，document 监听点击其他区域
- **重命名 input 要 `@click.stop`**：防止点击 input 触发 loadConversation
