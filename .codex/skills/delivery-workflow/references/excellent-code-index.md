# 优秀代码范式索引

> 使用说明：本文件是“按需参考的代码入口索引”，**不强制读取**。开发前如果涉及以下场景，建议先看对应行：统一响应结构、业务异常处理、全局异常处理、API 封装、流式响应、跨服务调用、文件上传下载。其它场景可跳过。仍需阅读真实文件，不能盲复制。

## 1. 维护原则

1. 只记录项目内已经被认可、可复用的代码路径。
2. 每条记录必须说明适用场景和为什么值得参考。
3. 如果发现某个样例已经过时，必须更新或移除。
4. 新增功能完成后，如果出现高质量范式，可以补充到本文件。
5. 使用某条样例前必须确认路径仍存在、职责仍匹配、没有明显过时。
6. 如果样例过时，必须在本文件中标注“已弃用”或移除，不得继续引用。

## 1. 维护原则

1. 只记录项目内已经被认可、可复用的代码路径。
2. 每条记录必须说明适用场景和为什么值得参考。
3. 如果发现某个样例已经过时，必须更新或移除。
4. 新增功能完成后，如果出现高质量范式，可以补充到本文件。
5. 使用某条样例前必须确认路径仍存在、职责仍匹配、没有明显过时。
6. 如果样例过时，必须在本文件中标注“已弃用”或移除，不得继续引用。

## 2. 后端通用范式

| 场景 | 推荐参考 | 参考原因 |
| --- | --- | --- |
| 统一响应结构 | `LiuTech/src/main/java/chat/liuxin/liutech/common/Result.java` | 统一 API 返回格式，新增接口应保持一致 |
| 业务异常 | `LiuTech/src/main/java/chat/liuxin/liutech/common/BusinessException.java` | 用业务异常表达可预期失败，避免散落 magic message |
| 全局异常处理 | `LiuTech/src/main/java/chat/liuxin/liutech/common/GlobalExceptionHandler.java` | 新异常类型应接入统一错误响应 |
| 管理端 Controller 基类 | `LiuTech/src/main/java/chat/liuxin/liutech/controller/admin/BaseAdminController.java` | 管理端公共能力优先复用，避免重复鉴权/响应逻辑 |
| MyBatis Plus 配置 | `LiuTech/src/main/java/chat/liuxin/liutech/config/MybatisPlusConfig.java` | 涉及分页、插件、SQL 行为时先检查这里 |

## 3. 后端业务范式

| 场景 | 推荐参考 | 参考原因 |
| --- | --- | --- |
| 文章公开接口 | `LiuTech/src/main/java/chat/liuxin/liutech/controller/web/PostsController.java` | Web 公开 API 的参数、分页、响应风格参考 |
| 管理端文章接口 | `LiuTech/src/main/java/chat/liuxin/liutech/controller/admin/PostsAdminController.java` | Admin CRUD 和权限边界参考 |
| 文件上传接口 | `LiuTech/src/main/java/chat/liuxin/liutech/controller/web/FileUploadController.java` | 涉及上传/下载必须重点审查安全边界 |
| 资源下载接口 | `LiuTech/src/main/java/chat/liuxin/liutech/controller/web/ResourceDownloadController.java` | 涉及下载权限、文件响应头时优先参考 |

## 4. AI 服务范式

| 场景 | 推荐参考 | 参考原因 |
| --- | --- | --- |
| AI 流式聊天 | `LiuTech-AI/src/main/java/chat/liuxin/ai/controller/AiChatController.java` | SSE API 入口和响应方式参考 |
| AI 聊天核心逻辑 | `LiuTech-AI/src/main/java/chat/liuxin/ai/service/impl/AiChatServiceImpl.java` | 涉及上下文、流式输出、TTS 时必须先读 |
| 博客服务调用 | `LiuTech-AI/src/main/java/chat/liuxin/ai/client/BlogApiClient.java` | AI 服务跨服务调用博客后端的范式 |
| AI 异常处理 | `LiuTech-AI/src/main/java/chat/liuxin/ai/exception/GlobalExceptionHandler.java` | AI 服务错误响应保持一致 |

## 5. 前端 Web 范式

| 场景 | 推荐参考 | 参考原因 |
| --- | --- | --- |
| API 基础封装 | `Web/src/services/api.ts` | Web 请求拦截、错误处理和 token 处理入口 |
| 文章 API | `Web/src/services/post.ts` | Web 业务 service 的组织方式参考 |
| AI 流式请求 | `Web/src/services/aiStream.ts` | SSE / 流式响应前端处理参考 |
| 全局错误处理 | `Web/src/composables/useErrorHandler.ts` | 页面和组件错误处理优先复用 |
| Markdown 渲染 | `Web/src/components/MarkdownRenderer.vue` | 涉及内容渲染、XSS 风险时优先检查 |
| 评论区 | `Web/src/components/CommentSection.vue` | 复杂组件拆分和交互状态参考 |

## 6. 前端 Admin 范式

| 场景 | 推荐参考 | 参考原因 |
| --- | --- | --- |
| Admin API 基础封装 | `Admin/src/services/api.ts` | 管理端请求、认证、错误处理入口 |
| 管理端文章服务 | `Admin/src/services/posts.ts` | Admin service CRUD 风格参考 |
| 用户状态 | `Admin/src/stores/user.ts` | 管理端登录态、用户信息状态参考 |
| 管理页结构 | `Admin/src/views/admin/PostsManagement.vue` | 表格、筛选、分页、编辑弹窗的综合范式 |
| 全局错误工具 | `Admin/src/utils/globalErrorHandler.ts` | 管理端错误处理统一入口 |

## 7. 使用检查

开发前问自己：

- 当前需求属于哪个范式？
- 是否已有相似 Controller / Service / Component 可以复用？
- 新代码是否保持了同一层的命名和错误处理风格？
- 是否引入了与范式冲突的安全或性能风险？
