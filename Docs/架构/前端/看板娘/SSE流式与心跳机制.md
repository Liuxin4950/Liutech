# SSE 流式与心跳保活机制（含两次线上事故复盘）

> 本文从零讲清楚：LiuTech 的 AI 流式回复（看板娘聊天 / 写作助手）在浏览器 → CDN → nginx → ai 服务 → 大模型 API 这条链路上怎么工作、为什么会断流、心跳是怎么保活的。基于 2026-08-04 两次线上事故（写作助手必现失败）复盘写成，读完能独立排查同类问题。

## 1. 一次写作请求的完整旅程

用户在 `/create`（写作助手）点"生成"后，请求的实际路径：

```
浏览器 (Chrome, HTTP/2)
  │  TLS 握手在这里终止，证书是腾讯云 CDN 的
  ▼
腾讯云 CDN 节点 (liuxin.chat → CNAME)
  │  CDN 立即回源（/ai/ 实时回源，不缓存），与源站保持另一条连接
  ▼
源站 nginx :443 (HTTP/1.1)
  │  proxy_pass http://ai_servers → ai:8081
  │  proxy_buffering off   ← SSE 必需，否则 nginx 缓冲整段再吐，失去流式
  ▼
liutech-ai 容器 :8081 (Spring Boot + Spring MVC)
  │  SseEmitter 持有连接，逐块推送事件
  ▼
SiliconFlow 大模型 API（DeepSeek-R1，上游 HTTP/2）
```

**关键认知：这不是"一次请求一次响应"，而是"一次请求，一条保持打开的长连接，服务端持续往里面写数据，直到写完才关闭"。** 这条链路上有 4 个节点（CDN、nginx、ai、浏览器），任何一个节点判定"这条连接死了"就会主动掐断，下游立刻报错。

- 看板娘聊天：`POST /ai/chat/stream`（`AiChatController.streamChat`）
- 写作助手：`POST /ai/writing/stream`（`AiChatController.writingStream`，需 ADMIN）
- 两个入口最终都进同一个 `StreamingChatService`，只是参数不同（见第 3 节）

## 2. SSE 是什么

SSE（Server-Sent Events）是浏览器原生支持的"服务器推送"协议：

```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache

event: start
data: {"conversationId":7,"model":"deepseek-ai/DeepSeek-R1"}

event: data
data: {"content":"你好"}

event: complete
data: {"responseLength":100}
```

每条事件用空行分隔，`event:` 是事件名，`data:` 是 JSON 载荷。前端用 `fetch` 拿到 `response.body` 的 ReadableStream 逐块读（`Web/src/services/aiStream.ts`），按 `event:` 分发。

**SSE 与传统 HTTP 响应的区别**：传统响应服务器写完就关闭；SSE 响应头一发完，连接保持打开，服务器想写多久写多久。代价是——连接空闲时，任何中间设备都可能把它当"僵尸连接"回收。

**什么是空闲超时**：CDN、nginx、浏览器都对"连接上多久没有数据流动"有上限（CDN 通常几十秒）。到了上限就主动断开，回收资源。SSE 流式回复最怕两件事：

1. **长时间没有数据可推**（大模型在"思考"、在调工具）→ 触发空闲超时 → 连接被掐
2. **数据推了一半被上游掐断**（大模型 API 繁忙、流被重置）→ 整条连接异常终止

## 3. 服务端实现：SseEmitter + StreamingChatService

Spring MVC 的 `SseEmitter` 就是"持有一条响应连接的对象"：

- `new SseEmitter(timeout)` 创建，构造函数里传的是**服务器端整体超时**（`aiChatProperties.getSseTimeout()`）
- `emitter.send(SseEventBuilder)` 往连接写一条事件
- `emitter.complete()` 正常收尾关闭；`emitter.completeWithError(e)` 异常终止
- `emitter.onCompletion(...)` / `emitter.onTimeout(...)` 注册收尾回调（必须在这里关掉自己起的线程池，否则泄漏）

`StreamingChatService`（`LiuTech-AI/.../service/StreamingChatService.java`）是流式核心，两个入口：

