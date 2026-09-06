import { ref } from 'vue'
import { expect, it } from 'vitest'
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
