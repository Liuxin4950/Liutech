import { watch, type Ref } from 'vue'
import { getServiceBaseURL, ServiceType } from '@/services/serviceConfig'
import type { useChatStore } from '@/stores/chat'
import type Live2d from '@/components/Live2d.vue'
import type BottomNavigation from '@/components/BottomNavigation.vue'

type ChatStore = ReturnType<typeof useChatStore>

/**
 * TTS 播放器 + Avatar Cue 调度 + 音乐互斥
 *
 * 职责：
 * - 消费 chatStore 的 TTS 音频队列，驱动 Live2d 口型同步
 * - 消费 chatStore 的 Avatar Cue 队列，驱动 Live2d 表情
 * - TTS 播放期间暂停音乐，结束后恢复
 * - 模型隐藏时停止播放，显示时恢复
 *
 * 从 MainLayout 提取，集中 TTS 相关的播放 / watcher / 音乐桥接逻辑，
 * 让 MainLayout 只负责布局编排，不再承担 TTS 宿主职责。
 */
export function useTtsPlayer(options: {
  chatStore: ChatStore
  live2dRef: Ref<InstanceType<typeof Live2d> | null>
  bottomNavRef: Ref<InstanceType<typeof BottomNavigation> | null>
}) {
  const { chatStore, live2dRef, bottomNavRef } = options

  let isTtsPlaying = false
  let playbackToken = 0
  let currentTtsAudio: HTMLAudioElement | null = null
  let isApplyingAvatarCue = false
  let audioUnlocked = false
  // TTS 播放期间是否需要在结束后恢复音乐
  let shouldResumeMusicAfterSpeech = false

  const resumeMusicAfterSpeechIfNeeded = () => {
    if (!shouldResumeMusicAfterSpeech) return
    shouldResumeMusicAfterSpeech = false
    bottomNavRef.value?.resumeMusic?.()
  }

  function resolveTtsPlayUrl(audioUrl?: string): string {
    if (!audioUrl) return ''
    if (audioUrl.startsWith('/')) {
      const base = getServiceBaseURL(ServiceType.MAIN).replace(/\/$/, '')
      if (base.startsWith('/') && audioUrl.startsWith(`${base}/`)) return audioUrl
      return `${base}${audioUrl}`
    }
    return audioUrl
  }

  function stopTtsPlayback() {
    playbackToken++
    try { currentTtsAudio?.pause() } catch {}
    currentTtsAudio = null
    isTtsPlaying = false
    resumeMusicAfterSpeechIfNeeded()
  }

  function waitOnce(audio: HTMLAudioElement, event: string, timeoutMs: number): Promise<boolean> {
    return new Promise((resolve) => {
      let done = false
      const timer = window.setTimeout(() => {
        if (done) return
        done = true
        resolve(false)
      }, timeoutMs)
      audio.addEventListener(event, () => {
        if (done) return
        done = true
        window.clearTimeout(timer)
        resolve(true)
      }, { once: true })
    })
  }

  const delay = (ms: number) => new Promise<void>((r) => window.setTimeout(r, ms))

  async function playNextTts() {
    if (isTtsPlaying) return
    if (chatStore.ttsEnabled !== true || chatStore.ttsAvailable !== true) return
    if (!live2dRef.value) return

    const token = playbackToken
    isTtsPlaying = true

    try {
      while (token === playbackToken) {
        if (chatStore.ttsEnabled !== true || chatStore.ttsAvailable !== true) break
        if (!live2dRef.value) break

        const next = chatStore.shiftTtsAudioQueue()
        if (!next) break

        if (next.cue && next.cue.expression !== 'neutral') {
          live2dRef.value.applyAvatarCue?.({ ...next.cue, skipResetTimer: true })
        }

        if (next.status === 'skipped') {
          console.warn(`[TTS][skip] seq=${next.seq} reason=${next.reason ?? 'unknown'}`)
          continue
        }

        const playUrl = resolveTtsPlayUrl(next.audioUrl)
        if (!playUrl) continue

        let audio = next.audioEl
          ? await live2dRef.value.speakAudioElement(next.audioEl)
          : await live2dRef.value.speakAudioUrl(playUrl)
        if (!audio) continue
        currentTtsAudio = audio

        let started = false
        for (let attempt = 0; attempt < 6 && token === playbackToken; attempt++) {
          try {
            await audio.play()
            started = true
            break
          } catch (e: any) {
            if (e?.name === 'NotAllowedError') break
            try { audio.pause() } catch {}
            currentTtsAudio = null
            await delay(250 + attempt * 200)
            if (!live2dRef.value) break
            const retryAudio = await live2dRef.value.speakAudioUrl(playUrl)
            if (!retryAudio) break
            audio = retryAudio
            currentTtsAudio = audio
          }
        }

        if (!started) {
          try { currentTtsAudio?.pause() } catch {}
          currentTtsAudio = null
          continue
        }

        await Promise.race([
          waitOnce(audio, 'ended', 60000),
          waitOnce(audio, 'error', 60000),
          waitOnce(audio, 'pause', 60000)
        ])

        currentTtsAudio = null
      }
    } finally {
      if (token === playbackToken) {
        isTtsPlaying = false
        live2dRef.value?.applyAvatarCue?.({ expression: 'neutral' })
        resumeMusicAfterSpeechIfNeeded()
      }
    }
  }

  async function applyNextAvatarCues() {
    if (isApplyingAvatarCue) return
    if (isTtsPlaying || chatStore.ttsAwaitingAudio || chatStore.ttsPendingCount > 0) return
    if (!live2dRef.value || !chatStore.showModel) return
    isApplyingAvatarCue = true
    try {
      while (live2dRef.value && chatStore.showModel) {
        const next = chatStore.shiftAvatarCueQueue()
        if (!next) break
        live2dRef.value.applyAvatarCue?.(next)
        await delay(120)
      }
    } finally {
      isApplyingAvatarCue = false
    }
  }

  async function unlockAudio() {
    if (audioUnlocked) return
    audioUnlocked = true
    try {
      const Ctx = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext | undefined
      if (!Ctx) return
      const ctx = new Ctx()
      const gain = ctx.createGain()
      gain.gain.value = 0
      const osc = ctx.createOscillator()
      osc.connect(gain)
      gain.connect(ctx.destination)
      try { await ctx.resume() } catch {}
      osc.start()
      osc.stop(ctx.currentTime + 0.01)
      window.setTimeout(() => { ctx.close().catch(() => {}) }, 50)
    } catch {}
  }

  /** 音乐播放：lipSync 单实例，TTS 在播时不抢占口型，音乐本身仍正常播放 */
  const handleMusicPlay = (audio: HTMLAudioElement) => {
    if (isTtsPlaying) return
    live2dRef.value?.startMusicLipSync?.(audio)
  }

  const handleMusicPause = () => {
    live2dRef.value?.stopMusicLipSync?.()
  }

  /** TTS 播放前：暂停音乐并标记结束后恢复 */
  const handleSpeakStart = () => {
    const nav = bottomNavRef.value
    if (nav?.isMusicPlaying?.()) {
      shouldResumeMusicAfterSpeech = true
      nav.pauseMusic?.()
    }
  }

  // ===== watcher：chatStore 状态 -> 播放器动作 =====

  // TTS 开关/可用性/队列变化：禁用时停止播放并清队列，启用且有待播音频时开始播放
  watch(
    () => [chatStore.ttsEnabled, chatStore.ttsAvailable, chatStore.ttsPendingCount],
    () => {
      if (chatStore.ttsEnabled !== true || chatStore.ttsAvailable !== true) {
        stopTtsPlayback()
        chatStore.clearTtsAudioQueue()
        return
      }
      playNextTts()
    }
  )

  // 新消息发送时（ttsCancelCounter 递增），停止当前 TTS 播放，避免旧音频和新回复重叠
  watch(
    () => chatStore.ttsCancelCounter,
    () => {
      stopTtsPlayback()
    }
  )

  // TTS 关闭时，独立的 avatar-cue 仍可驱动表情变化
  watch(
    () => chatStore.avatarCuePendingCount,
    () => {
      applyNextAvatarCues()
    }
  )

  // 模型显示/隐藏：隐藏时停播放，显示时恢复并刷新
  // setTimeout(0) 延迟到下一个 tick，确保 showModel 的 DOM 更新完成后再执行
  watch(
    () => chatStore.showModel,
    (visible) => {
      if (!visible) {
        stopTtsPlayback()
        return
      }
      setTimeout(() => {
        playNextTts()
        applyNextAvatarCues()
        live2dRef.value?.refresh?.()
      }, 0)
    }
  )

  return {
    stopTtsPlayback,
    playNextTts,
    applyNextAvatarCues,
    unlockAudio,
    isTtsPlaying: () => isTtsPlaying,
    handleMusicPlay,
    handleMusicPause,
    handleSpeakStart
  }
}