| | 看板娘 `processStreamChat` | 写作助手 `processWritingStream` |
| --- | --- | --- |
| 模型 | 数据库默认模型（DeepSeek-R1） | 同左 |
| 工具 | BlogMcpTools（查文章/推荐） | WritingTools（写标题/摘要/正文/选分类标签） |
| 消息落库 | 落库（会话历史） | 不落库（草稿在前端） |
| max_tokens | 数据库配置（R1=8192） | `writingParameters()` 加工（曾出事故①） |
| 心跳线程 | **有**（15s 一个 heartbeat） | **原本没有**（事故②，已修复） |

两个入口的骨架完全一样：

```java
SseEmitter emitter = new SseEmitter(aiChatProperties.getSseTimeout());
// 1. 注册收尾回调：流完成/超时时，必须关掉心跳线程和 TTS 线程池
emitter.onCompletion(() -> { ... shutdown(heartbeat); shutdown(ttsExecutor); });
emitter.onTimeout(() -> { ... shutdown(...); emitter.complete(); });

// 2. 丢到独立线程池跑，不占 Servlet 线程（避免长流阻塞 Tomcat 工作线程）
runOnStreamPool(() -> {
    // 3. 组装消息 → 发 start 事件 → 起心跳 → 订阅上游 Flux → 逐块转发
    SseEmitterHelper.sendSseEvent(emitter, "start", ...);
    Flux<String> flux = siliconFlowChatClient.streamChat(messages, modelName, ...);
    subscribeStream(emitter, flux, ...);
});
return emitter;   // 立刻返回，真正的流式在后面异步跑
```

`subscribeStream`（同一个私有方法，`writingMode` 参数区分）订阅上游 Flux 的三个回调：

- **onNext**（每来一个文本块）：`handleWritingChunk` 解析标记（写作模式）→ 切 TTS 段落 → 发 `avatar-cue` → 发 `data` 事件给前端
- **onError**（上游断了）：打日志 → 落库 partial → 发 `error` 事件 → `emitter.completeWithError`
- **onComplete**（上游写完）：flush 剩余 → 抽文章引用 → 发 `article-results` + `complete` → 等 TTS（若有）→ `emitter.complete()`

## 4. 心跳保活原理

### 4.1 为什么要心跳

写作助手的流式不是均匀输出的。DeepSeek-R1 是推理模型，典型时间线：

```
0s      请求发出
0~30s   模型"思考"（reasoning），可能一个字节都不推
30s+    开始吐正文，但调分类/标签工具时又停顿几秒
60s+    继续吐……几百到几千字要吐几十秒到几分钟
```

任何一个停顿超过 CDN 的空闲超时，CDN 就断开回源连接。nginx 收到上游断开，也关闭下游；浏览器端表现就是 `ERR_HTTP2_PROTOCOL_ERROR`（响应头已经收到、数据没收到完、连接被对端重置）。

**心跳 = 定时往连接里塞"空数据"**，让链路每时每刻都有数据流动，空闲超时永远不触发。浏览器对没有意义的 `heartbeat` 事件零感知。

### 4.2 心跳的实现（看板娘版，写作助手已对齐）

`StreamingChatService.java` 的 `processStreamChat`：

```java
// 心跳线程：单线程调度器，发完 start 事件后启动
ScheduledExecutorService hb = Executors.newSingleThreadScheduledExecutor();
heartbeatRef.set(hb);   // 放进引用，onCompletion 里能关掉
hb.scheduleAtFixedRate(() -> {
    if (emitterClosed.get()) return;   // 流已结束就别发了
    try {
        SseEmitterHelper.sendSseEvent(emitter, "heartbeat",
            SseEmitterHelper.eventPayload("conversationId", finalConvId,
                                          "timestamp", System.currentTimeMillis()));
    } catch (Exception e) {
        log.debug("心跳发送失败: {}", e.getMessage());
    }
}, HEARTBEAT_INITIAL_DELAY_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
// HEARTBEAT_INITIAL_DELAY_SEC = 15（首推延迟，避免短流白开线程）
// HEARTBEAT_INTERVAL_SEC = 15（发送间隔，必须 < 链路任何一环的空闲超时）
```

三处配套的收尾（防线程泄漏）：

```java
emitter.onCompletion(() -> {
    emitterClosed.set(true);
    SseEmitterHelper.shutdown(heartbeatRef.getAndSet(null), true);   // 关心跳
    SseEmitterHelper.shutdown(ttsExecutorRef.getAndSet(null), true); // 关 TTS 池
});
```

### 4.3 前端怎么容忍 heartbeat

`Web/src/services/aiStream.ts` 的事件分发：

