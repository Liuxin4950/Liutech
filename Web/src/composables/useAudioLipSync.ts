/** 音频图属于页面播放器生命周期；模型只持有分析支路，销毁模型不切断声音。 */
import { ref } from 'vue'

/**
 * 口型分析的诊断状态（模块级，UI 可直接读取）
 *
 * 为什么需要它：口型分析失败时，代码原本只 `console.warn` 一句，
 * 用户看到的现象是"有声音但模型不张嘴"，既不知道为什么、也不知道怎么办。
 * 现在失败会置位 `lipSyncDegraded`，界面据此给出提示并支持点击重试。
 */
export const lipSyncDegraded = ref(false)
/** 面向用户的原因说明（简短、可行动） */
export const lipSyncMessage = ref('')

/** 记录一次分析失败：只在首次失败时打日志，避免每段音频刷屏 */
function markDegraded(message: string, error?: unknown) {
  if (!lipSyncDegraded.value) {
    console.warn('[lipSync] 口型分析暂不可用：' + message, error ?? '')
  }
  lipSyncDegraded.value = true
  lipSyncMessage.value = message
}

/** 分析成功接入后清除失败标记 */
function clearDegraded() {
  if (lipSyncDegraded.value) {
    console.info('[lipSync] 口型分析已恢复')
  }
  lipSyncDegraded.value = false
  lipSyncMessage.value = ''
}

/** 页面级唯一 AudioContext */
let context: AudioContext | null = null
/** 音频元素 → 已绑定的源节点（WeakMap：元素被回收时一并释放） */
let sources = new WeakMap<HTMLMediaElement, MediaElementAudioSourceNode>()

/** 单次 resume() 的等待上限；超时不再算失败（见 resumeAudioContext 注释） */
const RESUME_TIMEOUT_MS = 2000

/**
 * 获取（必要时创建）页面级 AudioContext。
 *
 * 与旧实现的两点关键区别：
 * 1. `resume()` 超时不再抛异常。自动播放策略下它会一直挂着，等用户手势后才真正恢复；
 *    旧的 2 秒超时直接把这次调用判死，于是口型永远接不上（而声音照常播）。
 * 2. 上下文只创建一次、**从不主动关闭**。HTMLMediaElement 一旦绑定过某个 AudioContext
 *    就再也无法改绑（createMediaElementSource 会抛 InvalidStateError），而音乐播放器的
 *    <audio> 是跨会话长生命周期的 —— 旧实现在 TTS 播放器卸载时 close 掉上下文，
 *    会让这个元素此后永久失去口型能力。
 */
