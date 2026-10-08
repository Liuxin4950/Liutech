import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import MusicCapsule from '@/components/MusicCapsule.vue'
import { getMusicList, type MusicItem } from '@/services/musicApi'

vi.mock('@/services/musicApi', () => ({ getMusicList: vi.fn() }))
vi.mock('@/composables/useAudioLipSync', () => ({
  resumeAudioContext: vi.fn(async () => null),
  prepareAnalysisOnlyAudio: (audio: HTMLAudioElement) => { audio.muted = true }
}))

class ControlledAudio extends EventTarget {
  static all: ControlledAudio[] = []
  src = ''
  crossOrigin = ''
  preload = ''
  muted = false
  paused = true
  ended = false
  seeking = false
  duration = 60
  position = 0
  requests: Array<() => void> = []
  get currentTime() { return this.position }
  set currentTime(value: number) { this.position = value; this.ended = false }
  play = vi.fn(() => new Promise<void>(resolve => {
    this.requests.push(() => {
      this.paused = false
      this.dispatchEvent(new Event('playing'))
      resolve()
    })
  }))
  pause = vi.fn(() => { this.paused = true; this.dispatchEvent(new Event('pause')) })
  load = vi.fn()
  removeAttribute(name: string) { if (name === 'src') this.src = '' }
  constructor() { super(); ControlledAudio.all.push(this) }
  finishPlay(time = this.currentTime) { this.currentTime = time; this.requests.shift()?.() }
  finish() { this.ended = true; this.paused = true; this.dispatchEvent(new Event('ended')) }
}

interface Controls {
  playMusic: () => Promise<void>
  pauseMusic: () => void
  selectTrack: (index: number) => void
  setPlayMode: (mode: 'list' | 'single' | 'sequence') => void
  isPlaying: () => boolean
  getCurrentAudio: () => HTMLAudioElement | null
  seekTo: (position: number) => void
}
const item = (id: number): MusicItem => ({
  id, title: `歌曲${id}`, artist: null, coverUrl: null, fullAudioUrl: `/full-${id}.mp3`, vocalUrl: `/vocal-${id}.mp3`,
  duration: 60, sortOrder: id, status: 1, createdAt: '', updatedAt: ''
})
let wrapper: VueWrapper
const controls = () => wrapper.vm as unknown as Controls
const start = async (items = [item(1), item(2)]) => {
  vi.mocked(getMusicList).mockResolvedValue(items)
  wrapper = mount(MusicCapsule)
  await flushPromises()
  void controls().playMusic()
  return ControlledAudio.all.slice(-2)
}
const settle = async (tracks: ControlledAudio[]) => {
  tracks.forEach(audio => audio.finishPlay())
  await flushPromises()
  tracks.forEach(audio => audio.finishPlay())
  await flushPromises()
}

beforeEach(() => { ControlledAudio.all = []; vi.stubGlobal('Audio', ControlledAudio) })
afterEach(() => { wrapper?.unmount(); vi.unstubAllGlobals() })

