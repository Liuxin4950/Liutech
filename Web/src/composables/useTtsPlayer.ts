import { ref, type Ref } from 'vue'
import { getServiceBaseURL, ServiceType } from '@/config/services'

/**
 * TTS 播放器 composable。
 *
 * 职责：
 * - 从 store 顺序消费 TTS 音频队列，逐段播放
 * - 播放前应用对应的 avatar-cue 表情
 * - 处理浏览器 autoplay 策略和播放重试
 * - 提供停止播放和表情重置能力
 *
 * 数据流：
 *   store.enqueueTtsAudio() → ttsPendingCount 变化
 *   → 外部 watcher 调用 playNextTts()
 *   → 逐段取出音频 + cue → 应用表情 → 播放音频 → 等待结束 → 下一段
 *   → 全部播完 → 重置表情为 neutral
 */
export function useTtsPlayer(
  chatStore: {
    ttsEnabled: boolean
    ttsAvailable: boolean
    ttsAwaitingAudio: boolean
    ttsPendingCount: number
    shiftTtsAudioQueue: () => any
    shiftAvatarCueQueue: () => any
  },
  live2dRef: Ref<any>,
  showModel: Ref<boolean>
) {
  let isTtsPlaying = false
  /** 播放代次：每次 stopTtsPlayback 递增，用于取消正在进行的播放循环 */
  let playbackToken = 0
  let currentTtsAudio: HTMLAudioElement | null = null
  let isApplyingAvatarCue = false
  let audioUnlocked = false

  /** 将相对路径的音频 URL 拼接为主后端完整地址 */
  function resolveTtsPlayUrl(audioUrl?: string): string {
    if (!audioUrl) return ''
    if (audioUrl.startsWith('/')) {
      const base = getServiceBaseURL(ServiceType.MAIN).replace(/\/$/, '')
      if (base.startsWith('/') && audioUrl.startsWith(`${base}/`)) return audioUrl
      return `${base}${audioUrl}`
    }
    return audioUrl
  }

  /** 停止当前 TTS 播放，递增 playbackToken 使播放循环退出 */
  function stopTtsPlayback() {
    playbackToken++
    try { currentTtsAudio?.pause() } catch {}
    currentTtsAudio = null
    isTtsPlaying = false
    live2dRef.value?.resumeMusicAfterSpeechIfNeeded?.()
  }

  /** 等待音频事件触发，超时返回 false */
  function waitOnce(audio: HTMLAudioElement, event: string, timeoutMs: number): Promise<boolean> {
    return new Promise((resolve) => {
      let done = false
      const timer = window.setTimeout(() => {
        if (done) return
        done = true
        resolve(false)
      }, timeoutMs)
      audio.addEventListener(event, () => {
        if (done) return
        done = true
        window.clearTimeout(timer)
        resolve(true)
      }, { once: true })
    })
  }

  const delay = (ms: number) => new Promise<void>((r) => window.setTimeout(r, ms))

  /**
   * 播放 TTS 音频队列中的下一批音频。
   *
   * 流程：
   * 1. 从 store 取出下一个音频项（含绑定的 cue）
   * 2. 应用 cue 表情（skipResetTimer，由 finally 统一重置）
   * 3. 播放音频，处理 autoplay 重试（最多 6 次）
   * 4. 等待播放结束，继续下一段
   * 5. 全部播完后重置为 neutral 表情
   */
  async function playNextTts() {
    if (isTtsPlaying) return
    if (chatStore.ttsEnabled !== true || chatStore.ttsAvailable !== true) return
    if (!live2dRef.value) return

    const token = playbackToken
    isTtsPlaying = true

    try {
      while (token === playbackToken) {
        if (chatStore.ttsEnabled !== true || chatStore.ttsAvailable !== true) break
        if (!live2dRef.value) break

        const next = chatStore.shiftTtsAudioQueue()
        if (!next) break

        // 播放前应用对应的 avatar-cue，保持表情与音频同步
        // 跳过 neutral cue：中间段不需要重置表情，由 finally 统一处理
        if (next.cue && next.cue.expression !== 'neutral') {
          live2dRef.value.applyAvatarCue?.({ ...next.cue, skipResetTimer: true })
        }

        if (next.status === 'skipped') {
          console.warn(`[TTS][skip] seq=${next.seq} reason=${next.reason ?? 'unknown'}`)
          continue
        }

        const playUrl = resolveTtsPlayUrl(next.audioUrl)
        if (!playUrl) continue

        let audio = next.audioEl
          ? await live2dRef.value.speakAudioElement(next.audioEl)
          : await live2dRef.value.speakAudioUrl(playUrl)
        if (!audio) continue
        currentTtsAudio = audio

        // 尝试播放，处理浏览器 autoplay 策略
        // NotAllowedError 表示浏览器永久禁止了该上下文的自动播放，重试无意义
        // 其他错误（如网络抖动）用递增延迟重试：250ms, 450ms, 650ms, ...
        let started = false
        for (let attempt = 0; attempt < 6 && token === playbackToken; attempt++) {
          try {
            await audio.play()
            started = true
            break
          } catch (e: any) {
            if (e?.name === 'NotAllowedError') break
            try { audio.pause() } catch {}
            currentTtsAudio = null
            await delay(250 + attempt * 200)
            if (!live2dRef.value) break
            const retryAudio = await live2dRef.value.speakAudioUrl(playUrl)
            if (!retryAudio) break
            audio = retryAudio
            currentTtsAudio = audio
          }
        }

        if (!started) {
          try { currentTtsAudio?.pause() } catch {}
          currentTtsAudio = null
          continue
        }

        // 等待音频播放结束
        // ended: 正常播完; error: 播放出错; pause: 被外部停止（如 stopTtsPlayback）
        // 60 秒超时保护：防止异常情况下永久阻塞
        await Promise.race([
          waitOnce(audio, 'ended', 60000),
          waitOnce(audio, 'error', 60000),
          waitOnce(audio, 'pause', 60000)
        ])

        currentTtsAudio = null
      }
    } finally {
      if (token === playbackToken) {
        isTtsPlaying = false
        // 所有音频播完，恢复默认表情
        live2dRef.value?.applyAvatarCue?.({ expression: 'neutral' })
        live2dRef.value?.resumeMusicAfterSpeechIfNeeded?.()
      }
    }
  }

  /**
   * 消费独立的 avatar-cue 队列（TTS 关闭时的表情驱动）。
   *
   * 只在 TTS 未播放且无待播音频时执行，
   * 避免与 playNextTts 中的 cue 消费冲突。
   */
  async function applyNextAvatarCues() {
    if (isApplyingAvatarCue) return
    if (isTtsPlaying || chatStore.ttsAwaitingAudio || chatStore.ttsPendingCount > 0) return
    if (!live2dRef.value || !showModel.value) return
    isApplyingAvatarCue = true
    try {
      while (live2dRef.value && showModel.value) {
        const next = chatStore.shiftAvatarCueQueue()
        if (!next) break
        live2dRef.value.applyAvatarCue?.(next)
        await delay(120)
      }
    } finally {
      isApplyingAvatarCue = false
    }
  }

  /**
   * 解锁浏览器音频自动播放策略。
   * 在用户首次交互时调用，创建一个静音音频上下文来"解锁"后续播放。
   */
  async function unlockAudio() {
    if (audioUnlocked) return
    audioUnlocked = true
    try {
      const Ctx = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext | undefined
      if (!Ctx) return
      const ctx = new Ctx()
      const gain = ctx.createGain()
      gain.gain.value = 0
      const osc = ctx.createOscillator()
      osc.connect(gain)
      gain.connect(ctx.destination)
      try { await ctx.resume() } catch {}
      osc.start()
      osc.stop(ctx.currentTime + 0.01)
      window.setTimeout(() => { ctx.close().catch(() => {}) }, 50)
    } catch {}
  }

  return {
    stopTtsPlayback,
    playNextTts,
    applyNextAvatarCues,
    unlockAudio,
    get isTtsPlaying() { return isTtsPlaying }
  }
}