export async function resumeAudioContext(): Promise<AudioContext | null> {
  if (!context || context.state === 'closed') {
    const Context = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Context) {
      markDegraded('当前浏览器不支持 Web Audio')
      return null
    }
    context = new Context()
    sources = new WeakMap()
  }
  if (context.state === 'suspended') {
    let timer: ReturnType<typeof setTimeout> | undefined
    try {
      await Promise.race([
        context.resume(),
        new Promise<void>((_, reject) => {
          timer = setTimeout(() => reject(new Error('resume-timeout')), RESUME_TIMEOUT_MS)
        })
      ])
    } catch {
      // 刻意不抛出：把上下文交回调用方，由它决定"先播声音、等恢复后补挂分析"
    } finally {
      clearTimeout(timer)
    }
  }
  return context
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
  /** 因上下文未就绪而暂缓挂载分析的音频元素（上下文恢复后自动补挂） */
  let deferredElement: HTMLAudioElement | null = null
  let deferredToken = -1
  /**
   * 采样循环的身份
   *
   * 每次挂载/清理都自增，循环体先核对身份再干活：这样即使出现"补挂 + 重试"等叠加调用，
   * 也绝不会有两个循环同时写口型参数（曾把一个失效分析器的最后一次响亮值一直写下去，
   * 表现为模型一直张着嘴）。
   */
  let loopId = 0

  const pauseSampling = () => {
    cancelAnimationFrame(frame)
    frame = 0
    smoothed = 0
    // 口型只能由活跃循环驱动：循环一停就必须闭嘴
    setMouth(0)
  }

  /** 拆掉当前分析支路（取消循环、断开分析器），但不改变 generation */
  const teardownAnalysis = () => {
    loopId++
    pauseSampling()
    detach()
    detach = () => {}
    if (source && analyser) { try { source.disconnect(analyser) } catch { /* 已断开的支路无需再次处理 */ } }
    try { analyser?.disconnect() } catch { /* 忽略重复断开 */ }
    source = null
    analyser = null
  }

  const stop = () => {
    generation++
    deferredElement = null
    deferredToken = -1
    teardownAnalysis()
    audio = null
  }

  /**
   * 把音频元素接进分析图（要求上下文已 running）
   *
   * 说明：一旦 createMediaElementSource 生效，声音就完全走 AudioContext 输出。
   * 上下文处于 suspended 时挂载会**连声音一起弄没**，因此这个函数只在 running 时被调用。
   */
  const attach = (element: HTMLAudioElement, token: number): boolean => {
    if (!context || context.state !== 'running') return false
    // 关键：先彻底拆除上一路分析（含取消它的 rAF 循环），保证同一时刻只有一个循环在写口型
    teardownAnalysis()
    const myLoop = loopId
    const node = sources.get(element) || context.createMediaElementSource(element)
    if (!sources.has(element)) {
      // 声音只连一次 destination，分析器是旁路，stop 不会将正在播放的音乐静音。
      node.connect(context.destination)
      sources.set(element, node)
    }
    source = node
    audio = element
    analyser = context.createAnalyser()
    analyser.fftSize = 2048
    const data = new Uint8Array(analyser.fftSize)
    node.connect(analyser)
    const tick = () => {
      frame = 0
      // 已被更新的分析取代：安静退出，不再写任何口型值
      if (myLoop !== loopId) return
      if (token !== generation || !analyser || element.paused || element.ended) { pauseSampling(); return }
      try {
        analyser.getByteTimeDomainData(data)
      } catch (error) {
        // 分析器失效时如果不复位，最后一个值会把嘴"冻"在张开状态
        console.warn('[lipSync] 分析中断，已复位口型', error)
        pauseSampling()
        return
      }
      let sum = 0
      for (const value of data) sum += ((value - 128) / 128) ** 2
      const target = Math.pow(Math.max(0, Math.min(1, (Math.sqrt(sum / data.length) - config.noiseFloor) * config.gain)), config.curve)
      const alpha = target > smoothed ? config.smoothIn : config.smoothOut
      smoothed = smoothed * alpha + target * (1 - alpha)
      setMouth(smoothed)
      frame = requestAnimationFrame(tick)
    }
    const play = () => { if (!frame && token === generation && myLoop === loopId) frame = requestAnimationFrame(tick) }
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
    clearDegraded()
    return true
  }

  /** 上下文恢复 running 时，把之前暂缓的元素补挂上去 */
  const onContextStateChange = () => {
    const element = deferredElement
    if (!element || !context || context.state !== 'running') return
    const token = deferredToken
    deferredElement = null
    deferredToken = -1
    if (token !== generation) return
    try {
      attach(element, token)
    } catch (error) {
      markDegraded('该音频无法接入口型分析（音频元素已绑定或跨域限制）', error)
    }
  }

  /**
   * 开始用某个音频元素驱动口型
   *
   * 上下文已运行时立即挂载；未运行时不阻塞播放（声音先原生播出来），
   * 只是记下元素，等上下文恢复后由 statechange 自动补挂。
   */
  const start = async (element: HTMLAudioElement): Promise<boolean> => {
    stop()
    const token = generation
    try {
      const ctx = await resumeAudioContext()
      if (!ctx || token !== generation) return false

      if (ctx.state !== 'running') {
        deferredElement = element
        deferredToken = token
        ctx.removeEventListener('statechange', onContextStateChange)
        ctx.addEventListener('statechange', onContextStateChange)
        markDegraded('浏览器暂未允许音频分析，点击页面任意处即可启用口型')
        return false
      }

      return attach(element, token)
    } catch (error) {
      if (token === generation) stop()
      // 到这里通常是 createMediaElementSource 被拒：元素已绑定过别的上下文，
      // 或媒体跨域未获 CORS 许可（此时声音仍能播，只是无法分析）。
      markDegraded('该音频无法接入口型分析（音频元素已绑定或跨域限制）', error)
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

  /**
   * 手动重试当前音频的口型分析（由界面的"口型未启用"提示触发）
   *
   * 用户点击本身就是浏览器需要的交互手势，所以这里先恢复上下文再补挂分析，
   * 能直接解决"自动播放策略导致上下文挂起"这一最常见的失败原因。
   */
  const retry = async (): Promise<boolean> => {
    const element = deferredElement ?? audio
    await resumeAudioContext()
    if (!context || context.state !== 'running') {
      markDegraded('浏览器仍未允许音频分析，请再点击一次页面')
      return false
    }
    if (!element) {
      // 没有待挂载的音频：上下文已恢复，下一段音频会自动带上口型
      clearDegraded()
      return true
    }
    const token = deferredToken >= 0 ? deferredToken : generation
    deferredElement = null
    deferredToken = -1
    try {
      return attach(element, token)
    } catch (error) {
      markDegraded('该音频无法接入口型分析（音频元素已绑定或跨域限制）', error)
      return false
    }
  }

  return {
    config,
    start,
    stop,
    speak,
    retry,
    destroy: stop,
    updateConfig: (value: Partial<LipSyncConfig>) => Object.assign(config, value),
    currentAudio: () => audio
  }
}
