# TTS 与认证边界

> 说明 AI 服务对 TTS 的完整所有权、主服务身份内省契约、内部接口保护和用户数据清理边界。

## 服务所有权

| 能力 | 所有者 | 数据/密钥 |
| --- | --- | --- |
| JWT 签发、验签、用户当前状态与角色 | 主服务 | `JWT_SECRET`、`liutech.users` |
| AI 身份上下文 | AI 服务 | 主服务内省成功结果，按 token SHA-256 摘要缓存 60s |
| TTS 配置、状态、推理、音色、临时音频 | AI 服务 | `liutech_ai.ai_tts_config`、AI 容器密钥与 `/app/tts-cache` |
| 用户彻底删除 | 主库删除与任务提交、AI 后台永久清理 | `LIUTECH_INTERNAL_TOKEN` 保护幂等清理接口 |

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

### 管理配置与试听

[`Admin/AiSettings.vue`](../../../../Admin/src/views/admin/AiSettings.vue) 将当前编辑草稿、已保存配置和服务探测状态分别保存。只有成功读取配置后才能提交；读取失败保留旧内容并锁定提交，不把初始值或旧快照当作当前配置。重新检测服务不重新加载表单；有未保存改动时，重新读取配置需先保存或放弃改动。保存失败保留草稿，保存成功按接口返回的配置更新已保存快照。

音色目录读取只更新选项，不自动选择第一项或替换已有音色。选择云端音色时同时选入其对应模型。`POST /ai/admin/tts/siliconflow/voice` 返回原始 `SiliconFlowVoiceDTO`（`model/customName/text/uri`），上传只创建供应商音色并返回 URI，不写 `ai_tts_config`；前端将模型/音色组合选入草稿，必须通过 `PUT /ai/admin/tts/config` 显式保存才替换全站语音。

试听始终调用已保存配置，生成期间阻止重复请求；用户可取消等待或停止播放，迟到响应只能清理本次音频。浏览器拒绝播放时保留本次音频供再次点击播放，避免重复合成。重新读到不同配置、页面退出/隐藏、KeepAlive 停用、保存新配置和停止试听都会暂停并释放当前音频，不清空配置草稿。取消浏览器请求不等同于供应商已经中止合成。

主操作（检测、保存、试听）位于首屏，音频格式、采样率、语速与手工 URI 放在高级配置，状态说明放在折叠诊断中。文本默认模型仅展示并链接到模型配置，不建立第二套默认模型设置。

TTS 缓存是可再生临时数据，不挂载主服务 uploads，也不跨容器持久化。文件名只接受 UUID 加 `mp3/wav/opus/pcm`，目录规范化后必须仍位于配置的缓存根目录。

## 内部接口与公网边界

| 接口 | 调用方 | 行为 |
| --- | --- | --- |
| `GET /internal/auth/introspect` | AI → 主服务 | 返回当前最小身份，需用户 token + 内部 token |
| `DELETE /ai/internal/users/{userId}/data` | 旧版本兼容 | 普通幂等清空，不标记永久删除 |
| `DELETE /ai/internal/users/{userId}/permanent-data` | 主库清理任务 → AI | 幂等永久清理，返回 permanentlyPurged=true；阻止旧身份重新创建数据 |
| `POST /ai/internal/users/purge` | 主服务 → AI | 每次最多 100 个用户 ID |

Nginx 对 `/api/internal/**` 与 `/ai/internal/**` 直接返回 404；容器内通过 `backend:8080`、`ai:8081` 直连。两个服务读取同一个 `LIUTECH_INTERNAL_TOKEN`，比较使用常量时间算法。

用户软删除不清理 AI 数据；永久删除与持久化任务在主库一起提交，AI 清理由独立后台任务执行。AI 不可用时任务保留重试，不在数据库事务中等待 HTTP。发布顺序、租约和永久用户状态见[数据库版本迁移](../../../SQL/migrations/README.md)。

## 故障语义

| 场景 | 对外结果 |
| --- | --- |
| 主服务离线、游客聊天 | 继续工作；博客查询工具可降级为空 |
| 主服务离线、请求携带用户 token | 503 身份服务暂时不可用 |
| token 无效、过期或用户状态失效 | 401，前端清理登录状态 |
| AI 服务离线 | 主站核心博客功能不受影响 |
| TTS 关闭/离线/单段失败 | 文本流继续，发送 `audio-skip` |
| AI 用户数据清理失败 | 已提交主库删除，关联清理任务保留并退避重试 |

## 生产数据迁移

`Docs/SQL/sql.sql` 只用于新数据库。已有生产库先备份，再执行一次性最小迁移：创建 `liutech_ai.ai_tts_config`，将主库九个 `tts.*` 值聚合写入 `id=1`，验证字段一致后切换应用账户与镜像。主库旧值保留七天供整组回滚，之后再单独备份并删除。

数据库管理员显式创建或更新应用账号，仅授予各自库的 CRUD；已有账号不随业务发布重建，调整后验证两个账户不能查询对方数据库。

## 陷阱与约束

- 不把 `JWT_SECRET` 注入 AI 服务，也不从 JWT claims 直接建立角色。
- 不恢复主服务 `/tts/**` 或 `AiRuntimeService`；运行时快照属于 AI 服务。
- 不把 TTS 缓存挂到 `/app/uploads`，避免两个服务共享文件所有权。
- 不通过公网 Nginx 调内部接口。
- 不对现有生产库执行完整 `Docs/SQL/sql.sql`。

## 状态与可播放格式

TTS状态含`configured/online/onlineVerified`。SiliconFlow仅配置齐全不代表已验证在线，首次允许尝试，真实合成结果确认状态；失败短暂降级后允许重试。GPT-SoVITS探测5xx/404判不可用，正常POST端点的GET405允许。语音失败通过`audio-skip`降级，不冒充文本成功或中断已完成的文本。

输出支持MP3/WAV/Opus，不保存原始PCM配置；旧PCM配置读出回退MP3。临时音频仍是公开UUID地址，持有地址即可访问，当前清空会话不撤销浏览器已缓存音频。

## 回归入口

- [TtsSpeechStatusTest](../../../../LiuTech-AI/src/test/java/chat/liuxin/ai/service/tts/TtsSpeechStatusTest.java) 覆盖真实合成确认状态、失败冷却与上传不隐式更换全局音色。
- [ttsSettings.test.ts](../../../../Admin/tests/ttsSettings.test.ts)、[ttsService.test.ts](../../../../Admin/tests/ttsService.test.ts) 覆盖配置快照、未验证状态、原始 DTO 和取消信号。
- 语音页面验收覆盖加载/保存失败、草稿保留、快速重复提交、音色上传与试听取消。原本地烟雾脚本已移除；单元测试仍由 Admin/tests/ttsService.test.ts 与 ttsSettings.test.ts 执行，页面检查使用隔离浏览器与模拟 API，不调用真实供应商。
