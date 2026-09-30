import { ref, reactive, defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import { useTtsPlayer } from '@/composables/useTtsPlayer'
import { useChatTts } from '@/composables/chatTts'

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
