import { ref, reactive, defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import { useTtsPlayer } from '@/composables/useTtsPlayer'
import { useChatTts } from '@/composables/chatTts'
import AiChatInput from '@/components/AiChatInput.vue'

it('服务不可用时保留用户朗读偏好并清理等待状态', () => {
  const state = { ttsEnabled: ref(true), ttsAvailable: ref(true), ttsAwaitingAudio: ref(true) }
  const tts = useChatTts(state)
  tts.setTtsAvailable(false)
  expect(state.ttsEnabled.value).toBe(true)
  expect(state.ttsAwaitingAudio.value).toBe(false)
  tts.setTtsAvailable(true)
  expect(state.ttsEnabled.value).toBe(true)
})

it('audio-skip 的表情保留至自身计时结束，播放队列收尾不立即覆盖成 neutral', async () => {
  const model = { applyAvatarCue: vi.fn(), stopMusicLipSync: vi.fn() }
  const store = reactive({ showModel: true, ttsEnabled: true, ttsAvailable: true, ttsAwaitingAudio: false,
    ttsPendingCount: 0, ttsCancelCounter: 0, avatarCuePendingCount: 0,
    shiftTtsAudioQueue: vi.fn().mockReturnValueOnce({ status: 'skipped', cue: { expression: 'happy' } }),
    shiftAvatarCueQueue: vi.fn(), cancelTts: vi.fn(), clearTtsAudioQueue: vi.fn()
  })
  let player!: ReturnType<typeof useTtsPlayer>
  const wrapper = mount(defineComponent({ setup() {
    player = useTtsPlayer({ chatStore: store, live2dRef: ref(model), bottomNavRef: ref(null), live2dStatus: ref('ready') } as unknown as Parameters<typeof useTtsPlayer>[0])
    return () => h('div')
  } }))
  await player.playNextTts()
  expect(model.applyAvatarCue).toHaveBeenLastCalledWith({ expression: 'happy', skipResetTimer: false })
  wrapper.unmount()
})


it('队列已空而音频仍播放时保持实际播放状态，接话取消立即解除并暂停音频', async () => {
  const audio = document.createElement('audio')
  vi.spyOn(audio, 'play').mockResolvedValue()
  const pause = vi.spyOn(audio, 'pause').mockImplementation(() => {})
  const model = { applyAvatarCue: vi.fn(), stopMusicLipSync: vi.fn(), speakAudioElement: vi.fn().mockResolvedValue(audio) }
  const store = reactive({ showModel: true, ttsEnabled: true, ttsAvailable: true, ttsAwaitingAudio: false,
    ttsPlaybackActive: false, voiceInputActive: false,
    ttsPendingCount: 0, ttsCancelCounter: 0, avatarCuePendingCount: 0,
    shiftTtsAudioQueue: vi.fn().mockReturnValueOnce({ audioUrl: 'test.mp3', audioEl: audio }),
    shiftAvatarCueQueue: vi.fn(), cancelTts: vi.fn(), clearTtsAudioQueue: vi.fn(),
  })
  let player!: ReturnType<typeof useTtsPlayer>
  const wrapper = mount(defineComponent({ setup() {
    player = useTtsPlayer({ chatStore: store, live2dRef: ref(model), bottomNavRef: ref(null), live2dStatus: ref('ready') } as unknown as Parameters<typeof useTtsPlayer>[0])
    return () => h('div')
  } }))
  const playing = player.playNextTts()
  await flushPromises()
  expect(store.ttsPlaybackActive).toBe(true)
  expect(store.ttsPendingCount).toBe(0)
  store.ttsCancelCounter++
  expect(store.ttsPlaybackActive).toBe(false)
  expect(pause).toHaveBeenCalled()
  await playing
  wrapper.unmount()
})

it('免提在两段朗读间保持音乐暂停，退出后才恢复', async () => {
  const store = reactive({ showModel: true, ttsEnabled: true, ttsAvailable: true, ttsAwaitingAudio: false,
    ttsPlaybackActive: false, voiceInputActive: false,
    ttsPendingCount: 0, ttsCancelCounter: 0, avatarCuePendingCount: 0,
    shiftTtsAudioQueue: vi.fn(), shiftAvatarCueQueue: vi.fn(), cancelTts: vi.fn(), clearTtsAudioQueue: vi.fn(),
  })
  const nav = { isMusicPlaying: () => true, getMusicActionVersion: () => 1,
    pauseMusic: vi.fn(), resumeMusic: vi.fn(), getCurrentAudio: () => null }
  let player!: ReturnType<typeof useTtsPlayer>
  const wrapper = mount(defineComponent({ setup() {
    player = useTtsPlayer({ chatStore: store, live2dRef: ref({ stopMusicLipSync: vi.fn(), applyAvatarCue: vi.fn() }), bottomNavRef: ref(nav), live2dStatus: ref('ready') } as unknown as Parameters<typeof useTtsPlayer>[0])
    return () => h('div')
  } }))
  store.voiceInputActive = true
  expect(nav.pauseMusic).toHaveBeenCalledOnce()
  await player.playNextTts()
  expect(nav.resumeMusic).not.toHaveBeenCalled()
  store.voiceInputActive = false
  expect(nav.resumeMusic).toHaveBeenCalledOnce()
  wrapper.unmount()
})

it('乱序音频入队不会提前消费表情游标；缺失音频在终态补 skip 后继续播放', () => {
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
  const tts = useChatTts({ ttsEnabled: ref(true), ttsAvailable: ref(true), ttsAwaitingAudio: ref(true) })
  tts.enqueueAvatarCue({ seq: 1, expression: 'happy' })
  tts.enqueueAvatarCue({ seq: 2, expression: 'sad' })
  tts.enqueueTtsAudio({ seq: 2, audioUrl: 'second.wav' })
  tts.enqueueTtsAudio({ seq: 1, audioUrl: 'first.wav' })
  expect(tts.shiftTtsAudioQueue()?.cue?.expression).toBe('happy')
  expect(tts.shiftTtsAudioQueue()?.cue?.expression).toBe('sad')
  tts.enqueueAvatarCue({ seq: 3, expression: 'thinking' })
  tts.enqueueTtsAudio({ seq: 4, audioUrl: 'fourth.wav' })
  tts.finishTtsAudio(4)
  expect(tts.shiftTtsAudioQueue()).toMatchObject({ seq: 3, status: 'skipped' })
  expect(tts.shiftTtsAudioQueue()?.seq).toBe(4)
  expect(tts.ttsAudioQueue.pendingCount.value).toBe(0)
})

function playbackFixture(status: 'loading' | 'ready' = 'ready') {
  const audio = document.createElement('audio')
  let paused = true
  Object.defineProperty(audio, 'paused', { get: () => paused })
  vi.spyOn(audio, 'play').mockImplementation(async () => { paused = false; audio.dispatchEvent(new Event('playing')) })
  vi.spyOn(audio, 'pause').mockImplementation(() => { paused = true })
  const model = { applyAvatarCue: vi.fn(), stopMusicLipSync: vi.fn(), speakAudioElement: vi.fn().mockResolvedValue(audio) }
  const store = reactive({ showModel: true, isLoading: false, aiThinking: false, ttsEnabled: true, ttsAvailable: true, ttsAwaitingAudio: true,
    ttsPlaybackActive: false, ttsPlaybackState: 'idle', ttsPlaybackMessage: '', voiceInputActive: false,
    ttsPendingCount: 0, ttsCancelCounter: 0, avatarCuePendingCount: 0,
    recordSpeechPlayback: vi.fn(), speechTimings: { maxGapMs: 0 },
    shiftTtsAudioQueue: vi.fn().mockReturnValueOnce({ audioUrl: 'test.wav', audioEl: audio, cue: { expression: 'happy' } }),
    shiftAvatarCueQueue: vi.fn(), cancelTts: vi.fn(() => { store.ttsCancelCounter++; store.ttsAwaitingAudio = false }), clearTtsAudioQueue: vi.fn(),
  })
  const live2dStatus = ref(status)
  let player!: ReturnType<typeof useTtsPlayer>
  const wrapper = mount(defineComponent({ setup() {
    player = useTtsPlayer({ chatStore: store, live2dRef: ref(model), bottomNavRef: ref(null), live2dStatus } as unknown as Parameters<typeof useTtsPlayer>[0])
    return () => h('div')
  } }))
  return { audio, model, store, live2dStatus, player, wrapper }
}

it('模型下载不挡住首句声音，模型稍后 ready 仅补口型而不重播', async () => {
  const { audio, model, store, live2dStatus, player, wrapper } = playbackFixture('loading')
  const playback = player.playNextTts()
  await flushPromises()
  expect(audio.play).toHaveBeenCalledOnce()
  expect(store.ttsPlaybackState).toBe('playing')
  expect(model.speakAudioElement).not.toHaveBeenCalled()
  live2dStatus.value = 'ready'
  await flushPromises()
  expect(model.speakAudioElement).toHaveBeenCalledWith(audio)
  expect(audio.play).toHaveBeenCalledOnce()
  audio.dispatchEvent(new Event('ended'))
  await playback
  wrapper.unmount()
})

it('表情等实际 playing 才切换；句间仍待合成时保持情绪，不插 neutral', async () => {
  const { audio, model, store, player, wrapper } = playbackFixture()
  let playStarted!: () => void
  vi.mocked(audio.play).mockImplementation(() => new Promise<void>(resolve => { playStarted = resolve }))
  const playback = player.playNextTts()
  await flushPromises()
  expect(store.ttsPlaybackState).toBe('preparing')
  expect(model.applyAvatarCue).not.toHaveBeenCalled()
  audio.dispatchEvent(new Event('playing'))
  playStarted()
  expect(model.applyAvatarCue).toHaveBeenLastCalledWith({ expression: 'happy', skipResetTimer: true, immediate: true })
  audio.dispatchEvent(new Event('ended'))
  await playback
  expect(store.ttsPlaybackState).toBe('buffering')
  expect(model.applyAvatarCue).not.toHaveBeenCalledWith(expect.objectContaining({ expression: 'neutral' }))
  store.ttsAwaitingAudio = false
  await flushPromises()
  expect(store.ttsPlaybackState).toBe('idle')
  expect(model.applyAvatarCue).toHaveBeenLastCalledWith({ expression: 'neutral', immediate: true })
  wrapper.unmount()
})

it('模型在缓冲期间就绪时先补分析监听，恢复 playing 后接回口型且不重播', async () => {
  const { audio, model, store, live2dStatus, player, wrapper } = playbackFixture('loading')
  const playback = player.playNextTts()
  await flushPromises()
  audio.dispatchEvent(new Event('waiting'))
  live2dStatus.value = 'ready'
  await flushPromises()
  expect(store.ttsPlaybackState).toBe('buffering')
  expect(model.speakAudioElement).toHaveBeenCalledWith(audio)
  expect(model.applyAvatarCue).not.toHaveBeenCalled()
  audio.dispatchEvent(new Event('playing'))
  expect(model.applyAvatarCue).toHaveBeenCalledWith(expect.objectContaining({ expression: 'happy' }))
  expect(audio.play).toHaveBeenCalledOnce()
  audio.dispatchEvent(new Event('ended'))
  await playback
  wrapper.unmount()
})

it('play Promise 永远不返回也能在启动超时后退出，保留文本并给出提示', async () => {
  vi.useFakeTimers()
  const { audio, store, player, wrapper } = playbackFixture()
  vi.mocked(audio.play).mockImplementation(() => new Promise<void>(() => {}))
  const playback = player.playNextTts()
  await Promise.resolve()
  await vi.advanceTimersByTimeAsync(12001)
  await playback
  expect(store.ttsPlaybackActive).toBe(false)
  expect(store.ttsPlaybackState).toBe('error')
  expect(store.ttsPlaybackMessage).toContain('文字回答仍可查看')
  wrapper.unmount()
  vi.useRealTimers()
})


it('等待模型或语音时仍可输入接话，空输入提供停止按钮', async () => {
  const wrapper = mount(AiChatInput, { props: { modelValue: '', isLoading: true, responseActive: true,
    voiceSupported: false, voiceListening: false, voiceInterimText: '', voiceError: '' } })
  expect(wrapper.find('textarea').attributes('disabled')).toBeUndefined()
  expect(wrapper.find('.send-btn').text()).toBe('停止')
  await wrapper.find('.send-btn').trigger('click')
  expect(wrapper.emitted('stopReply')).toHaveLength(1)
  await wrapper.setProps({ modelValue: '等一下，我补充一个条件' })
  await wrapper.find('textarea').trigger('keypress', { key: 'Enter' })
  expect(wrapper.emitted('send')).toHaveLength(1)
  wrapper.unmount()
})
