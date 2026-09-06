# TTS、音乐与口型

> 播放状态由音频事件确认，模型就绪状态控制口型连接；语音偏好与服务在线状态独立。

## 文件地图

| 文件 | 职责 |
| --- | --- |
| [chat.ts](../../../../Web/src/stores/chat.ts) | 语音偏好、在线状态、取消代次、SSE 音频入口 |
| [chatTts.ts](../../../../Web/src/composables/chatTts.ts) | 音频/Avatar Cue 队列与预加载 |
| [useSequencedBuffer.ts](../../../../Web/src/composables/useSequencedBuffer.ts) | seq 保序，丢弃已经消费的旧序号 |
| [useTtsPlayer.ts](../../../../Web/src/composables/useTtsPlayer.ts) | TTS 消费循环、音乐互斥和模型就绪后补接 |
| [useAudioLipSync.ts](../../../../Web/src/composables/useAudioLipSync.ts) | 跨模型生命周期的声音主路、模型持有的分析支路 |
| [MusicCapsule.vue](../../../../Web/src/components/MusicCapsule.vue) | 实际音乐音轨、加载/错误、播放代次、用户操作版本 |
| [MainLayout.vue](../../../../Web/src/layouts/MainLayout.vue) | 按需加载模型/聊天组件、桥接 ready 与音频事件 |

## 状态来源

- `ttsEnabled` 是持久化的用户偏好；`ttsAvailable` 来自 runtime 检测，检测失败不再覆盖偏好。
- `cancelTts()` 增加 `ttsCancelCounter`、清队列并解除等待；流处理捕获自己的 `audioGeneration`，取消后迟到音频不能入队。
- `live2dStatus` 与 `showModel` 决定是否能消费 TTS；模型还未 ready 时不提前取走队列。
- MusicCapsule 的 `getCurrentAudio()` 只返回当前实际播放的人声或伴奏，`getActionVersion()` 表示用户主动操作版本。

## 顺序播放

```text
SSE audio / audio-skip → chatTts → useSequencedBuffer
  → 模型 ready 且语音允许时 shift 下一项
  → 应用同 seq 的 cue → speakAudioElement / speakAudioUrl
  → audio.play → 等待 ended/error/pause/取消/60 秒超时
  → 下一项；所有出口清理监听和计时器
```

`seq` 从 1 开始；缺少下一项时等待后续入队，不乱序播放。音频预加载先设置 crossOrigin 后设置 src。新消息、关闭语音和隐藏模型取消旧播放；重新开启只接收后续有效轮次。播放失败不进行无上限重试，音乐可恢复。

`avatarCueQueue` 通过 `shiftBySeq` 与对应音频绑定；没有 TTS 等待时可独立驱动模型表情。模型本身的表情映射与复位规则见 [Live2d渲染](Live2d渲染.md)。

## 音频图与模型生命周期

```text
HTMLAudioElement → 唯一 MediaElementAudioSourceNode → AudioContext.destination
                                      └→ 当前模型的 AnalyserNode → RMS → ParamMouthOpenY
```

声音主路和 source 注册表在 `useAudioLipSync` 模块中跨模型实例保留。模型 destroy 只移除分析支路、事件与 RAF；页面播放器退出时才关闭 AudioContext。分析连接失败不假定口型已成功连接。

`playing` 启动采样，`pause/ended/error` 停止采样并闭嘴；监听保留到解除绑定，因此暂停后再次播放可重新采样。已经播放的音乐在模型 ready/重新显示后由协调器查询快照并补接。

`resumeAudioContext()` 在真实用户交互中恢复实际使用的 context，等待上限 2 秒；不使用临时静音 context 冒充其它 context 已解锁。

## 音乐与 TTS

TTS 开始前暂停音乐并记录用户操作版本；分段之间等待音频时保持暂停。音频完成且队列清空，或者 TTS 取消/故障时，仅在用户操作版本未改变的情况下恢复音乐。用户切歌或手动播放优先，不能被旧轮次结束回调覆盖；音乐暂停事件不停止正在进行的 TTS 口型。

音乐音轨播放有 8 秒上限，等待任务可取消；结果按实际可播放轨道发布。双轨部分失败会显示降级提示，并选择可用音轨驱动口型。旧播放 Promise 不能写回新一轮状态。只有当前音轨仍有效的回调才能影响当前播放器。

## 约束与验证

- 不将用户偏好、服务状态、播放状态合并成一个 boolean。
- 同一 audio 不能被不同模型反复创建 source node；模型销毁也不能静音独立音乐。
- 全局只由 `useTtsPlayer` 协调 TTS 与音乐，不往 chat store 塞播放器回调。
- 真实音乐文件、TTS、跨域响应与浏览器播放许可仍需集成验证；单元测试只证明受控时序。
- 自动化覆盖见 [audioLipSync.test.ts](../../../../Web/src/__tests__/audioLipSync.test.ts)、[chatTts.test.ts](../../../../Web/src/__tests__/chatTts.test.ts)。
