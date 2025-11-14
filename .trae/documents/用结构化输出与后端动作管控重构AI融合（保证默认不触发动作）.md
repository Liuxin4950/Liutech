## 改造目标
- 在正常对话中强制 `action=none`，仅当用户明确指令且高置信度时才触发动作。
- 去掉手写 JSON 解析与多次“修复生成”的不稳定逻辑，改为结构化输出解析（Bean Output Parser）+ 枚举白名单。
- 将“意图判定/动作映射”收口到后端，多阶段流水线提高稳定性，前端仅做最小执行。
- 在关键位置补充注释，便于你学习与维护。

## 技术方案
- **三阶段管线**：
  1) 意图分类（Agent-1，小提示词）：仅输出 `IntentResult{type, confidence, targets}`，结构化，短且稳；
  2) 文本回复（Agent-2，主模型）：只生成对话 `message` 与 `emotion`，不携带动作；
  3) 动作映射（Agent-3，服务端逻辑）：基于意图与上下文，映射枚举动作与参数；不满足阈值/上下文/白名单 → 强制 `none`。
- **结构化输出解析**：使用 Spring AI 的 Bean Output Parser（或等价实现）为 `IntentResult`、`ActionResponse` 生成 JSON Schema 提示，将模型输出直接映射到 Java Bean，失败统一兜底。
- **严格白名单与阈值**：动作统一为枚举 `AiAction`；`confidence>=阈值` 且上下文完整（如 `articleId` 存在）才能触发；否则 `none`。

## 拟改动文件与内容
- 后端 DTO 与枚举（新增）
  - `chat/liuxin/ai/dto/IntentResult.java`：
    - 字段：`IntentType type`、`double confidence`、`Map<String,Object> targets`
    - 注释：用途、取值范围、示例 JSON
  - `chat/liuxin/ai/dto/IntentType.java`（枚举）：`LIKE/FAVORITE/COMMENT/SHARE/NAVIGATE/SEARCH/NONE`
  - `chat/liuxin/ai/dto/ActionResponse.java`：
    - 字段：`String message`、`String emotion`、`AiAction action`、`Map<String,Object> metadata`
    - 注释：各字段语义、示例 JSON
  - `chat/liuxin/ai/dto/AiAction.java`（枚举）：与 `application.yml` 白名单一致（如 `navigate_home`、`interact_like`、`search_posts`、`show_capabilities`、`none`）；或保留 `navigate:home` 等字符串枚举风格（两者择一，计划采用“枚举+参数解耦”的形式，便于校验）。
- `chat/liuxin/ai/config/ChatClientConfig.java`（增强）
  - 保持 `defaultSystem(aiPromptConfig.getFullSystemPrompt())` 与日志 Advisor；
  - 将 Bean 输出的 Schema 追加到系统提示尾部（注释说明“为什么这样能提高格式稳定性”）。
- `chat/liuxin/ai/service/SiliconFlowChatClient.java`（调用保持不变）
  - 仍用 `ChatClient` 调用；在关键处加注释解释“为什么不在这里做解析”。
- `chat/liuxin/ai/service/impl/AiChatServiceImpl.java`（核心重构）
  - 保留：历史上下文拼接与上下文注入；
  - 替换：
    - 新增 `runIntentClassifier(input, context)`：构造短提示，解析为 `IntentResult`；注释解释“短提示与结构化输出的优势”。
    - 新增 `runContentGenerator(messages)`：只生成 `message` 与可选 `emotion`；不带动作；
    - 新增 `mapToAction(IntentResult, context)`：将意图与上下文映射到 `AiAction + metadata`，并进行阈值/白名单/上下文三重校验；不满足则 `none`；
    - 统一落库：把 `assistant.message` 与 `emotion/action/metadata` 写入 `metadataJson`；
    - 移除：`parseModelOutput/ensureValidStructuredOutput` 及二次修复调用，注释说明“删除原因与新方案的稳定性”。
- 前端 `Web/src/components/AiChat.vue`
  - 精简 `dispatchAction`：改为注册表驱动；若 `action==='none'` 直接返回；其余按映射表执行；
  - 删除 `hasLikeIntent/hasFavoriteIntent` 客户端判断逻辑（注释说明“意图判断迁移到后端”）。

## 关键注释位置（学习导向）
- DTO/枚举类头：说明设计、取舍与示例 JSON
- `ChatClientConfig`：说明 `defaultSystem` 与 Schema 注入的作用
- `AiChatServiceImpl`：管线每个阶段方法上添加说明（何时调用、输入输出、失败兜底）；在动作映射处标注“三重校验 → 默认 none”的关键决策点
- 前端 `dispatchAction`：说明“仅当 `action!=none` 且来自服务端”才执行，避免前端二次判定

## 稳定性保障
- 结构化输出 + Schema 提示：减少非 JSON/字段缺失；
- 三重校验（白名单/阈值/上下文）：任何不达标 → `none`；
- 单次生成：去掉“让模型再修复一次”的二次生成；
- 统一兜底：解析失败统一返回 `message` 与 `none`，避免前端散落保护分支；
- 日志：保留日志 Advisor，记录意图与动作决策。

## 验证用例
- 闲聊：“你好，最近在看什么？” → `action=none`；
- 明确指令（详情页）：“帮我点赞这篇文章” 且有 `articleId` → `intent=LIKE/conf>=0.8` → `action=interact_like`，`metadata.postId` 正确；
- 模糊表达或缺上下文：“这个不错” 或无 `articleId` → `action=none`；
- 非法输出或提示词爆破 → 解析失败 → 兜底 `none`。

## 里程碑
- 阶段1：后端 DTO/枚举与解析器接入（含注释）
- 阶段2：`AiChatServiceImpl` 三阶段管线改造（含注释）
- 阶段3：前端 `dispatchAction` 精简与注册表改造（含注释）
- 阶段4：联调与用例验证；输出最终总结与注意事项

请确认以上计划，我将按此实施并在关键位置添加教学注释。