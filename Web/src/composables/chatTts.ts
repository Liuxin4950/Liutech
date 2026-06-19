import { type Ref } from 'vue'
import { useSequencedBuffer } from '@/composables/useSequencedBuffer'
import { getServiceBaseURL, ServiceType } from '@/config/services'
import type { TtsAudioItem, AvatarCueItem } from '@/stores/chat'

/**
 * TTS 音频队列 + Avatar Cue 管理
 * 从 chat store 中拆分，职责单一
 */
export function useChatTts(state: {
  ttsEnabled: Ref<boolean>
  ttsAvailable: Ref<boolean>
  ttsAwaitingAudio: Ref<boolean>
}) {
  const ttsAudioQueue = useSequencedBuffer<TtsAudioItem>()
  const avatarCueQueue = useSequencedBuffer<AvatarCueItem>()

  const resolveTtsAudioUrl = (audioUrl?: string): string => {
    if (!audioUrl) return ''
    if (audioUrl.startsWith('http://') || audioUrl.startsWith('https://')) return audioUrl
    const base = getServiceBaseURL(ServiceType.MAIN).replace(/\/$/, '')
    if (base.startsWith('/') && audioUrl.startsWith(`${base}/`)) return audioUrl
    if (audioUrl.startsWith('/')) return `${base}${audioUrl}`
    return `${base}/${audioUrl}`
  }

  const setTtsEnabled = (enabled: boolean) => {
    state.ttsEnabled.value = enabled
  }

  const setTtsAvailable = (available: boolean) => {
    state.ttsAvailable.value = available
    if (!available) {
      state.ttsAwaitingAudio.value = false
      setTtsEnabled(false)
    }
  }

  const enqueueTtsAudio = (item: TtsAudioItem) => {
    if (!item) return
    if (typeof item.seq !== 'number' || item.seq <= 0) return
    const now = Date.now()
    const enriched: TtsAudioItem = {
      ...item,
      status: item.status ?? (item.audioUrl ? 'ready' : 'skipped'),
      enqueuedAt: item.enqueuedAt ?? now
    }
    if (enriched.status === 'ready' && enriched.audioUrl) {
      try {
        enriched.audioUrl = resolveTtsAudioUrl(enriched.audioUrl)
        const pre = new Audio(enriched.audioUrl)
        pre.preload = 'auto'
        pre.crossOrigin = 'anonymous'
        pre.load()
        enriched.audioEl = pre
      } catch {
      }
    }
    const cue = avatarCueQueue.shiftBySeq(item.seq)
    if (cue) {
      enriched.cue = cue
    }
    ttsAudioQueue.enqueue(enriched)
  }

  const shiftTtsAudioQueue = (): TtsAudioItem | null => ttsAudioQueue.shift()
  const clearTtsAudioQueue = () => ttsAudioQueue.clear()

  const enqueueAvatarCue = (item: AvatarCueItem) => {
    if (!item) return
    avatarCueQueue.enqueue({ ...item, enqueuedAt: item.enqueuedAt ?? Date.now() })
  }

  const shiftAvatarCueQueue = (): AvatarCueItem | null => avatarCueQueue.shift()
  const shiftAvatarCueQueueBySeq = (seq: number): AvatarCueItem | null => avatarCueQueue.shiftBySeq(seq)
  const clearAvatarCueQueue = () => avatarCueQueue.clear()

  return {
    ttsAudioQueue,
    avatarCueQueue,
    setTtsEnabled,
    setTtsAvailable,
    enqueueTtsAudio,
    shiftTtsAudioQueue,
    clearTtsAudioQueue,
    enqueueAvatarCue,
    shiftAvatarCueQueue,
    shiftAvatarCueQueueBySeq,
    clearAvatarCueQueue
  }
}
