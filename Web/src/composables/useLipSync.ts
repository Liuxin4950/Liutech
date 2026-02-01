export type MouthController = (mouthOpenY01: number) => void

export interface LipSyncConfig {
  /**
   * 口型“静音门限”
   * - RMS 低于该值视为接近静音，嘴巴逐步闭合
   * - 云端音频底噪更明显时，可适当调小，让轻声也能张嘴
   */
  noiseFloor: number
  /**
   * 口型“增益”
   * - 值越大，张嘴幅度越大
   * - 若出现“动不动就满嘴/喘气也很夸张”，把它调小
   */
  gain: number
  /**
   * 平滑系数（张嘴更快/闭嘴更慢）
   * - 越接近 1 越平滑（更跟得慢）
   * - 越接近 0 越灵敏（更抖）
   */
  smoothIn: number
  smoothOut: number
  /**
   * 曲线（非线性）
   * - 小于 1：小声更容易张开（更“活”）
   * - 大于 1：更偏向大声时才明显张开
   */
  curve: number
}

export interface SpeakOptions {
  url: string
  volume?: number
  crossOrigin?: '' | 'anonymous' | 'use-credentials'
  /**
   * 是否真正播放（发声）
   * - true：浏览器播放该音频，同时用同一条音频驱动口型
   * - false：只驱动口型（大多数场景没必要，保留扩展点）
   */
  play?: boolean
}

const clamp01 = (n: number) => Math.max(0, Math.min(1, n))

export const createDefaultLipSyncConfig = (): LipSyncConfig => ({
  noiseFloor: 0.006,
  gain: 14,
  smoothIn: 0.78,
  smoothOut: 0.8,
  curve: 0.75
})

/**
 * 口型驱动器（与“音频播放”解耦）
 *
 * 核心思想：
 * - 任何“正在播放的音频”（音乐/人声/TTS）都可以作为驱动源
 * - 口型永远跟随同一条音频时间轴，避免另开一条音频造成不同步
 */
export const useLipSync = (setMouthOpen: MouthController, initialConfig?: Partial<LipSyncConfig>) => {
  const config: LipSyncConfig = { ...createDefaultLipSyncConfig(), ...(initialConfig || {}) }

  let audio: HTMLAudioElement | null = null
  let context: AudioContext | null = null
  let analyser: AnalyserNode | null = null
  let source: MediaElementAudioSourceNode | null = null
  let data: Uint8Array<ArrayBuffer> | null = null
  let rafId: number | null = null
  let mouthSmoothed = 0
  let detachListeners: (() => void) | null = null

  const ensureContext = async () => {
    if (!context) {
      const Ctx = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext | undefined
      if (!Ctx) return false

      context = new Ctx()
      analyser = context.createAnalyser()
      analyser.fftSize = 2048
      analyser.smoothingTimeConstant = 0.85
      data = new Uint8Array(new ArrayBuffer(analyser.fftSize))

      analyser.connect(context.destination)
    }

    if (context.state === 'suspended') {
      try {
        await context.resume()
      } catch {
      }
    }

    return true
  }

  const detachAudioListeners = () => {
    if (!detachListeners) return
    detachListeners()
    detachListeners = null
  }

  const stop = () => {
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    detachAudioListeners()
    mouthSmoothed = 0
    setMouthOpen(0)
  }

  const attach = async (mediaElement: HTMLAudioElement) => {
    stop()

    const ok = await ensureContext()
    if (!ok || !context || !analyser || !data) return false

    if (audio !== mediaElement) {
      try {
        source?.disconnect()
      } catch {
      }

      audio = mediaElement
      source = context.createMediaElementSource(mediaElement)
      source.connect(analyser)
    }

    detachAudioListeners()
    const onPauseOrEnd = () => stop()
    mediaElement.addEventListener('pause', onPauseOrEnd)
    mediaElement.addEventListener('ended', onPauseOrEnd)
    detachListeners = () => {
      mediaElement.removeEventListener('pause', onPauseOrEnd)
      mediaElement.removeEventListener('ended', onPauseOrEnd)
    }

    return true
  }

  const computeRms = (buffer: Uint8Array) => {
    let sum = 0
    for (let i = 0; i < buffer.length; i++) {
      const n = (buffer[i] - 128) / 128
      sum += n * n
    }
    return Math.sqrt(sum / buffer.length)
  }

  const tick = () => {
    if (!audio || !analyser || !data) return
    if (audio.paused || audio.ended) {
      stop()
      return
    }

    analyser.getByteTimeDomainData(data)

    const rms = computeRms(data)
    const raw = (rms - config.noiseFloor) * config.gain
    const target = Math.pow(clamp01(raw), config.curve)

    const alpha = target > mouthSmoothed ? config.smoothIn : config.smoothOut
    mouthSmoothed = mouthSmoothed * alpha + target * (1 - alpha)

    setMouthOpen(mouthSmoothed)
    rafId = requestAnimationFrame(tick)
  }

  const start = async (mediaElement: HTMLAudioElement) => {
    const ok = await attach(mediaElement)
    if (!ok) return false
    rafId = requestAnimationFrame(tick)
    return true
  }

  /**
   * 统一“说话”入口：
   * - 给你一个 URL，我们内部创建一个 Audio 并播放
   * - 同时用同一个 Audio 做口型驱动
   *
   * 未来你对接“文本→推理→TTS 音频 URL”时，只需要把 URL 丢进来即可
   */
  const speak = async (options: SpeakOptions) => {
    const el = new Audio(options.url)
    el.preload = 'auto'
    el.crossOrigin = options.crossOrigin ?? 'anonymous'
    el.volume = options.volume ?? 1

    const ok = await start(el)
    if (!ok) return null

    if (options.play !== false) {
      try {
        await el.play()
      } catch {
      }
    }

    return el
  }

  const updateConfig = (partial: Partial<LipSyncConfig>) => {
    Object.assign(config, partial)
  }

  const destroy = () => {
    stop()
    try {
      source?.disconnect()
    } catch {
    }
    try {
      analyser?.disconnect()
    } catch {
    }
    if (context) {
      context.close().catch(() => {
      })
      context = null
    }
    source = null
    analyser = null
    data = null
    audio = null
  }

  return {
    config,
    start,
    stop,
    speak,
    updateConfig,
    destroy
  }
}

