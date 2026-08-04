# Live2d 渲染

> Live2d 模型基于 PIXI.js + Live2D Cubism Core，负责模型渲染、口型同步、表情动作驱动、尺寸自适应。
> 本文档描述 PIXI 生命周期、ticker 控制、口型同步、表情队列、resize。

## PIXI Application 生命周期

[`Live2d.vue`](../../../../Web/src/components/Live2d.vue) 在 `onMounted` 动态加载脚本后初始化：

```
loadLive2DScripts()                    按序加载 4 个脚本（pixi/live2dcubismcore/live2d/cubism4）
  └─ initLive2D()
      ├─ new PIXI.Application({ view, width, height, backgroundAlpha:0, antialias, resolution })
      ├─ if (!props.visible) app.ticker.stop()    初始不可见时停 ticker
      ├─ Live2DModelClass.from(model3.json)       异步加载模型
      │   ├─ anchor.set(0.5, 0.28)                锚点偏上
      │   ├─ scale.set(0.15)                      固定缩放
      │   ├─ 绑定 pointerdown/move/up             拖拽
      │   ├─ stage.pointermove -> model.focus     鼠标跟随
      │   └─ hit body -> motion('tap_body')       点击触发动作
      └─ startRandomMotion('idle')                自动眨眼+呼吸
```

**资源清理**（`onBeforeUnmount`）：移除事件监听、stopAllMotions、removeChild、destroy 模型、destroy PIXI app（含纹理）。`useLipSync.destroy` 关闭 AudioContext。

## ticker 控制（性能关键）

PIXI ticker 默认 60fps 持续渲染。模型不可见时必须停 ticker，否则空转耗 CPU/GPU。

```
props.visible
  ├─ true  -> app.ticker.start() + refreshRenderer()
  └─ false -> app.ticker.stop()
```

- `visible` prop 由 MainLayout 传 `:visible="chatStore.showModel"`
- `watch(visible)` 切换时执行
- `initLive2D` 创建 app 后立即检查 `props.visible`，初始不可见则 stop

**陷阱**：`is-hidden` CSS 只设 `visibility:hidden + pointer-events:none`，PIXI 仍渲染。必须配合 ticker.stop 才能真正省资源。

## 口型同步：useLipSync

内联在 Live2d.vue，基于 Web Audio API 分析音频 RMS 驱动 `ParamMouthOpenY`：

```
speakAudioElement(audio)
  ├─ attach(audio)                    MediaElementSource -> AnalyserNode
  ├─ rAF 循环:
  │   ├─ analyser.getByteTimeDomainData  取时域波形
  │   ├─ computeRms(buffer)             计算 RMS
  │   ├─ target = (rms - noiseFloor) * gain
  │   ├─ mouthSmoothed = 平滑插值（smoothIn/smoothOut 不同系数）
  │   └─ setMouthOpen(mouthSmoothed)    驱动 ParamMouthOpenY
  └─ audio pause/ended -> stop
```

**配置**：`noiseFloor=0.015, gain=14, smoothIn=0.78, smoothOut=0.88, curve=0.75`。通过 `defineExpose` 暴露 `lipSyncConfig` + `setLipSyncConfig` 可调。

**单实例**：lipSync 是模块级单例，TTS 和音乐共用。TTS 在播时不启动音乐口型（见 [TTS与表情](TTS与表情.md) 音乐互斥）。

## 表情与动作：applyAvatarCue

`applyAvatarCue(cue)` 驱动模型表情/动作，由 useTtsPlayer 调用：

```
applyAvatarCue(cue)
  ├─ pendingAvatarCue = cue
  ├─ 若距上次表情 < 1200ms:
  │   └─ pendingAvatarCueTimer = setTimeout(flush, 等待差值)   防抖
  └─ 否则 flushAvatarCue() 立即执行
      ├─ resolveExpressionName(cue)   happy/sad/angry/... -> 模型表情名
      ├─ model.expression(name)       设置表情
      ├─ model.motion(motion)         触发动作（3000ms 防抖）
      └─ expressionResetTimer          duration 后重置（默认 2600ms，clip 1600-6000）
```

**表情映射**：`expressionMap` 把语义名（happy/sad/angry/thinking/surprised/shy/confused/calm）映射到模型表情名。`neutral` 重置为 null。

**防抖**：`EXPRESSION_MIN_INTERVAL_MS=1200` 防止表情频繁切换；`MOTION_MIN_INTERVAL_MS=3000` 防止动作频繁触发。

## 尺寸自适应：ResizeObserver + resolution

窗口大小、容器尺寸、分辨率（DPR）变化时刷新渲染：

```
ResizeObserver(容器) + window resize
  └─ debouncedRefresh (150ms 防抖)
      └─ refreshRenderer()
          ├─ rawWidth/rawHeight = container.clientWidth/Height
          ├─ 跳过: DPR 和尺寸都没变 -> return
          ├─ app.renderer.resolution = devicePixelRatio   同步 DPR
          ├─ app.renderer.resize(width, height)           重设 canvas backing store
          └─ model.x/y = width/2, height/2                重新居中
```

**DPR 同步**：浏览器缩放（Ctrl+Plus）改变 `devicePixelRatio`，`renderer.resolution` 赋值 + `resize` 更新 canvas backing store，避免模糊。

**跳过逻辑**：`lastDpr/lastWidth/lastHeight` 记录上次值，都未变化时跳过（避免无意义 resize）。

**日志**：`[Live2D] refresh WxH @DPR (合并 N 次触发)` 打印触发情况，可据此判断是否异常。

## 交互

| 交互 | 触发 | 处理 |
| --- | --- | --- |
| 拖拽模型 | pointerdown/move/up on model | 移动 model.x/y |
| 鼠标跟随 | stage.pointermove | model.focus(x, y) 眼睛跟随 |
| 点击模型 | hit area 'body' | motion('tap_body') + 随机表情 |
| 滚轮（展开态） | wheel on canvas | preventDefault + scrollBodyBy 转发给聊天列表 |

`interactive` prop 控制是否启用交互（`applyInteractionMode`）。`followPointer` prop 控制非交互时是否鼠标跟随。

## 暴露的 API（defineExpose）

| 方法 | 用途 |
| --- | --- |
| `speakAudioUrl(url)` | 播放音频 URL + 口型同步 |
| `speakAudioElement(audio)` | 用已预加载的 Audio 播放 + 口型同步 |
| `applyAvatarCue(cue)` | 应用表情/动作 |
| `startMusicLipSync(audio)` | 音乐口型同步 |
| `stopMusicLipSync()` | 停止音乐口型 |
| `refresh()` | 手动触发 resize + render |
| `lipSyncConfig` / `setLipSyncConfig` | 口型参数 |

## 陷阱与约束

- **ticker 必须随 visible 停启**：否则隐藏时 60fps 空转
- **resolution 更新要配合 resize**：单独 set resolution 不生效，必须 resize 触发 backing store 重设
- **模型 scale 固定 0.15**：不随容器缩放，大容器模型偏小是设计选择
- **拖拽和交互依赖 ticker**：ticker 停时交互失效，但隐藏时不需要交互
- **资源清理必须彻底**：模型 destroy + PIXI app destroy（含纹理）+ AudioContext close，否则内存泄漏
