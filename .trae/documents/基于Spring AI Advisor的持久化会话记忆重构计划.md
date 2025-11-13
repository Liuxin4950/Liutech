## 总体思路
- 后端以 Spring AI 的 ChatClient + Advisor（MessageChatMemoryAdvisor、SimpleLoggerAdvisor）为主线，重写 ChatMemory 为数据库持久化，实现“用户ID + 会话ID + 类型”的会话记忆。
- 动作解析改为“严格模式 + 规则门控 + 可信度评分”的管道，保证只触发允许的动作，并统一元数据结构。
- 前端新增完整聊天界面（左侧会话列表、右侧消息区），首次请求自动创建会话ID，随后持续聊天；列表支持按“类型”筛选与搜索。
- 保留现有功能与风格，主题色全部使用 `theme.css` 的变量，兼容明暗模式。

## 数据模型（重构后）
### 会话表 `ai_conversation`
- 字段：
  - `id` BIGINT PK
  - `user_id` VARCHAR(64)
  - `type` VARCHAR(32)（会话类型）
  - `title` VARCHAR(200)（可空，默认“新会话”或来源标题）
  - `status` TINYINT（0-active，9-archived）
  - `message_count` INT
  - `last_message_at` DATETIME
  - `metadata` JSON（如 `{ page, postId, tags, extra }`）
  - `created_at`、`updated_at`
- 索引：`idx_user_type(user_id,type)`、`idx_user_last(user_id,last_message_at)`
- 类型建议（可扩展）：`general`（通用）、`post`（文章）、`tag`、`category`、`user`、`system` 等。

### 消息表 `ai_chat_message`
- 字段：
  - `id` BIGINT PK
  - `conversation_id` BIGINT（必填）
  - `user_id` VARCHAR(64)
  - `role` ENUM('user','assistant','system')
  - `content` LONGTEXT
  - `model` VARCHAR(100)
  - `status` TINYINT（1完成，9错误）
  - `metadata` JSON（如 `{ emotion, action, extra }`）
  - `created_at`、`updated_at`
- 索引：`idx_conv_created(conversation_id,created_at)`

## 后端实现
### ChatMemory 持久化
- 新建 `PersistentChatMemory` 实现 Spring AI `ChatMemory`：
  - `add(conversationId, Message|List<Message>)`：写入消息并更新 `ai_conversation.message_count/last_message_at`。
  - `get(conversationId, lastN)`：取最近 N 条，升序返回用于 Prompt。
  - `clear(conversationId)`：清空该会话消息。
- 将 `MemoryService` 扩展为会话维度：
  - `createConversation(userId, type, title?, metadata?)` → 返回会话ID
  - `listConversations(userId, type?, page, size)`、`getConversation(conversationId)`
  - `listMessagesByConversation(conversationId, page, size)`
  - `renameConversation(conversationId, title)`、`archiveConversation(conversationId)`、`deleteConversation(conversationId)`

### Advisor 配置
- `@Bean ChatMemory chatMemory()` 返回 `PersistentChatMemory`。
- `@Bean ChatClient chatClient(...)`：
  - `.defaultSystem(aiPromptConfig.getFullSystemPrompt())`
  - `.defaultAdvisors(new SimpleLoggerAdvisor(), new MessageChatMemoryAdvisor(chatMemory))`
- 调用处向 AdvisorContext 注入会话ID：
  - `.advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))`
- 模型调用统一使用 `ChatClient`，底层可连接硅基流动（OpenAI 兼容）。

### 动作解析（强化版）
- 管道化：
  1) 结构化校验（严格 JSON Schema：`message/emotion/action/metadata`）
  2) 动作白名单校验（与应用枚举同步）
  3) 上下文门控（是否明确用户意图、来源页面是否匹配）
  4) 可信度评分（对话历史与关键词匹配，低于阈值则置 `action=none`）
- 统一 `metadata`：搜索类使用 `{ query, filters }`；导航类使用 `{ target }`；交互类使用 `{ postId }`。
- 保留你已有的解析逻辑并抽象为策略类，便于扩展新动作。

### API 契约
- 发起聊天：`POST /ai/chat`
  - 请求：`{ conversationId?, type?, message, context }`
  - 行为：若未携带 `conversationId`，后端先创建会话（`type` 缺省为 `general`），并在响应回传 `conversationId`。
  - 响应：`{ success, message, emotion, action, metadata, conversationId }`
- 查询会话记录列表（按类型）：`GET /ai/history/{type}` → 返回 `string[]`（会话ID列表）
- 查询会话记录详情：`GET /ai/history/{type}/{chatId}` → 返回 `[{ role, content, timestamp, metadata }]`
- 会话管理：
  - `GET /ai/conversations?type&page&size`
  - `POST /ai/conversations { type, title?, metadata? }`
  - `GET /ai/conversations/{id}/messages?page&size`
  - `PUT /ai/conversations/{id}/rename { title }`
  - `PUT /ai/conversations/{id}/archive`
  - `DELETE /ai/conversations/{id}`

## 前端实现
- 新界面：
  - `ChatApp.vue`：两栏布局（左会话列表、右聊天详情），采用主题变量适配明暗模式。
  - `ConversationList.vue`：加载 `GET /ai/conversations` 与筛选 `type`；支持搜索关键词。
  - `ChatDetail.vue`：渲染消息与输入框；发送时携带 `conversationId`；分页加载历史。
  - 复用 `SearchPanel.vue`；AI 返回 `search:posts` 时打开并执行检索。
- 首次消息：维持你现有 `AiChat.vue` 逻辑；若没有 `conversationId`，由后端创建并返回，随后前端缓存继续使用；该会话同步出现在左侧列表。

## 分阶段计划
- 阶段1（后端主）：
  - 建表并实现 `PersistentChatMemory` 与 Advisor 配置
  - `/ai/chat` 支持首次创建会话并回传 `conversationId`
  - 最小会话管理接口：列表与详情
- 阶段2（前端主）：
  - 新聊天界面骨架与路由
  - 会话列表/详情接入；消息分页；与现有 `AiChat.vue` 兼容
- 阶段3（增强）：
  - 流式聊天（SSE）
  - 会话内搜索与高亮
  - 滚动摘要（长会话上下文优化）
  - 审计与限流、重试与错误提示

## 测试与保障
- 单测：`PersistentChatMemory` 的 add/get/clear；Advisor 写入与读取正确性。
- 集成：首次自动创建会话、后续请求保持同会话；列表与详情一致。
- 回滚：保留迁移脚本；如需退回“仅按用户维度”，可用脚本合并消息并移除 `conversation_id`。

---
请确认该方案。如果确认，我将从阶段1开始落地：后端持久化记忆、Advisor接入、以及 `/ai/chat` 的首次自动创建会话行为，并提供最小的会话查询接口，随后推进前端新界面骨架。