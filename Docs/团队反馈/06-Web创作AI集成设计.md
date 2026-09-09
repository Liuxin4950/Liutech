# 06 - Web 创作页 AI 集成设计与修复

> 团队：架构师主导，PM/测试/运维/项目主管审核
> 日期：2026-07-09 | 分支：refactor/blog-polish
> 本文档记录顶尖团队面对"AI 功能只显示回复没集成"问题时的分析思路与修复方案，供学生学习。

---

## 一、用户反馈

Web `/create` 页 AI 功能"只显示回复，没集成"。期望：
1. 多轮对话（AI 问目标，用户能回复）
2. AI 写的文章以 HTML 写入富文本编辑器
3. 自动匹配现有标签/分类，没有就新增，前端默认选中

## 二、现状分析（顶尖团队原则：先看代码再下结论）

调研发现前端**已有完整集成代码**，不是"没做"：

| 文件 | 已有实现 |
| --- | --- |
| `usePostEditor.ts` handleFieldUpdate | field-update 回写 form.title/summary/content（DOMPurify.sanitize）/categoryId/tagIds + undoStack 撤销 |
| `usePostEditor.ts` rememberAiTaxonomySuggestions | 自动匹配现有标签/分类（sameName），已存在则选中，不存在则建议创建 |
| `CreatePost.vue` ai-taxonomy-card | AI 建议创建分类/标签，点击"创建并选中" |
| `AdminWritingAssistant.vue` | quickPrompts + send + onFieldUpdate/onWritingDraft handler + inferFieldScope |

所以前端集成代码就绪。问题在**后端 AI 没输出 field-update + 不支持多轮**。

## 三、根因（顶尖团队原则：找根因不治表症）

### 根因1：AI 不输出 field-update 标记
`PromptService.buildWritingSystemPrompt` 加了 `---field-update--- JSON ---end---` 格式约束，但 **AI 模型不一定遵循**。AI 只输出自然语言文本（onData 累加显示），没输出 `---field-update---` 标记 → `FieldUpdateParser` 解析不到 → `field-update` SSE 事件不发 → `handleFieldUpdate` 不触发 → "只显示回复，没集成"。

### 根因2：写作模式不支持多轮
`StreamingChatService.processWritingStream` 的 `onComplete` 回调是空（不落库），前端 `send` 每次新请求没传历史消息。AI 不知道之前问过什么、用户答过什么 → 无法"AI 问目标，用户回复"的多轮交互。

## 四、顶尖团队思路（确定性优先）

AI 端到端可用的关键不是"期望 AI 按格式输出"，而是"**无论 AI 怎么输出，后端都能提取结构化结果回写表单**"。

三条原则：
1. **兜底优于依赖**：不能只靠 AI 遵循 `---field-update---` 格式。后端 `onComplete` 时，如果整轮没发过 field-update，从全文提取 HTML 作为 `contentHtml` 发一次 field-update。这样即使 AI 只输出纯文本文章，也能写入编辑器。
2. **多轮上下文**：写作模式复用 `ChatRequest.tempMessages`（前端传历史 user/assistant），AI 能追问目标、记住用户回复。不落库（写作是临时创作，不需持久化）。
3. **提示词强化但非唯一依赖**：明确要求 AI 输出 field-update，但兜底保证即使不输出也能用。

## 五、修复方案（可实施、可验证）

### 修复1：后端兜底（StreamingChatService 写作 onComplete）
`processWritingStream` 的 `subscribeStream` 传一个 `onComplete` 回调，写作模式下：如果整轮没发过 field-update，把 `fullResponse` 作为 `contentHtml` 发一次 field-update 事件（DOMPurify 在前端已做，后端只透传）。

实现：`subscribeStream` 加 `AtomicBoolean fieldUpdateSent` 标志，`handleWritingChunk` 发 field-update 时置 true；`onComplete` 时若 `!fieldUpdateSent` 且 `fullResponse` 非空，发兜底 field-update。

### 修复2：多轮支持（前端传 tempMessages）
`AdminWritingAssistant` 维护 `history`（user/assistant 消息列表），`send` 时把 history 作为 `tempMessages` 传给后端。后端 `PromptService` 写作模式已支持 `buildGuestPromptMessages`（tempMessages），复用。

### 修复3：提示词强化
`buildWritingSystemPrompt` 追加："当你已经生成完整文章内容后，**必须**输出 field-update 标记把正文 HTML 写入 contentHtml 字段，否则用户无法应用到编辑器。"

## 六、验证标准
1. 用户输入"帮我写一篇 Spring Boot 入门文章" → AI 输出 HTML 写入 TinyMCE 编辑器（兜底或 field-update）
2. AI 问"目标读者是谁" → 用户回复"应届生" → AI 基于回复继续（多轮）
3. AI 建议标签"Spring Boot" → 已存在则选中，不存在则前端显示"创建并选中"
4. `mvn test` LiuTech-AI 全绿（兜底逻辑加测试）

## 七、团队审核
- 架构师：兜底 + 多轮 + 提示词强化，确定性优先，不过度设计
- PM：用户核心诉求（写入编辑器/多轮/标签匹配）全部覆盖
- 测试：兜底 field-update 加单元测试
- 项目主管：避免"为拆分而拆分"，复用现有 tempMessages/handleFieldUpdate，最小改动
