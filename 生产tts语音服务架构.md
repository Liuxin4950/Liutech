# 生产 TTS 语音服务架构（Liutech）

本文记录当前 Liutech 的「流式文本 → 分段触发 TTS → 返回音频 URL → 前端队列播放 + Live2D 口型」的实现架构、关键配置、以及本次落地过程踩过的坑与对应修复方案。

> 目标：在不影响“文本流式体验”的前提下，让语音尽可能“边出字边出声”，并能在本地/内网穿透/云端网页等多种部署方式下稳定工作。

---

## 1. 总体架构与职责边界

### 1.1 组件划分

- **TTS 服务（独立进程）**
  - 提供 `/infer_single`：输入文本 → 产出 `audio_url`
  - 提供 `/outputs/*.wav`：静态音频文件服务（支持 Range/206）
  - 运行位置：通常在开发机/自建机器（可能通过内网穿透提供公网访问）

- **AI 服务（LiuTech-AI）**
  - 负责：文本流式生成、分段规则、触发 TTS 推理、向前端 SSE 推送 `audio` 事件
  - 只推送“音频 URL”，不下载/代理音频，尽量减少链路开销

- **主服务（LiuTech）**
  - 负责：管理端配置的持久化与对外查询
  - 对外提供 `/tts/status`、`/tts/config` 供 AI/Web 查询
  - 本次最终方案：不再承担音频代理/转发（追求最低延迟）

- **Web 前端（Web）**
  - 负责：接收 SSE 的 `audio` 事件 → 放入队列（带 seq）→ 预加载音频 → 顺序播放
  - 播放由 Live2D 组件驱动：音频播放 + 同一音频驱动口型

### 1.2 数据流（核心链路）

1. 管理端配置 TTS：
   - `tts.enabled`、`tts.baseUrl`（应为客户端可访问地址：内网穿透域名/局域网 IP 等）
2. Web 发送聊天请求（流式）到 AI 服务：
   - `ttsEnabled=true` 时，AI 服务才会做“分段 → 触发 TTS”
3. AI 服务：
   - 持续接收大模型 chunk（文本流式）
   - 把 chunk 累积进 `ttsBuffer`
   - 按分段规则切分出 `segment` 后立刻异步调用 TTS `/infer_single`
   - 得到 `audio_url` 后立刻通过 SSE 发送 `event: audio {seq, text, audioUrl}`
4. Web 前端：
   - 收到 `audio` 事件后立即 `enqueue`，并预加载该 `audioUrl`
   - 播放逻辑按 `seq` 顺序取出下一段，交给 Live2D 播放
5. Live2D：
   - 使用同一个 `HTMLAudioElement` 驱动口型 + 播放

---

## 2. 分段规则（AI 服务侧）

### 2.1 现行规则（避免短句浪费）

分段逻辑在 AI 服务 `extractTtsSegments` 中实现（对 `ttsBuffer` 进行切分）：

- **必须累计 ≥ 20 字** 才允许触发“按标点切段”
- **优先按标点切**（。！？；换行 / 英文 ! ? ;）
- **兜底强制切分**：若一直没有标点，累计到 **45 字** 强制切一段（避免长时间不出声）

这样可以避免“5 个字也推理一次”的浪费，又不会因为完全没标点导致语音长期不出。

> 注意：分段只影响 TTS，不影响文本流式 `data` 事件的输出节奏。

---

## 3. SSE 事件设计（AI → Web）

### 3.1 事件类型

- `start`：会话开始（conversationId、model）
- `data`：文本 chunk（流式展示用）
- `audio`：TTS 音频片段（seq、text、audioUrl、conversationId）
- `complete`：流结束（conversationId、responseLength）

### 3.2 seq 的作用

TTS 推理是异步的（甚至可能并发），`audio` 事件到达顺序不一定与文本出现顺序一致。

因此：

- AI 服务为每段生成 `seq`（从 1 递增）
- Web 前端按 `seq` 缓存并严格按 `nextSeq` 播放

---

## 4. Web 前端播放架构（队列 + 预加载）

### 4.1 队列结构（按 seq）

- 收到 `audio` → 存入 `seq -> item` 的 buffer
- `nextSeq` 指向“下一段应该播放的 seq”
- 只有当 buffer 中存在 `nextSeq` 才会出队播放，播放完 `nextSeq++`

### 4.2 为什么要“预加载”

本次最大的坑之一是：即使 AI 端已经发了 `audioUrl`，浏览器并不一定立刻对音频资源发起网络请求（尤其在媒体策略未解锁/浏览器行为差异时）。

解决方案：

- **收到 `audioUrl` 的瞬间**就 `new Audio(url); audio.preload='auto'; audio.load()` 预加载
- 播放时尽量复用这个已经创建/预加载的 `audioEl`，避免重新创建导致再次等待

### 4.3 媒体播放解锁（Autoplay Policy）

另一类坑是：浏览器可能要求“用户交互后才能播放音频”。

处理方式：

- 在用户点击“发送消息/回车发送”的同一事件里，做一次轻量的 media prime（AudioContext resume / 静音短音频播放）
- 这样后续由 SSE 驱动的播放更容易成功

