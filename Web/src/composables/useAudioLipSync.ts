/** 音频图属于页面播放器生命周期；模型只持有分析支路，销毁模型不切断声音。 */
let context: AudioContext | null = null
let sources = new WeakMap<HTMLMediaElement, MediaElementAudioSourceNode>()

export async function resumeAudioContext() {
  if (!context || context.state === 'closed') {
    const Context = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Context) return null
    context = new Context()
    sources = new WeakMap()
  }
  if (context.state === 'suspended') {
    let timer: ReturnType<typeof setTimeout> | undefined
    try {
      await Promise.race([context.resume(), new Promise<never>((_, reject) => {
        timer = setTimeout(() => reject(new Error('音频等待用户交互')), 2000)
      })])
    } finally { clearTimeout(timer) }
  }
  return context
}

export function disposeAudioContext() {
  const previous = context
  context = null
  sources = new WeakMap()
  void previous?.close().catch(() => {})
}

interface LipSyncConfig { noiseFloor: number; gain: number; smoothIn: number; smoothOut: number; curve: number }

export function useAudioLipSync(setMouth: (value: number) => void, initial: Partial<LipSyncConfig> = {}) {
  const config = { noiseFloor: 0.006, gain: 10, smoothIn: 0.6, smoothOut: 0.8, curve: 0.75, ...initial }
  let analyser: AnalyserNode | null = null
  let source: MediaElementAudioSourceNode | null = null
  let audio: HTMLAudioElement | null = null
  let frame = 0
  let generation = 0
  let smoothed = 0
  let detach = () => {}
  const pauseSampling = () => {
    cancelAnimationFrame(frame)
    frame = 0
    smoothed = 0
    setMouth(0)
  }
  const stop = () => {
    generation++
    pauseSampling()
    detach()
    detach = () => {}
    if (source && analyser) { try { source.disconnect(analyser) } catch { /* 已断开的支路无需再次处理 */ } }
    analyser?.disconnect()
    source = null
    analyser = null
    audio = null
  }
  const start = async (element: HTMLAudioElement): Promise<boolean> => {
    stop()
    const token = generation
    try {
      const ctx = await resumeAudioContext()
      if (!ctx || token !== generation) return false
      const node = sources.get(element) || ctx.createMediaElementSource(element)
      if (!sources.has(element)) {
        // 声音只连一次 destination，分析器是旁路，stop 不会将正在播放的音乐静音。
        node.connect(ctx.destination)
        sources.set(element, node)
      }
      source = node
      audio = element
      analyser = ctx.createAnalyser()
      analyser.fftSize = 2048
      const data = new Uint8Array(analyser.fftSize)
      node.connect(analyser)
      const tick = () => {
        frame = 0
        if (token !== generation || !analyser || element.paused || element.ended) { pauseSampling(); return }
        analyser.getByteTimeDomainData(data)
        let sum = 0
        for (const value of data) sum += ((value - 128) / 128) ** 2
        const target = Math.pow(Math.max(0, Math.min(1, (Math.sqrt(sum / data.length) - config.noiseFloor) * config.gain)), config.curve)
        const alpha = target > smoothed ? config.smoothIn : config.smoothOut
        smoothed = smoothed * alpha + target * (1 - alpha)
        setMouth(smoothed)
        frame = requestAnimationFrame(tick)
      }
      const play = () => { if (!frame && token === generation) frame = requestAnimationFrame(tick) }
      element.addEventListener('playing', play)
      element.addEventListener('pause', pauseSampling)
      element.addEventListener('ended', pauseSampling)
      element.addEventListener('error', pauseSampling)
      detach = () => {
        element.removeEventListener('playing', play)
        element.removeEventListener('pause', pauseSampling)
        element.removeEventListener('ended', pauseSampling)
        element.removeEventListener('error', pauseSampling)
      }
      if (!element.paused && !element.ended) play()
      return true
    } catch (error) {
      if (token === generation) stop()
      console.warn('[lipSync] 无法连接口型分析，音频保持独立播放', error)
      return false
    }
  }
  const speak = async (options: { url: string; volume?: number; crossOrigin?: '' | 'anonymous' | 'use-credentials'; play?: boolean }) => {
    const element = new Audio()
    element.crossOrigin = options.crossOrigin ?? 'anonymous'
    element.preload = 'auto'
    element.volume = options.volume ?? 1
    element.src = options.url
    await start(element)
    return element
  }
  return { config, start, stop, speak, destroy: stop, updateConfig: (value: Partial<LipSyncConfig>) => Object.assign(config, value), currentAudio: () => audio }
}
