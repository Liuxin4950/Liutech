import { watch, onBeforeUnmount, type Ref } from 'vue'
import { getServiceBaseURL, ServiceType } from '@/services/serviceConfig'
import { resumeAudioContext, disposeAudioContext } from '@/composables/useAudioLipSync'
import type { useChatStore } from '@/stores/chat'
import type Live2d from '@/components/Live2d.vue'
import type BottomNavigation from '@/components/BottomNavigation.vue'

type ChatStore = ReturnType<typeof useChatStore>

/** 唯一播放协调入口：TTS 优先；模型就绪后才消费队列，音乐始终由 MusicCapsule 管理。 */
export function useTtsPlayer(options: {
  chatStore: ChatStore
  live2dRef: Ref<InstanceType<typeof Live2d> | null>
  bottomNavRef: Ref<InstanceType<typeof BottomNavigation> | null>
  live2dStatus: Ref<'idle' | 'loading' | 'ready' | 'error'>
}) {
  const { chatStore, live2dRef, bottomNavRef, live2dStatus } = options
  let isTtsPlaying = false
  let playbackToken = 0
  let currentTtsAudio: HTMLAudioElement | null = null
  let cancelWait: (() => void) | null = null
  let musicResumeVersion: number | null = null
  let disposed = false
  const ready = () => !disposed && live2dStatus.value === 'ready' && chatStore.showModel && !!live2dRef.value

  const syncMusic = () => {
    if (!ready() || isTtsPlaying) return
    const audio = bottomNavRef.value?.getCurrentAudio()
    if (audio && !audio.paused && !audio.ended) live2dRef.value?.startMusicLipSync(audio)
    else live2dRef.value?.stopMusicLipSync()
  }
  const resumeMusic = () => {
    const version = musicResumeVersion
    musicResumeVersion = null
    const nav = bottomNavRef.value
    if (!disposed && version !== null && nav?.getMusicActionVersion() === version) void nav.resumeMusic()
  }
  const stopTtsPlayback = () => {
    playbackToken++
    cancelWait?.()
    cancelWait = null
    currentTtsAudio?.pause()
    currentTtsAudio = null
    isTtsPlaying = false
    live2dRef.value?.stopMusicLipSync()
    resumeMusic()
    syncMusic()
  }
  // 一个等待拥有全部事件和超时；取消、完成、失败都走同一清理出口。
  const waitForAudio = (audio: HTMLAudioElement) => new Promise<void>(resolve => {
    let timer: ReturnType<typeof setTimeout>
    const finish = () => {
      clearTimeout(timer)
      for (const event of ['ended', 'error', 'pause']) audio.removeEventListener(event, finish)
      if (cancelWait === finish) cancelWait = null
      audio.pause()
      resolve()
    }
    cancelWait = finish
    for (const event of ['ended', 'error', 'pause']) audio.addEventListener(event, finish)
    timer = setTimeout(finish, 60000)
  })
  const handleSpeakStart = () => {
    const nav = bottomNavRef.value
    if (nav?.isMusicPlaying()) {
      musicResumeVersion = nav.getMusicActionVersion()
      nav.pauseMusic()
    }
  }
  const playNextTts = async () => {
    if (isTtsPlaying || !ready() || !chatStore.ttsEnabled || !chatStore.ttsAvailable) return
    const token = playbackToken
    isTtsPlaying = true
    try {
      while (token === playbackToken && ready() && chatStore.ttsEnabled && chatStore.ttsAvailable) {
        const item = chatStore.shiftTtsAudioQueue()
        if (!item) break
        if (item.status === 'skipped' || !item.audioUrl) continue
        if (item.cue) live2dRef.value?.applyAvatarCue({ ...item.cue, skipResetTimer: true })
        const base = getServiceBaseURL(ServiceType.MAIN).replace(/\/$/, '')
        const url = item.audioUrl.startsWith('/') && !item.audioUrl.startsWith(`${base}/`) ? `${base}${item.audioUrl}` : item.audioUrl
        const audio = item.audioEl
          ? await live2dRef.value!.speakAudioElement(item.audioEl)
          : await live2dRef.value!.speakAudioUrl(url)
        if (!audio) continue
        if (token !== playbackToken || !ready()) { audio.pause(); break }
        currentTtsAudio = audio
        const done = waitForAudio(audio)
        try {
          await audio.play()
          if (token !== playbackToken || !ready()) audio.pause()
          await done
        } catch (error) {
          if (token !== playbackToken) { audio.pause(); break }
          cancelWait?.()
          console.warn('[TTS] 本段播放失败，可重新开启语音后发送下一条消息', error)
        }
        if (token !== playbackToken) break
        currentTtsAudio = null
      }
    } finally {
      if (token === playbackToken) {
        isTtsPlaying = false
        live2dRef.value?.stopMusicLipSync()
        live2dRef.value?.applyAvatarCue({ expression: 'neutral' })
        if (!chatStore.ttsAwaitingAudio && chatStore.ttsPendingCount === 0) resumeMusic()
        syncMusic()
      }
    }
  }
  const applyNextAvatarCues = () => {
    if (!ready() || isTtsPlaying || chatStore.ttsAwaitingAudio || chatStore.ttsPendingCount > 0) return
    let cue = chatStore.shiftAvatarCueQueue()
    while (cue) { live2dRef.value?.applyAvatarCue(cue); cue = chatStore.shiftAvatarCueQueue() }
  }
  const unlockAudio = () => { void resumeAudioContext().then(syncMusic).catch(error => console.warn('[audio] 请点击播放以启用声音', error)) }
  const handleMusicPlay = (_audio: HTMLAudioElement) => {
    if (isTtsPlaying) chatStore.cancelTts() // 用户主动播放音乐时取消本轮，迟到音频也不能再抢占。
    syncMusic()
  }
  const handleMusicPause = () => { if (!isTtsPlaying) live2dRef.value?.stopMusicLipSync() }
  watch(() => [chatStore.ttsEnabled, chatStore.ttsAvailable, chatStore.ttsPendingCount, chatStore.ttsAwaitingAudio], () => {
    if (!chatStore.ttsEnabled || !chatStore.ttsAvailable) { stopTtsPlayback(); chatStore.clearTtsAudioQueue() }
    else void playNextTts()
  })
  watch(() => chatStore.ttsCancelCounter, stopTtsPlayback, { flush: 'sync' })
  watch(() => chatStore.avatarCuePendingCount, applyNextAvatarCues)
  watch(() => [chatStore.showModel, live2dStatus.value], () => {
    if (!chatStore.showModel) { chatStore.cancelTts(); stopTtsPlayback(); return }
    if (ready()) { syncMusic(); void playNextTts(); applyNextAvatarCues() }
    else stopTtsPlayback()
  }, { flush: 'post' })
  onBeforeUnmount(() => { disposed = true; stopTtsPlayback(); disposeAudioContext() })
  return { stopTtsPlayback, playNextTts, applyNextAvatarCues, unlockAudio, syncMusic, isTtsPlaying: () => isTtsPlaying, handleMusicPlay, handleMusicPause, handleSpeakStart }
}
