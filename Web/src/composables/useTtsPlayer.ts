import { watch, onBeforeUnmount, type Ref } from 'vue'
import { getServiceBaseURL, ServiceType } from '@/services/serviceConfig'
import type { AvatarCueItem, useChatStore } from '@/stores/chat'
import { resumeAudioContext } from '@/composables/useAudioLipSync'
import type Live2d from '@/components/Live2d.vue'
import type BottomNavigation from '@/components/BottomNavigation.vue'

type ChatStore = ReturnType<typeof useChatStore>

/** 唯一播放协调入口：声音不等待模型下载；模型 ready 后补接口型，表情以实际 playing 为准。 */
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
  let activeCue: AvatarCueItem | undefined
  let cancelWait: (() => void) | null = null
  let musicResumeVersion: number | null = null
  let disposed = false
  let hasSpeech = false
  let showingThinking = false
  let keepTimedCue = false
  let lastAudioEnd: number | null = null
  const canPlay = () => !disposed && chatStore.showModel
  const ready = () => canPlay() && live2dStatus.value === 'ready' && !!live2dRef.value
  const applySpeakingCue = () => {
    if (ready() && activeCue) live2dRef.value?.applyAvatarCue({ ...activeCue, skipResetTimer: true, immediate: true })
  }
  const syncMusic = () => {
    if (!ready() || isTtsPlaying || chatStore.voiceInputActive) return
    const audio = bottomNavRef.value?.getCurrentAudio()
    if (audio && !audio.paused && !audio.ended) live2dRef.value?.startMusicLipSync(audio)
    else live2dRef.value?.stopMusicLipSync()
  }
  const resumeMusic = () => {
    if (chatStore.voiceInputActive) return
    const version = musicResumeVersion
    musicResumeVersion = null
    const nav = bottomNavRef.value
    if (!disposed && version !== null && nav?.getMusicActionVersion() === version) void nav.resumeMusic()
  }
  const finishTurn = () => {
    if (isTtsPlaying || chatStore.isLoading || chatStore.ttsAwaitingAudio || chatStore.ttsPendingCount > 0) return
    if (chatStore.ttsPlaybackState !== 'error') chatStore.ttsPlaybackState = 'idle'
    if ((hasSpeech || showingThinking) && !keepTimedCue) live2dRef.value?.applyAvatarCue({ expression: 'neutral', immediate: true })
    hasSpeech = false
    showingThinking = false
    resumeMusic()
    syncMusic()
  }
  const stopTtsPlayback = () => {
    playbackToken++
    cancelWait?.()
    cancelWait = null
    currentTtsAudio?.pause()
    currentTtsAudio = null
    activeCue = undefined
    isTtsPlaying = false
    chatStore.ttsPlaybackActive = false
    chatStore.ttsPlaybackState = 'idle'
    lastAudioEnd = null
    if (hasSpeech || showingThinking) live2dRef.value?.applyAvatarCue({ expression: 'neutral', immediate: true })
    hasSpeech = false
    showingThinking = false
    keepTimedCue = false
    live2dRef.value?.stopMusicLipSync()
    resumeMusic()
    syncMusic()
  }
  const handleSpeakStart = () => {
    const nav = bottomNavRef.value
    if (nav?.isMusicPlaying()) {
      musicResumeVersion = nav.getMusicActionVersion()
      nav.pauseMusic()
    }
  }
  // 超时也能结束等待，即使浏览器的 play() Promise 永远不返回。
  const waitForAudio = (audio: HTMLAudioElement) => new Promise<'ended' | 'error' | 'cancelled' | 'timeout'>(resolve => {
    let timer: ReturnType<typeof setTimeout>
    const arm = (ms: number) => { clearTimeout(timer); timer = setTimeout(() => finish('timeout'), ms) }
    const ended = () => finish('ended')
    const failed = () => finish('error')
    const cancel = () => finish('cancelled')
    const playing = () => {
      chatStore.ttsPlaybackState = 'playing'
      chatStore.recordSpeechPlayback?.()
      if (lastAudioEnd !== null && chatStore.speechTimings) {
        chatStore.speechTimings.maxGapMs = Math.max(chatStore.speechTimings.maxGapMs, performance.now() - lastAudioEnd)
        lastAudioEnd = null
      }
      applySpeakingCue()
      arm(Number.isFinite(audio.duration) ? Math.max(15000, (audio.duration - audio.currentTime + 10) * 1000) : 60000)
    }
    const waiting = () => { chatStore.ttsPlaybackState = 'buffering'; arm(10000) }
    const finish = (result: 'ended' | 'error' | 'cancelled' | 'timeout') => {
      clearTimeout(timer)
      audio.removeEventListener('ended', ended)
      audio.removeEventListener('error', failed)
      audio.removeEventListener('playing', playing)
      audio.removeEventListener('waiting', waiting)
      audio.removeEventListener('stalled', waiting)
      if (cancelWait === cancel) cancelWait = null
      audio.pause()
      resolve(result)
    }
    cancelWait = cancel
    audio.addEventListener('ended', ended)
    audio.addEventListener('error', failed)
    audio.addEventListener('playing', playing)
    audio.addEventListener('waiting', waiting)
    audio.addEventListener('stalled', waiting)
    arm(12000)
  })
  const playNextTts = async () => {
    if (isTtsPlaying || !canPlay() || !chatStore.ttsEnabled || !chatStore.ttsAvailable) return
    const token = playbackToken
    isTtsPlaying = true
    try {
      while (token === playbackToken && canPlay() && chatStore.ttsEnabled && chatStore.ttsAvailable) {
        const item = chatStore.shiftTtsAudioQueue()
        if (!item) break
        keepTimedCue = !!item.cue && (item.status === 'skipped' || !item.audioUrl)
        if (item.status === 'skipped' || !item.audioUrl) {
          if (item.cue && ready()) live2dRef.value?.applyAvatarCue({ ...item.cue, skipResetTimer: false })
          continue
        }
        hasSpeech = true
        chatStore.ttsPlaybackActive = true
        chatStore.ttsPlaybackState = 'preparing'
        handleSpeakStart()
        const base = getServiceBaseURL(ServiceType.AI).replace(/\/$/, '')
        const url = /^https?:\/\//.test(item.audioUrl) ? item.audioUrl : `${base}/${item.audioUrl.replace(/^\/+/, '')}`
        let audio = item.audioEl
        if (!audio) { audio = new Audio(); audio.crossOrigin = 'anonymous'; audio.preload = 'auto'; audio.src = url }
        currentTtsAudio = audio
        activeCue = item.cue
        // 只接分析支路；模型仍在下载或加载失败时，原生音频照常播放。
        if (ready()) {
          try { await live2dRef.value!.speakAudioElement(audio) }
          catch (error) { console.warn('[TTS] 模型口型未接入，继续播放声音', error) }
        }
        if (token !== playbackToken || !canPlay()) { audio.pause(); break }
        const done = waitForAudio(audio)
        try { void audio.play().catch(() => audio.dispatchEvent(new Event('error'))) }
        catch { audio.dispatchEvent(new Event('error')) }
        const outcome = await done
        if (token !== playbackToken) break
        currentTtsAudio = null
        activeCue = undefined
        if (outcome !== 'ended') {
          chatStore.cancelTts()
          chatStore.ttsPlaybackState = 'error'
          chatStore.ttsPlaybackMessage = '语音未能继续播放，文字回答仍可查看；可点击接话或关闭后重新开启语音。'
          break
        }
        lastAudioEnd = performance.now()
      }
    } finally {
      if (token === playbackToken) {
        isTtsPlaying = false
        chatStore.ttsPlaybackActive = false
        live2dRef.value?.stopMusicLipSync()
        if (chatStore.ttsAwaitingAudio || chatStore.ttsPendingCount > 0) chatStore.ttsPlaybackState = hasSpeech ? 'buffering' : 'idle'
        finishTurn()
      }
    }
  }
  const updateThinkingCue = () => {
    if (ready() && chatStore.aiThinking && !isTtsPlaying && !hasSpeech) {
      showingThinking = true
      live2dRef.value?.applyAvatarCue({ expression: 'thinking', skipResetTimer: true, immediate: true })
    }
  }
  const applyNextAvatarCues = () => {
    if (!ready() || isTtsPlaying || chatStore.ttsAwaitingAudio || chatStore.ttsPendingCount > 0) return
    let cue = chatStore.shiftAvatarCueQueue()
    while (cue) { live2dRef.value?.applyAvatarCue(cue); cue = chatStore.shiftAvatarCueQueue() }
  }
  const unlockAudio = () => { void resumeAudioContext().then(syncMusic).catch(error => console.warn('[audio] 请点击播放以启用声音', error)) }
  const handleMusicPlay = (_audio: HTMLAudioElement) => {
    if (isTtsPlaying || chatStore.ttsAwaitingAudio || chatStore.ttsPendingCount > 0) chatStore.cancelTts()
    syncMusic()
  }
  watch(() => chatStore.voiceInputActive, active => {
    if (active) handleSpeakStart()
    else finishTurn()
  }, { flush: 'sync' })
  const handleMusicPause = () => { if (!isTtsPlaying) live2dRef.value?.stopMusicLipSync() }
  watch(() => [chatStore.ttsEnabled, chatStore.ttsAvailable, chatStore.ttsPendingCount, chatStore.ttsAwaitingAudio, chatStore.isLoading, chatStore.aiThinking], () => {
    if (!chatStore.ttsEnabled || !chatStore.ttsAvailable) { stopTtsPlayback(); chatStore.clearTtsAudioQueue() }
    else { updateThinkingCue(); void playNextTts(); finishTurn(); applyNextAvatarCues() }
  })
  watch(() => chatStore.ttsCancelCounter, stopTtsPlayback, { flush: 'sync' })
  watch(() => chatStore.avatarCuePendingCount, applyNextAvatarCues)
  watch(() => [chatStore.showModel, live2dStatus.value], async () => {
    if (!chatStore.showModel) { chatStore.cancelTts(); stopTtsPlayback(); return }
    updateThinkingCue()
    const audio = currentTtsAudio
    const token = playbackToken
    if (ready() && audio) {
      await live2dRef.value?.speakAudioElement(audio)
      if (token === playbackToken && currentTtsAudio === audio && !audio.paused) applySpeakingCue()
    } else { syncMusic(); void playNextTts(); applyNextAvatarCues() }
  }, { flush: 'post' })
  onBeforeUnmount(() => { disposed = true; stopTtsPlayback() })
  return { stopTtsPlayback, playNextTts, applyNextAvatarCues, unlockAudio, syncMusic, isTtsPlaying: () => isTtsPlaying, handleMusicPlay, handleMusicPause, handleSpeakStart }
}