describe('音乐双轨时序与续播', () => {
  it('等待较慢的人声后对齐双轨，仅完整音频出声', async () => {
    const [full, vocal] = await start()
    full.finishPlay(2)
    await flushPromises()
    expect(full.paused).toBe(true)
    expect(full.muted).toBe(true)
    expect(controls().isPlaying()).toBe(false)
    expect(vocal.muted).toBe(true)
    vocal.finishPlay(0)
    await flushPromises()
    expect(full.currentTime).toBe(0)
    expect(vocal.currentTime).toBe(0)
    full.finishPlay(0.2)
    vocal.finishPlay(0)
    await flushPromises()
    expect(vocal.currentTime).toBe(0.2)
    expect(full.muted).toBe(false)
    expect(controls().getCurrentAudio()).toBe(vocal)
    expect(controls().isPlaying()).toBe(true)
    vocal.currentTime = 3
    full.currentTime = 5
    full.dispatchEvent(new Event('timeupdate'))
    expect(vocal.currentTime).toBe(5)
    expect(full.currentTime).toBe(5)
  })

  it('主轨结束立即下一首，不受尚未结束的人声阻塞', async () => {
    const tracks = await start()
    await settle(tracks)
    tracks[0].finish()
    await flushPromises()
    expect(ControlledAudio.all).toHaveLength(4)
    expect(ControlledAudio.all[2].src).toBe('/full-2.mp3')
    expect(tracks[1].paused).toBe(true)
    await settle(ControlledAudio.all.slice(2))
    expect(controls().isPlaying()).toBe(true)
  })

  it('单首歌默认循环，单曲模式保留当前歌曲，顺序模式在末尾停止', async () => {
    const tracks = await start([item(1)])
    await settle(tracks)
    tracks[0].finish()
    await flushPromises()
    await settle(tracks)
    expect(tracks[0].play).toHaveBeenCalledTimes(4)
    expect(controls().isPlaying()).toBe(true)
    controls().setPlayMode('single')
    tracks[0].finish()
    await flushPromises()
    await settle(tracks)
    expect(ControlledAudio.all).toHaveLength(2)
    expect(controls().isPlaying()).toBe(true)
    controls().setPlayMode('sequence')
    tracks[0].finish()
    await flushPromises()
    expect(tracks[0].play).toHaveBeenCalledTimes(6)
    expect(controls().isPlaying()).toBe(false)
  })

  it('加载中暂停会取消待播放，两轨迟到结果不能重新出声', async () => {
    const tracks = await start()
    controls().pauseMusic()
    tracks.forEach(audio => audio.finishPlay())
    await flushPromises()
    expect(tracks.every(audio => audio.paused)).toBe(true)
    expect(tracks[0].play).toHaveBeenCalledTimes(1)
    expect(controls().isPlaying()).toBe(false)
  })

  it('旧歌曲加载结果不能改变新歌或恢复旧音轨', async () => {
    const oldTracks = await start()
    controls().selectTrack(1)
    const newTracks = ControlledAudio.all.slice(2)
    await settle(newTracks)
    oldTracks.forEach(audio => audio.finishPlay())
    await flushPromises()
    expect(oldTracks.every(audio => audio.paused)).toBe(true)
    expect(newTracks.every(audio => !audio.paused)).toBe(true)
    expect(controls().getCurrentAudio()).toBe(newTracks[1])
  })

  it('任一轨缓冲时共同暂停，并从主轨时间恢复；进度跳转同步两轨', async () => {
    const tracks = await start()
    await settle(tracks)
    tracks[0].currentTime = 12
    tracks[1].currentTime = 11
    tracks[1].dispatchEvent(new Event('waiting'))
    expect(tracks.every(audio => audio.paused)).toBe(true)
    await settle(tracks)
    expect(tracks[0].currentTime).toBe(12)
    expect(tracks[1].currentTime).toBe(12)
    tracks[0].dispatchEvent(new Event('durationchange'))
    controls().seekTo(30)
    expect(tracks[0].currentTime).toBe(30)
    expect(tracks[1].currentTime).toBe(30)
  })

  it('纠正人声时间轴产生的 seeking/waiting 不会无限重启主轨', async () => {
    const tracks = await start()
    await settle(tracks)
    tracks[1].seeking = true
    tracks[1].dispatchEvent(new Event('waiting'))
    expect(tracks.every(audio => !audio.paused)).toBe(true)
    expect(tracks[0].play).toHaveBeenCalledTimes(2)
    tracks[1].seeking = false
    tracks[1].dispatchEvent(new Event('waiting'))
    expect(tracks.every(audio => audio.paused)).toBe(true)
    await settle(tracks)
  })

  it('人声失败继续完整音频并停止口型，主轨结束仍可续播', async () => {
    const tracks = await start()
    tracks[1].dispatchEvent(new Event('error'))
    tracks[0].finishPlay()
    await flushPromises()
    tracks[0].finishPlay()
    await flushPromises()
    expect(controls().isPlaying()).toBe(true)
    expect(controls().getCurrentAudio()).toBeNull()
    tracks[0].finish()
    await flushPromises()
    expect(ControlledAudio.all[2].src).toBe('/full-2.mp3')
  })
})