---

## 5. 本次落地踩过的坑与修复记录

### 5.1 坑：音频 URL 是 127.0.0.1，代理 502 / 云端不可达

现象：

- Web 收到 `audioUrl=http://127.0.0.1:8000/outputs/...`
- 对云端网页/非本机访问必然不可达

最终方案（以最低延迟为目标）：

- **不做主服务音频代理**
- AI 端拿到 `audio_url` 后做一次“地址归一化”：
  - 若返回的是相对路径或 `127.0.0.1/localhost`，拼接/替换为管理端配置的 `tts.baseUrl`
  - 直接把“客户端可访问的 URL”推给前端

### 5.2 坑：主服务代理（byte[]）导致“必须下载完才开始播”

现象：

- `/tts/proxy` 返回 `byte[]` 会导致服务端先把整段音频拉完，浏览器才能开始播放
- 表现为 `speakAudioUrlMs` 巨大（十几秒甚至更久）

处理：

- 最终直接移除音频代理，追求链路最短、延迟最低

### 5.3 坑：SSE complete 过早，导致后续音频生成了但发不出来

现象：

- TTS 推理确实跑完了（TTS 服务有日志）
- 但 Web 收不到后续 `audio`，播放到一半就结束

原因：

- AI 服务在 `complete` 回调里对 TTS futures 只等待固定时间（例如 30s），随后 `emitter.complete()` 关闭 SSE
- 剩余 TTS 任务即使完成，也无法再向已经关闭的 SSE 发送 `audio`

修复：

- 在流式结束时，把“等待 TTS 任务完成并发送最终 complete”的动作放到异步线程中执行
- 等待时间上限使用 `sseTimeout`（默认 120s）以适配长文

### 5.4 坑：短句切分导致“5 个字也推理一次”

现象：

- 文本里有很多逗号/换行/短句，导致频繁调用 TTS，浪费推理时间

修复：

- 将切分条件调整为：必须累计 ≥ 20 字后，才允许按标点切分发送
- 无标点情况下，到 45 字强制切一段

### 5.5 坑：并行推理过高导致系统抖动/资源争抢

现象：

- AI 侧对多个段落并行触发 TTS
- 同时 TTS 自身也可能开启并行合成
- 结果：整体延迟更不稳定、首段更慢

修复建议：

- AI 侧控制并发：`tts.stream.concurrency` 默认 1（优先保证首段稳定）
- TTS 请求参数关闭 `parallel_infer`（由上层控制并发即可）

---

## 6. 配置要点（生产/自用推荐）

### 6.1 管理端（主服务）TTS 配置

- `tts.enabled`：开启后，Web UI 才允许打开语音
- `tts.baseUrl`：必须是“客户端可访问”的地址
  - 本机测试：`http://127.0.0.1:8000`
  - 局域网：`http://192.168.x.x:8000`
  - 云端网页访问本机：使用内网穿透域名（建议 https，避免 Mixed Content）

### 6.2 AI 服务

- `blog.api.url`：AI 用它访问主服务 `/tts/status`（在线探测/获取 baseUrl）
- `tts.infer.model-name`：TTS 模型名称
- `tts.stream.concurrency`：TTS 段落并行数（默认 1）
- SSE 超时：`spring.ai.sse.timeout`（建议 ≥ 120s，长文必需）

### 6.3 Web（浏览器侧）

- 若网页是 `https`，音频 URL 也必须 `https`，否则会触发 Mixed Content 拦截
- 若发现第一次播放“必须点一下页面/打开链接才响”，优先检查：
  - 是否在“发送消息”事件里做了媒体解锁
  - 是否启用了预加载（prefetch）

---

## 7. 快速排障清单（按优先级）

1. **audioUrl 是否可在当前浏览器直接打开？**
   - 打不开：必然播放不了（先解决网络可达性/https/http）
2. **是否触发了 `[TTS][prefetch]`，并且 TTS 服务收到了 GET /outputs？**
   - 没有：浏览器未发起请求（多半是媒体策略/混合内容/URL 不可达）
3. **是否出现 `[TTS][playing]`？**
   - 没有：检查 autoplay policy、音频是否被拦截、控制台是否有 NotAllowedError/Mixed Content
4. **是否出现 `complete` 但后续音频没发？**
   - 检查 AI 服务是否在关闭 SSE 前等待了 TTS futures（长文需要更长的 `sseTimeout`）

---

## 8. 关键实现位置（便于回溯）

- AI 分段与 SSE：`LiuTech-AI/src/main/java/chat/liuxin/ai/service/impl/AiChatServiceImpl.java`
- AI 调用 TTS：`LiuTech-AI/src/main/java/chat/liuxin/ai/client/TtsClient.java`
- Web 队列/预加载：`Web/src/stores/chat.ts`
- Web 播放驱动：`Web/src/layouts/MainLayout.vue`
- Live2D 播放与口型：`Web/src/components/Live2d.vue`、`Web/src/composables/useLipSync.ts`