```ts
case 'audio':
case 'audio-skip':
case 'audio-complete':
case 'avatar-cue':
case 'heartbeat':
  // 音频/保活事件，直接透传
  onEvent?.(eventType, parsedData)
  break
```

`heartbeat` 进 `onEvent` 回调后，业务层不渲染任何东西，天然忽略。所以给写作助手补心跳**不需要动前端**。

### 4.4 陷阱：心跳间隔必须小于空闲超时

- 心跳 15s 一次，要求链路上最严格节点的空闲超时 > 15s（腾讯云 CDN 空闲超时约 60s，安全）
- 若以后换了更严格的 CDN/代理，把 `HEARTBEAT_INTERVAL_SEC` 调小（如 10s），不要动其他逻辑
- nginx 侧 `proxy_read_timeout 3600s` 已配置，不是瓶颈

## 5. 事故①：写作助手失败（SiliconFlow 繁忙限流）

> 本节结论经历了一次推翻：最初误判为"max_tokens 越界必拒"，复测后确认真实根因是上游繁忙限流。下面保留完整复盘，警示"单次对照样本 + 瞬时故障窗口"组合容易得出错误结论。

### 症状

`/ai/writing/stream` 请求失败，返回 200 但 body 只有 305 字节（SSE error 事件）；看板娘聊天当时正常。ai 服务日志：

```
com.openai.errors.InternalServerException: 503:
{"code":50508,"message":"System is too busy now. Please try again later."}
```

### 排查路径（含误判）

1. 直接 curl 接口 → 401 → 一度怀疑鉴权。看 nginx access log 发现**用户浏览器请求是 200**（`POST /ai/writing/stream 200 305`），401 只是自己没带 token 的复现
2. 对比 JWT_SECRET 两服务哈希 → 一致，排除鉴权
3. 看 ai 容器内日志文件（`/app/logs/liutech-ai-error.log`，logback 没把业务日志输出到 docker stdout，必须 exec 进容器看文件）→ 503 busy
4. 用生产 key 直接 curl SiliconFlow，同模型不同参数对照：

```
max_tokens=32768 → 429 Too Many Requests   （写作参数）
max_tokens=8192  → 正常返回                （看板娘参数）
```

**误判**：当时据此断定"32768 越界被上游拒绝"，结论是写作模式的 `writingParameters()` 用 `Math.max` 把 max_tokens 顶到 32768 所致，遂改为 `Math.min`（8192）。但这两次测试是**先后执行**的，恰好跨越 SiliconFlow 繁忙窗口（22:16-22:23 高峰期），429 实为**限流**而非参数拒绝。

### 复测推翻

SiliconFlow 恢复后（当日稍晚）重跑同一对照，**max_tokens=32768 同样成功返回**（SiliconFlow 对超上限参数做钳制而非拒绝）。结论修正：

- **真实根因：SiliconFlow 对 DeepSeek-R1 的瞬时繁忙限流**（503 "System is too busy" / 429），写作助手撞上限流窗口失败，看板娘当时只是恰好没撞上（或短请求更容易挤过）
- max_tokens 越界结论不成立；`Math.min` 改动属于**防御性改进**（不依赖上游钳制行为，其他模型/平台不一定会钳制），保留但不应归为根因修复

### 保留的代码改动

`AiChatServiceImpl.writingParameters()`：`Math.max` → `Math.min`（取数据库配置与全局 writingMaxTokens 的较小值）。价值：对不钳制越界参数的模型/上游避免 4xx；代价：R1 写作输出上限被压到 8192（其默认配置值），若 R1 长文生成需要更长输出，应改数据库 per-model max_tokens 而非动代码。

**教训：对照实验要控制"时间"变量——瞬时故障（限流/繁忙）窗口内先后跑的两组样本，差异可能来自时间而非参数；结论必须在上游恢复后复测确认，否则会把巧合当因果。**

## 6. 事故②：长文必断流（写作助手无心跳）

### 症状

SiliconFlow 限流缓解后（限流是瞬时的，非代码修复），**长写作请求必断**：浏览器 `net::ERR_HTTP2_PROTOCOL_ERROR 200 (OK)`——状态 200 说明连接建立且响应头已发，然后流被掐。

### 排查证据链

