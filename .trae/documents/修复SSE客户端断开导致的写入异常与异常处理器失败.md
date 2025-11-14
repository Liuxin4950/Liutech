## 问题概览
- 错误1（SocketDispatcher.write0 / “你的主机中的软件中止了一个已建立的连接”）：典型于客户端在服务器写响应时断开连接（如 SSE/长连接、浏览器切换路由或网络中断）。
- 错误2（Failure in @ExceptionHandler GlobalExceptionHandler#handleGenericException）：异常发生后尝试返回JSON错误体，但连接已断开，写回再次失败，Spring 的异常解析器记录该警告。

## 代码定位
- SSE事件发送：`LiuTech-AI/src/main/java/chat/liuxin/ai/service/impl/AiChatServiceImpl.java`
  - 关键发送点：`emitter.send(...)` 在 `265`、`273`、`279`、`342`、`355` 行；异常分支 `completeWithError(ex)` 在 `372` 行。
- 全局异常处理器：`LiuTech-AI/src/main/java/chat/liuxin/ai/exception/GlobalExceptionHandler.java:141–154`（`handleGenericException`）。
- 端口：AI 服务 `8081`，主后端 `8080`；当前日志来源于 AI 服务（Tomcat `http-nio-8081`）。

## 改造方案
1. 服务端（SSE健壮化）
- 为 `SseEmitter` 注册 `onTimeout`、`onError`、`onCompletion`，统一回收资源与轻量日志。
- 封装 `safeSend(emitter, name, data)`：对 `IOException`/`IllegalStateException` 做特判：
  - 如 `ClientAbortException` 或消息含“Broken pipe/Connection reset/你的主机中的软件中止了一个已建立的连接”，仅 `warn` 记录并 `emitter.complete()`，不再 `completeWithError`，不再尝试追加任何发送。
  - 其他真实业务异常仍走错误事件与落库逻辑。
- 调整超时：`new SseEmitter(120000L)` 并在配置中设置 `spring.mvc.async.request-timeout=120s` 保持一致。

2. 反向代理/容器配置（若经Nginx或网关）
- 关闭SSE缓冲：`proxy_buffering off; proxy_request_buffering off;`。
- 延长读超时：`proxy_read_timeout 3600s;`，并确保 `Content-Type: text/event-stream` 不被篡改。

3. 前端配合
- 使用 `EventSource` 订阅 `/ai/chat/stream`；在组件卸载/路由切换时调用 `eventSource.close()`。
- 对 `error/complete` 事件做一致处理，避免快速开始-立即取消造成频繁“写入中止”日志。

4. 全局异常响应稳健性
- 维持 `GlobalExceptionHandler` 的 JSON 结构（当前使用 `HashMap` 已避免 `Map.of` 空值问题）。
- 对于 SSE 请求上下文产生的异常，不依赖全局异常器写回；由 SSE 通道内的错误事件向客户端告知。

## 验证方案
- 本地复现：前端打开流式聊天后立即关闭连接，观察服务端日志从“ERROR”转为“WARN/INFO”，不再出现 `Failure in @ExceptionHandler`。
- 正常流程：保持连接至 `complete`，确保仍能按序收到 `user/start/data/complete` 事件，并在错误路径收到 `error` 事件。
- 回归：普通 `/ai/chat` 一次性接口不受影响；异常时依旧返回标准化JSON。

## 预计改动点
- 修改 `AiChatServiceImpl` 的发送逻辑与超时；新增 `SseEmitter` 监听器；新增 `isClientAbort(Throwable)` 判定。
- 可选：在 `application.yml` 中同步设置异步请求超时；如使用Nginx，更新 `default.conf` 的SSE相关指令。

请确认是否按以上方案实施。我将据此完成代码改造与必要配置更新，并提供验证结果。