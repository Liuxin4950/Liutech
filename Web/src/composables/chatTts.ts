import { type Ref } from 'vue'
import { useSequencedBuffer } from '@/composables/useSequencedBuffer'
import { getServiceBaseURL, ServiceType } from '@/services/serviceConfig'
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
    const base = getServiceBaseURL(ServiceType.AI).replace(/\/$/, '')
    return `${base}/${audioUrl.replace(/^\/+/, '')}`
  }

  const setTtsEnabled = (enabled: boolean) => {
    state.ttsEnabled.value = enabled
  }

  const setTtsAvailable = (available: boolean) => {
    state.ttsAvailable.value = available
    if (!available) {
      state.ttsAwaitingAudio.value = false
      ttsAudioQueue.clear()
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
        const pre = new Audio()
        pre.preload = 'auto'
        pre.crossOrigin = 'anonymous'
        pre.src = enriched.audioUrl
        pre.load()
        enriched.audioEl = pre
      } catch {
      }
    }
    ttsAudioQueue.enqueue(enriched)
  }

  const shiftTtsAudioQueue = (): TtsAudioItem | null => {
    const item = ttsAudioQueue.shift()
    if (item && !item.cue) item.cue = avatarCueQueue.shiftBySeq(item.seq) ?? undefined
    return item
  }
  /** audio-complete/断流后填补未返回的序号，不让后续可播音频永久卡住。 */
  const finishTtsAudio = (totalSegments?: number) => {
    const known = [...Object.keys(ttsAudioQueue.buffer.value), ...Object.keys(avatarCueQueue.buffer.value)].map(Number)
    const last = Number.isSafeInteger(totalSegments) && totalSegments! >= 0
      ? totalSegments! : Math.max(0, ...known)
    // 保护客户端循环；正常单轮回复远少于这个上限。
    if (last > 4096) return 0
    let missing = 0
    for (let seq = ttsAudioQueue.nextSeq.value; seq <= last; seq++) {
      if (!ttsAudioQueue.buffer.value[seq]) {
        missing++
        ttsAudioQueue.enqueue({ seq, status: 'skipped', reason: 'audio-missing' })
      }
    }
    return missing
  }
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
    finishTtsAudio,
    clearTtsAudioQueue,
    enqueueAvatarCue,
    shiftAvatarCueQueue,
    shiftAvatarCueQueueBySeq,
    clearAvatarCueQueue
  }
}
