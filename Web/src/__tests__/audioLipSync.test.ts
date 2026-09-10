import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'

/**
 * 口型分析（Web Audio）单元测试
 *
 * 覆盖三类场景：
 * 1. 基本播放时序（暂停不采样、playing 才采样、复用同一 source）；
 * 2. 浏览器自动播放策略导致上下文挂起时：**不阻塞声音**、不误挂分析，且上下文恢复后自动补挂；
 * 3. 失败状态对 UI 可见（lipSyncDegraded），重试成功后清除。
 */
describe('音频与模型生命周期', () => {
  type Module = typeof import('@/composables/useAudioLipSync')
  let lipSyncModule: Module
  let frames: Map<number, FrameRequestCallback>
  let source: { connect: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn> }
  let createSource: ReturnType<typeof vi.fn>
  let contextState: string
  let contextListeners: Map<string, Set<() => void>>
  let resume: ReturnType<typeof vi.fn>
  /** 置 true 后 analyser 读取抛错，用于模拟分析器失效 */
  let analyserReadFails = false

  /** 切换上下文状态并派发 statechange（模拟用户手势后浏览器恢复音频） */
  const setContextState = (next: string) => {
    contextState = next
    contextListeners.get('statechange')?.forEach(listener => listener())
  }

  beforeEach(async () => {
    // 注意：用例里 vi.useFakeTimers() 必须限定 toFake —— 默认连 requestAnimationFrame
    // 一起假造，会覆盖下面的 rAF 桩，导致帧计数恒为 0。
    frames = new Map()
    let id = 0
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.set(++id, callback); return id })
    vi.stubGlobal('cancelAnimationFrame', (value: number) => frames.delete(value))
    source = { connect: vi.fn(), disconnect: vi.fn() }
    createSource = vi.fn(() => source)
    contextState = 'running'
    contextListeners = new Map()
    analyserReadFails = false
    resume = vi.fn(async () => {})
    vi.stubGlobal('AudioContext', class {
      get state() { return contextState }
      destination = {}
      createMediaElementSource = createSource
      createAnalyser = () => ({
        fftSize: 2048,
        disconnect: vi.fn(),
        getByteTimeDomainData: (data: Uint8Array) => {
          if (analyserReadFails) throw new Error('analyser detached')
          // 160 ≈ 持续有较大振幅（对应"嘴张开"）
          data.fill(160)
        }
      })
      resume = resume
      close = vi.fn(async () => {})
      addEventListener = (type: string, listener: () => void) => {
        if (!contextListeners.has(type)) contextListeners.set(type, new Set())
        contextListeners.get(type)!.add(listener)
      }
      removeEventListener = (type: string, listener: () => void) => { contextListeners.get(type)?.delete(listener) }
    })
    // 上下文是"全页共用一个、从不关闭"的单例，测试之间必须重置模块状态
    vi.resetModules()
    lipSyncModule = await import('@/composables/useAudioLipSync')
  })

  afterEach(() => { vi.unstubAllGlobals() })

  /**
   * 手动触发"最新一帧"
   *
   * 真实 requestAnimationFrame 在回调执行时会自动出队，桩里必须手动模拟，
   * 否则留下的陈旧帧会让后续断言失真（踩过一次）。
   */
  const runFrame = () => {
    const ids = [...frames.keys()]
    if (!ids.length) return
    const id = Math.max(...ids)
    const callback = frames.get(id)
    frames.delete(id)
    callback?.(0)
  }

  /** 造一个可控 paused 状态的音频元素 */
  const createAudio = (initiallyPaused = true) => {
    const audio = new Audio()
    let paused = initiallyPaused
    Object.defineProperty(audio, 'paused', { get: () => paused })
    return {
      audio,
      setPaused: (value: boolean) => { paused = value }
    }
  }

  it('先绑定暂停音频，playing 后才采样，暂停后能再次播放', async () => {
    const { audio, setPaused } = createAudio(true)
    const mouth = vi.fn()
    const lip = lipSyncModule.useAudioLipSync(mouth)

    expect(await lip.start(audio)).toBe(true)
    expect(frames.size).toBe(0)

    setPaused(false)
    audio.dispatchEvent(new Event('playing'))
    expect(frames.size).toBe(1)

    setPaused(true)
    audio.dispatchEvent(new Event('pause'))
    expect(frames.size).toBe(0)
    expect(mouth).toHaveBeenLastCalledWith(0)

    setPaused(false)
    audio.dispatchEvent(new Event('playing'))
    expect(frames.size).toBe(1)
    lip.destroy()
  })

  it('模型重建复用同一 source；销毁模型只断开分析支路', async () => {
    const { audio } = createAudio(false)
    const first = lipSyncModule.useAudioLipSync(vi.fn())
    await first.start(audio)
    first.destroy()
    expect(source.disconnect).not.toHaveBeenCalledWith()

    const next = lipSyncModule.useAudioLipSync(vi.fn())
    await next.start(audio)
    expect(createSource).toHaveBeenCalledTimes(1)
    next.destroy()
  })

  it('上下文挂起时不挂载分析（保住声音），并给出可见提示', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    contextState = 'suspended'
    resume.mockImplementation(() => new Promise<void>(() => { /* 永远挂起，模拟自动播放策略 */ }))
    const { audio } = createAudio(false)
    const lip = lipSyncModule.useAudioLipSync(vi.fn())

    // 关键：即使上下文没恢复也要立刻返回 false，不能把播放卡住
    const pending = lip.start(audio)
    await vi.advanceTimersByTimeAsync(2100)   // 触发 resume 的 2 秒等待上限
    expect(await pending).toBe(false)
    // 不能碰音频元素：一旦 createMediaElementSource 生效，挂起的上下文会把声音一起吞掉
    expect(createSource).not.toHaveBeenCalled()
    expect(lipSyncModule.lipSyncDegraded.value).toBe(true)
    expect(lipSyncModule.lipSyncMessage.value).toContain('点击页面')

    lip.destroy()
    vi.useRealTimers()
  })

  it('上下文随后恢复时自动补挂分析，口型开始驱动', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    contextState = 'suspended'
    resume.mockImplementation(() => new Promise<void>(() => {}))
    const { audio } = createAudio(false)
    const mouth = vi.fn()
    const lip = lipSyncModule.useAudioLipSync(mouth)

    const pending = lip.start(audio)
    await vi.advanceTimersByTimeAsync(2100)
    expect(await pending).toBe(false)
    expect(frames.size).toBe(0)

    // 用户手势后浏览器恢复音频上下文：应自动补挂，无需重新发起请求
    setContextState('running')

    expect(createSource).toHaveBeenCalledTimes(1)
    expect(frames.size).toBe(1)
    expect(lipSyncModule.lipSyncDegraded.value).toBe(false)
    lip.destroy()
    vi.useRealTimers()
  })

  it('点击重试：上下文恢复后立即补挂，并清除失败标记', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    contextState = 'suspended'
    // 首次：resume 一直挂起（自动播放策略）；用户点击后这一次才真正恢复
    resume.mockImplementation(() => new Promise<void>(() => {}))
    const { audio } = createAudio(false)
    const lip = lipSyncModule.useAudioLipSync(vi.fn())

    const pending = lip.start(audio)
    await vi.advanceTimersByTimeAsync(2100)
    expect(await pending).toBe(false)
    expect(lipSyncModule.lipSyncDegraded.value).toBe(true)

    // 用户在"口型未启用"提示上点一下（真实手势）
    resume.mockImplementation(async () => { contextState = 'running' })
    expect(await lip.retry()).toBe(true)
    expect(createSource).toHaveBeenCalledTimes(1)
    expect(lipSyncModule.lipSyncDegraded.value).toBe(false)
    lip.destroy()
    vi.useRealTimers()
  })

  it('重复挂载不会并存多个采样循环（否则口型会被写死）', async () => {
    const { audio } = createAudio(false)
    const lip = lipSyncModule.useAudioLipSync(vi.fn())

    expect(await lip.start(audio)).toBe(true)
    expect(frames.size).toBe(1)

    // 模拟"上下文 statechange 补挂 + 用户点击重试"这类叠加调用
    expect(await lip.start(audio)).toBe(true)
    // 旧循环必须被取消，任何时刻只能有一个循环在写口型
    expect(frames.size).toBe(1)

    lip.destroy()
    expect(frames.size).toBe(0)
  })

  it('分析中断时立即闭嘴，不会把嘴冻在张开状态', async () => {
    const { audio } = createAudio(false)
    const mouth = vi.fn()
    const lip = lipSyncModule.useAudioLipSync(mouth)

    await lip.start(audio)
    // 先跑一帧：响亮信号 → 嘴张开
    runFrame()
    expect(mouth.mock.calls[mouth.mock.calls.length - 1]?.[0]).toBeGreaterThan(0)

    // 分析器失效（例如节点被断开）：必须立刻复位并停止循环
    analyserReadFails = true
    runFrame()
    expect(mouth).toHaveBeenLastCalledWith(0)
    expect(frames.size).toBe(0)

    lip.destroy()
  })

  it('createMediaElementSource 被拒时报出可行动的原因，而不是静默', async () => {
    const { audio } = createAudio(false)
    createSource.mockImplementation(() => { throw new Error('InvalidStateError') })
    const lip = lipSyncModule.useAudioLipSync(vi.fn())

    expect(await lip.start(audio)).toBe(false)
    expect(lipSyncModule.lipSyncDegraded.value).toBe(true)
    expect(lipSyncModule.lipSyncMessage.value).toContain('已绑定')
    lip.destroy()
  })
})
