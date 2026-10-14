# Live2d 渲染与角色表现

> PIXI/Cubism 负责模型与物理，角色控制器统一处理口型、表情、姿态和注视。声音、队列与识别生命周期见 [TTS与表情](TTS与表情.md)。

## 入口与生命周期

[Live2d.vue](../../../../Web/src/components/Live2d.vue) 按需加载脚本、模型与四张纹理，发出 `load-start/ready/error`。模型资源就绪即可播放与接入口型，不等待温和表情资源下载。占位图、错误重试与 Performance 模型加载计时保留。

每次加载有独立代次，同时检查组件与 PIXI app 身份；重试、卸载后迟到的模型立即销毁。表情加载结果只补充所属控制器资源，不能重演加载发起时的 cue。

模型隐藏或页面进入后台时停止 ticker、分析采样及指针交互，清除过期表情。恢复时发出 `resume`，由播放协调器查询当前音频并补接；不重播音频。卸载移除媒体查询、页面可见性、指针、resize 和模型更新监听，销毁模型与 PIXI；共享声音主路继续由播放器持有。

## 单一参数写入入口

[avatarController.ts](../../../../Web/src/composables/avatarController.ts) 定义共用 `AvatarCue`、`AvatarState`、`VoiceRecognitionState` 与一模型一个的控制器。

```text
现有 store/播放器 → MainLayout 派生 presentationState
SSE cue / 点击 → 控制器的当前表情目标
音频 RMS → lipEnvelope → 控制器的开合目标
指针 → 有界注视目标
    ↓
Cubism beforeModelUpdate
    → 按运行时真实 ID、默认值及上下限混合参数
    → coreModel.update 绘制
    → 库原有 loadParameters 撤销本帧叠加
```

分析器不直接写 Cubism 参数；控制器不另开动画循环、不逐帧更新 Vue 状态。原有眨眼、呼吸与物理保留，头部/身体角度限制在温和范围；不存在的参数不会调用 SDK 创建虚拟参数。

## 角色状态

| 状态 | 来源与表现 |
| --- | --- |
| `idle` | 无识别或回复活动；安静待机 |
| `listening` | 识别器真实 `onstart` 后；回正关注，轻微调整眉头与头部 |
| `thinking` | 回复生成中；轻微偏头与现有思考姿态 |
| `preparing` | 首段/后段语音准备；保持思考姿态，不张嘴冒充讲话 |
| `speaking` | TTS 实际 `playing`；播放相应 cue，音频独占开合 |
| `buffering` | 实际缓冲或段间等待；闭嘴，保留当前情绪 |
| `music` | 协调器接入当前纯人声；仅由纯人声驱动开合 |

实际 TTS 播放优先于识别状态。`voiceInputActive` 仍表示监听或免提会话，不能用于判断角色是否正在听；`voiceRecognitionState` 区分 `idle/starting/listening`。

## 口型包络

[useAudioLipSync.ts](../../../../Web/src/composables/useAudioLipSync.ts) 使用 1024 点波形 RMS；[lipEnvelope.ts](../../../../Web/src/composables/lipEnvelope.ts) 处理滞回静音门、有界归一化与按毫秒计算的过渡。

| 默认参数 | 值与用途 |
| --- | --- |
| `noiseFloor` | 0.006；静音门打开/闭合阈值分别为其 1.6/1.15 倍 |
| `gain` | 6，限制在 2–8；归一化参考不低于 `1/gain`，持续低电平不被放大成讲话 |
| `curve` | 0.75 |
| `attackMs/releaseMs` | 30/45ms，约 95% 的过渡完成时间 |
| `silenceMs` | 25ms 静音确认 |

峰值参考按 1500ms 衰减，开合上限 0.92。暂停、缓冲、结束、取消、分析失败立即清空目标；受控测试覆盖 30/60/120Hz 下静音输入后 100ms 内降到 0.05 以下。

`ParamMouthOpenY` 始终由有效音频独占；`ParamMouthForm` 是情绪嘴角变化，不是音素。左右偏移、鼓腮和嘴部切换不作为语音识别结果使用。当前没有音素时间轴或逐字嘴形。

AudioContext 继续使用全页单例，不主动关闭。同一 audio 的 source node 只创建一次；挂起时不新建会重定向声音的分析源，先原生播放，恢复后补挂。分析失败仍通过现有可点击提示重试；重试等待期间若轮次改变，不补回旧音频。

## 表情、强度与姿态

控制器从现有 `.exp3.json` 加载温和表情，按参数原生范围计算目标，再应用强度，避免 `Add:30` 先放大后直接饱和。强度限制在 0–0.6，缺省 0.45；切换 250ms、复位 350ms。资源中的手部参数 `Param17` 运行时范围为 0–30，思考时过渡到已有姿态，结束时回收。

| 语义 cue | 资源 |
| --- | --- |
| `happy/sad/angry` | 开心1/伤心1/生气 |
| `thinking/shy` | 思考/害羞 |
| `surprised/confused` | 挑眉/半眼 |
| `neutral/calm` | 中性，不触发草元素或星星眼特效 |

所有入口均通过控制器，不直接调用 `model.expression`，不再随机选择全部 26 个表情。cue 默认持续 2600ms，时长限制 1600–6000ms；语音 cue 持续到整轮结束或取消。没有声音的 cue 可定时复位；模型晚到时，播放器只应用尚未过期的最新独立 cue。

模型没有 `Motions` 分组，因此不调用虚构的 `idle/tap_body`；兼容字段 `motion` 只有在资源确实提供同名动作时才调用，并保留 3 秒冷却。现有手部姿态不等同于挥手动画。

## 注视与交互

统一使用 DOM Pointer Events，不依赖模型缺失的 `HitAreas`。点击打开/关闭聊天，仅待机时短暂微笑或挑眉，表情冷却 3 秒；交流时点击不抢语音 cue。拖动超过 6 CSS 像素不触发点击，支持指针捕获与取消。

注视目标限制到 ±0.18，1500ms 无活动后回正；聆听与思考时关注正前方，说话时跟随幅度进一步减小。鼠标离开页面或失去焦点清除目标；触摸按下可短暂注视。

减少动态效果偏好通过 `matchMedia` 跟踪：关闭额外姿态与指针跟随，限制自然转头，保留音频开合。

角色表现迭代保留既有布局：模型容器位置、尺寸、层叠关系、展开/折叠规则和响应式断点不调整。

## 尺寸与暴露 API

ResizeObserver 与 window resize 共用 150ms 防抖；DPR/尺寸未变跳过刷新。模型保持原有 scale=0.15、anchor=(0.5, 0.28)，初始化与 resize 时仍按既有规则居中。

`defineExpose` 保留 `speakAudioUrl/speakAudioElement`、音乐口型入口、`retryMouthSync`、`applyAvatarCue`、`lipSyncConfig/setLipSyncConfig`、`refresh`，新增 `resetAvatar` 清空当前角色目标。

## 验证边界

[avatarController.test.ts](../../../../Web/src/__tests__/avatarController.test.ts) 覆盖包络、强度、帧内混合、无累计漂移、状态、复位、隐藏和减少动态；音频生命周期与播放时序分别见 [audioLipSync.test.ts](../../../../Web/src/__tests__/audioLipSync.test.ts) 和 [chatTts.test.ts](../../../../Web/src/__tests__/chatTts.test.ts)。

隔离 Chromium 使用实际模型与受控音频验证了参数强度、开口/暂停、思考姿态回收、拖拽/点击、隐藏停 ticker 和窄屏触摸；这是模拟环境，不代表真实手机 GPU、中文语音观感、麦克风权限或真实供应商延迟验收。