1. 服务器本地 curl（直连源站 nginx）长写作 → 流式正常，排除上游和 ai 服务本身
2. 走 CDN 域名（`https://liuxin.chat`，带浏览器 UA 绕过 CDN 反爬）长写作 → **`curl: (92) HTTP/2 stream error`**，输出在 6460 字节处截断——CDN 路径必现
3. 对照：CDN 路径短请求（chat/writing）都完整返回 → 断流与"长"强相关
4. nginx access log：该请求记录 `200 7007`——**nginx 把完整 7007 字节都转发给了 CDN** → 断点在 CDN 层，不在源站
5. ai 服务日志：无新错误 → ai 侧正常写完
6. 读代码对比看板娘与写作助手 → `processWritingStream` **没有心跳线程**（注释明写"无心跳线程"），`processStreamChat` 有

结论：R1 长文生成存在数十秒的数据停顿（推理、工具调用），写作助手没有心跳填充，CDN 空闲超时掐断长流。看板娘有心跳，所以"live2d 可以、写作不行"。

### 修复

`processWritingStream` 补齐与看板娘一致的心跳：

```java
ScheduledExecutorService hb = Executors.newSingleThreadScheduledExecutor();
heartbeatRef.set(hb);
hb.scheduleAtFixedRate(() -> { ... 发 heartbeat 事件 ... },
        HEARTBEAT_INITIAL_DELAY_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
```

同时在 `onCompletion`/`onTimeout` 里 `shutdown(heartbeatRef.getAndSet(null), true)`，并新增 `AtomicReference<ScheduledExecutorService> heartbeatRef` 声明。

### 验证（修复后）

```
curl exit=0（修复前 92）
输出 67061 字节完整（修复前 6460 字节截断）
heartbeat 事件出现 4 次（长文生成期间持续保活）
结尾 event:complete 正常
```

## 7. 这类问题的通用排查套路

1. **分层定位**：先确认断在哪一层。浏览器 F12 Network 看状态码和响应大小；服务器 curl `localhost`（绕 nginx）→ `https://域名`（走 CDN）→ `docker exec` 容器内 curl 上游 API，逐层逼近
2. **看两边日志**：nginx access log 记录它发出多少字节（`200 7007`）；ai 服务日志（容器内文件 `/app/logs/*.log`，logback 不写 stdout！）记录上游是否正常。nginx 完整转发 + ai 无错误 = 断在 CDN
3. **对照实验**：同参数跑正常/异常两条路径（短 vs 长、chat vs writing、直连 vs CDN），差异点就是嫌疑
4. **SSE 三件套检查**：nginx `proxy_buffering off`、`proxy_read_timeout` 足够、服务端有 < 空闲超时的心跳

## 8. 关键文件索引

| 文件 | 职责 |
| --- | --- |
| `LiuTech-AI/.../service/StreamingChatService.java` | 流式核心：两个入口 + 心跳 + subscribeStream |
| `LiuTech-AI/.../service/impl/AiChatServiceImpl.java` | writingParameters（max_tokens 加工）、同步接口 |
| `LiuTech-AI/.../service/SiliconFlowChatClient.java` | 上游客户端：@Retryable 只拦订阅前调用，流内异常不重试 |
| `LiuTech-AI/.../infra/security/AiModelPolicy.java` | 模型选择：数据库默认模型 > yml 兜底 |
| `LiuTech-AI/.../controller/AiChatController.java` | 四个端点路由 + 鉴权 |
| `Web/src/services/aiStream.ts` | 前端 SSE 读取与事件分发（heartbeat 兼容） |
| `nginx/conf.d/ai-proxy.include` | SSE 代理配置：buffering off、超时、CORS |

## 陷阱清单

- ai 服务 logback 业务日志不输出到 docker stdout，`docker compose logs` 看不到业务日志；排查必须 `docker exec liutech-ai` 看 `/app/logs/` 下的文件
- `@Retryable`/`@CircuitBreaker` 注解对**返回 Flux 的流式方法**几乎无效——重试发生在"订阅前"的方法调用阶段，实际 HTTP 请求在订阅时才发起，流内异常直接走 onError
- max_tokens 建议以数据库 per-model 配置为上限（`Math.min` 防御）；但不同上游对越界参数处理不一（SiliconFlow 钳制不报错），验证结论别依赖单次对照样本
- CDN 对非浏览器 UA 返回 418 反爬，服务器侧验证要走 CDN 时必须带浏览器 UA
- 心跳间隔必须小于链路最严格节点的空闲超时；换 CDN/代理后要复查
