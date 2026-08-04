# TTS 与表情

> TTS 播放链路是看板娘最复杂的部分：SSE 事件乱序到达、音频与表情按序绑定、TTS 与音乐互斥、浏览器自动播放策略。
> 本文档描述队列保序、播放循环、音乐互斥、Avatar Cue 调度。

## 队列保序：useSequencedBuffer

SSE 事件到达顺序不一定等于播放顺序（网络抖动、并发处理）。[`useSequencedBuffer.ts`](../../../../Web/src/composables/useSequencedBuffer.ts) 用 seq 编号保证严格按序消费。

```
buffer: Record<seq, item>   按 seq 索引存储
nextSeq: number             下一个待消费的 seq（从 1 递增）

enqueue(item)    按 item.seq 入队（乱序到达也按 seq 存）
shift()          取 nextSeq 对应的项，nextSeq++（严格顺序消费）
shiftBySeq(seq)  取指定 seq 的项，推进 nextSeq（跨队列绑定用）
clear()          清空 + 重置 nextSeq=1
```

**关键**：`shift()` 会阻塞直到 `nextSeq` 对应的项到达。如果 seq=2 先到、seq=1 后到，`shift()` 第一次返回 null（seq=1 没到），消费方等待；seq=1 到达后 `shift()` 返回 seq=1 的项，nextSeq 推进到 2，下次 `shift()` 返回 seq=2 的项。

## 双队列：chatTts

[`chatTts.ts`](../../../../Web/src/composables/chatTts.ts) 管理两个独立队列：

| 队列 | 内容 | 消费方 |
| --- | --- | --- |
| `ttsAudioQueue` | TTS 音频项（seq, audioUrl, text, cue） | useTtsPlayer.playNextTts |
| `avatarCueQueue` | 表情/动作项（seq, expression, motion） | useTtsPlayer.applyNextAvatarCues |

### audio ↔ cue 按 seq 绑定

TTS 音频和表情是两个独立 SSE 事件，但需要同步播放（播某段音频时显示对应表情）。绑定逻辑在 `enqueueTtsAudio`：

```ts
const cue = avatarCueQueue.shiftBySeq(item.seq)  // 取出同 seq 的 cue
if (cue) enriched.cue = cue                       // 绑定到 audio item
ttsAudioQueue.enqueue(enriched)
```

播放时 `next.cue` 已是对应表情，直接 `applyAvatarCue`。

### 音频预加载

`enqueueTtsAudio` 对 ready 状态的音频立即创建 `Audio` 对象并 `preload`，减少播放时等待。

## 播放器：useTtsPlayer

[`useTtsPlayer.ts`](../../../../Web/src/composables/useTtsPlayer.ts) 是 TTS 播放的核心，接收 `chatStore + live2dRef + bottomNavRef`。

### playNextTts 播放循环

```
playNextTts()
  ├─ while (队列非空 && 未取消)
  │   ├─ shiftTtsAudioQueue()              取下一个音频
  │   ├─ applyAvatarCue(next.cue)          应用对应表情
  │   ├─ if skipped: continue              跳过的音频不播
  │   ├─ live2dRef.speakAudioElement/Url   交给 Live2d 播放+口型同步
  │   ├─ audio.play() 重试 6 次            NotAllowedError 不重试
  │   └─ 等待 ended/error/pause            超时 60s
  └─ finally: applyAvatarCue(neutral)      恢复中性表情
```

**重试策略**：`audio.play()` 失败时退避 `250 + attempt*200` ms 重试，最多 6 次。`NotAllowedError`（用户未交互）直接放弃，不重试。

### stopTtsPlayback

`playbackToken++` 使正在跑的循环 token 失效，`currentTtsAudio.pause()` 停止当前音频，`resumeMusicAfterSpeechIfNeeded` 恢复音乐。

## 4 个 watcher 桥接

useTtsPlayer 内部用 4 个 watcher 把 chatStore 状态变化桥接到播放动作：

| watch | 触发动作 |
| --- | --- |
| `[ttsEnabled, ttsAvailable, ttsPendingCount]` | 禁用时 stop + 清队列；启用且有待播时 playNextTts |
| `ttsCancelCounter` | 新消息发送，stopTtsPlayback |
| `avatarCuePendingCount` | applyNextAvatarCues（TTS 关闭时仍驱动表情） |
| `showModel` | 隐藏时 stop；显示时 play + applyCues + refresh |

**ttsCancelCounter**：`sendMessage` 时递增，watcher 监听到后停止旧播放。是响应式桥接，chatStore 单向通知播放器，不知道播放器存在。**不要改成回调 hook**（会让 chatStore 持有播放器回调，增加耦合）。

## Avatar Cue 调度

`applyNextAvatarCues` 消费 `avatarCueQueue`，逐个驱动 Live2d 表情：

```
applyNextAvatarCues()
  ├─ 前置条件：TTS 未在播、无待播音频、模型可见
  ├─ while (队列非空 && 模型可见)
  │   ├─ shiftAvatarCueQueue()
  │   └─ live2dRef.applyAvatarCue(next)
  │       └─ delay(120)              每个 cue 间隔 120ms
  └─ finally: isApplyingAvatarCue = false
```

**与 TTS 的关系**：TTS 在播时，cue 已绑定到 audio item（由 playNextTts 驱动）；TTS 关闭时，独立 cue 队列由 applyNextAvatarCues 消费。

## 音乐与 TTS 互斥

TTS 和音乐共用 Live2d 的 lipSync 单实例，必须互斥：

| 时机 | 处理 |
| --- | --- |
| 音乐播放（`handleMusicPlay`） | TTS 在播时不启动音乐口型（音乐本身仍播放） |
| TTS 播放前（`handleSpeakStart`） | 暂停音乐，标记 `shouldResumeMusicAfterSpeech=true` |
| TTS 结束（`resumeMusicAfterSpeechIfNeeded`） | 若标记为 true，调 `bottomNavRef.resumeMusic` 恢复 |

`shouldResumeMusicAfterSpeech` 是 useTtsPlayer 内部状态，只在 TTS 主动暂停音乐时标记，结束后恢复。

## 音频解锁

浏览器要求用户交互后才能播放音频。`unlockAudio` 在首次 pointerdown/keydown/touchstart 时创建静音 AudioContext + 振荡器播放 0.01s，解锁后续音频播放。

## 陷阱与约束

- **lipSync 单实例**：TTS 和音乐共用，不能同时驱动口型
- **seq 必须连续**：后端必须保证 seq 从 1 递增无缺失，否则 `shift()` 会卡住等待缺失的 seq
- **showModel=false 时停 TTS**：模型不可见时播放无意义，watcher 会 stop
- **chatStore 包装 chatTts 不要删**：是接口适配，让外部通过 chatStore 统一访问，封装 useSequencedBuffer
