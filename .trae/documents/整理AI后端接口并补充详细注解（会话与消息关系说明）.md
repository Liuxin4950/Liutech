## 目标
- 为 AI 后端的核心接口与控制器补充详细中文注解，明确“会话（AiConversation）-消息（AiChatMessage）”的存储与生命周期关系。
- 保持现有路由与逻辑不变，仅增强可读性与维护性。

## 涉及文件
- 接口与实现：`MemoryService`、`MemoryServiceImpl`
- 实体：`AiConversation`、`AiChatMessage`
- 控制器：`AiChatController`、`AiConversationController`
-（可选）服务：`AiChatServiceImpl`（补充SSE事件语义与落库时机说明）

## 注解内容要点
- 会话与消息关系：一对多；`message_count` 与 `last_message_at` 的维护时机；删除会话的级联删除策略（先删消息后删会话）。
- 消息字段语义：`role/user/assistant/system`、`status=1/9`、`metadata` 用途；排序与分页规则（按 `created_at` 与 `id`）。
- MemoryService 方法契约：入参、返回、排序约定、事务性、异常处理与边界行为（limit/page）。
- 控制器接口：路由、鉴权、请求/响应结构、持久化副作用；SSE 事件序列与错误路径说明。

## 实施步骤
1. 在 `AiConversation`、`AiChatMessage` 类上补充更完整的类与字段注释，突出关系与设计约束。
2. 在 `MemoryService` 与 `MemoryServiceImpl` 为每个方法添加 Javadoc，说明：用途、参数、返回、排序与事务、会话指标更新规则。
3. 在 `AiChatController`、`AiConversationController` 的每个接口方法添加 Javadoc，补充路由、鉴权、请求体/参数、响应含义与存储副作用；对 `/ai/chat/stream` 增加事件语义说明。
4.（可选）在 `AiChatServiceImpl` 顶部添加类注释，描述流式过程的事件与入库时机，便于后续维护。

## 验证
- 编译通过；不修改逻辑，仅新增注释。
- 通过代码阅读即可掌握会话与消息的关系与接口副作用。

## 交付
- 直接在源码中完成注释增强，无新增文档文件。