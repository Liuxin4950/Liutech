# TTS 与认证边界

> 说明 AI 服务对 TTS 的完整所有权、主服务身份内省契约、内部接口保护和用户数据清理边界。

## 服务所有权

| 能力 | 所有者 | 数据/密钥 |
| --- | --- | --- |
| JWT 签发、验签、用户当前状态与角色 | 主服务 | `JWT_SECRET`、`liutech.users` |
| AI 身份上下文 | AI 服务 | 主服务内省成功结果，按 token SHA-256 摘要缓存 60s |
| TTS 配置、状态、推理、音色、临时音频 | AI 服务 | `liutech_ai.ai_tts_config`、AI 容器密钥与 `/app/tts-cache` |
| 用户彻底删除 | 主服务发起、AI 先清理 | `LIUTECH_INTERNAL_TOKEN` 保护幂等清理接口 |

两个应用使用 `liutech_app` 与 `liutech_ai_app`，各自只拥有本服务数据库的运行期增删改查权限。应用不使用 MySQL root。

## 身份数据流

```text
浏览器携带 Authorization: Bearer <token>
  → AI RemoteAuthenticationFilter
    → token 摘要缓存命中：建立 SecurityContext
    → 未命中：GET backend:8080/internal/auth/introspect
       headers: Authorization + X-LiuTech-Internal-Token
       → 主服务内部令牌过滤
       → 主服务 JwtAuthenticationFilter
       → 当前 users 状态、密码摘要、角色校验
       → Result<{userId, username, role}>
```

规则：

- `/ai/chat` 无 token 时是游客，不落库。
- 带 token 但无效时返回 HTTP 401，不能退化成游客。
- 身份服务连接失败或异常时返回 HTTP 503，不能使用 token 内自称角色。
- `/ai/runtime`、`/ai/models/**`、`/ai/tts/audio/**`、健康检查不需要身份内省。
- `/ai/internal/**` 不走用户身份内省，只由内部服务令牌过滤器保护。

实现入口：[`RemoteAuthenticationFilter.java`](../../../../LiuTech-AI/src/main/java/chat/liuxin/ai/infra/filter/RemoteAuthenticationFilter.java)、[`AuthIntrospectionClient.java`](../../../../LiuTech-AI/src/main/java/chat/liuxin/ai/common/client/AuthIntrospectionClient.java)、[`AuthIntrospectionController.java`](../../../../LiuTech/src/main/java/chat/liuxin/liutech/controller/internal/AuthIntrospectionController.java)。

## TTS 数据流

```text
StreamingChatService 文本分段
  → AI TtsSpeechService.inferSingleAudioUrl
    → ai_tts_config 读取开关与供应商
    → GPT-SoVITS 或 SiliconFlow
    → /app/tts-cache/{uuid}.{format}
    → SSE audio { audioUrl: "tts/audio/{fileName}" }
  → Web 按 AI baseURL 解析并播放
```

管理端通过 `/ai/admin/tts/**` 读写配置、查询状态、上传/列出音色和试听。公共 `GET /ai/runtime` 同时返回默认模型与最小 TTS 状态；公共音频由 `GET /ai/tts/audio/{fileName}` 提供。

TTS 缓存是可再生临时数据，不挂载主服务 uploads，也不跨容器持久化。文件名只接受 UUID 加 `mp3/wav/opus/pcm`，目录规范化后必须仍位于配置的缓存根目录。

## 内部接口与公网边界

| 接口 | 调用方 | 行为 |
| --- | --- | --- |
| `GET /internal/auth/introspect` | AI → 主服务 | 返回当前最小身份，需用户 token + 内部 token |
| `DELETE /ai/internal/users/{userId}/data` | 主服务 → AI | 幂等删除单个用户会话与消息 |
| `POST /ai/internal/users/purge` | 主服务 → AI | 每次最多 100 个用户 ID |

Nginx 对 `/api/internal/**` 与 `/ai/internal/**` 直接返回 404；容器内通过 `backend:8080`、`ai:8081` 直连。两个服务读取同一个 `LIUTECH_INTERNAL_TOKEN`，比较使用常量时间算法。

用户软删除不清理 AI 数据；彻底删除前必须先收到 AI 清理成功响应。AI 不可用时彻底删除中止，避免产生无法归属的聊天数据。

## 故障语义

| 场景 | 对外结果 |
| --- | --- |
| 主服务离线、游客聊天 | 继续工作；博客查询工具可降级为空 |
| 主服务离线、请求携带用户 token | 503 身份服务暂时不可用 |
| token 无效、过期或用户状态失效 | 401，前端清理登录状态 |
| AI 服务离线 | 主站核心博客功能不受影响 |
| TTS 关闭/离线/单段失败 | 文本流继续，发送 `audio-skip` |
| AI 用户数据清理失败 | 主服务中止彻底删除用户 |

## 生产数据迁移

`Docs/SQL/sql.sql` 只用于新数据库。已有生产库先备份，再执行一次性最小迁移：创建 `liutech_ai.ai_tts_config`，将主库九个 `tts.*` 值聚合写入 `id=1`，验证字段一致后切换应用账户与镜像。主库旧值保留七天供整组回滚，之后再单独备份并删除。

数据库账户通过 `mysql/init-users.sh` 创建或更新。已有数据卷不会自动重跑初始化脚本，需要在维护窗口内显式执行该脚本并验证两个账户不能查询对方数据库。

## 陷阱与约束

- 不把 `JWT_SECRET` 注入 AI 服务，也不从 JWT claims 直接建立角色。
- 不恢复主服务 `/tts/**` 或 `AiRuntimeService`；运行时快照属于 AI 服务。
- 不把 TTS 缓存挂到 `/app/uploads`，避免两个服务共享文件所有权。
- 不通过公网 Nginx 调内部接口。
- 不对现有生产库执行完整 `Docs/SQL/sql.sql`。
