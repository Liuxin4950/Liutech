import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { disposeAudioContext, useAudioLipSync } from '@/composables/useAudioLipSync'

describe('音频与模型生命周期', () => {
  let frames: Map<number, FrameRequestCallback>
  let source: { connect: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn> }
  let createSource: ReturnType<typeof vi.fn>
  beforeEach(() => {
    frames = new Map()
    let id = 0
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.set(++id, callback); return id })
    vi.stubGlobal('cancelAnimationFrame', (value: number) => frames.delete(value))
    source = { connect: vi.fn(), disconnect: vi.fn() }
    createSource = vi.fn(() => source)
    vi.stubGlobal('AudioContext', class {
      state = 'running'
      destination = {}
      createMediaElementSource = createSource
      createAnalyser = () => ({ fftSize: 2048, disconnect: vi.fn(), getByteTimeDomainData: (data: Uint8Array) => data.fill(160) })
      close = vi.fn(async () => {})
    })
  })
  afterEach(() => { disposeAudioContext(); vi.unstubAllGlobals() })
  it('先绑定暂停音频，playing 后才采样，暂停后能再次播放', async () => {
    const audio = new Audio()
    let paused = true
    Object.defineProperty(audio, 'paused', { get: () => paused })
    const mouth = vi.fn()
    const lip = useAudioLipSync(mouth)
    expect(await lip.start(audio)).toBe(true)
    expect(frames.size).toBe(0)
    paused = false
    audio.dispatchEvent(new Event('playing'))
    expect(frames.size).toBe(1)
    paused = true
    audio.dispatchEvent(new Event('pause'))
    expect(frames.size).toBe(0)
    expect(mouth).toHaveBeenLastCalledWith(0)
    paused = false
    audio.dispatchEvent(new Event('playing'))
    expect(frames.size).toBe(1)
    lip.destroy()
  })
  it('模型重建复用同一 source；销毁模型只断开分析支路', async () => {
    const audio = new Audio()
    const first = useAudioLipSync(vi.fn())
    await first.start(audio)
    first.destroy()
    expect(source.disconnect).not.toHaveBeenCalledWith()
    const next = useAudioLipSync(vi.fn())
    await next.start(audio)
    expect(createSource).toHaveBeenCalledTimes(1)
    next.destroy()
  })
})
