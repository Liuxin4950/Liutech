import { ref, watch, type Ref } from 'vue'
import type { VoiceRecognitionState } from './avatarController'

interface SpeechRecognitionLike {
  continuous: boolean
  interimResults: boolean
  lang: string
  onstart: ((event: Event) => void) | null
  onend: ((event: Event) => void) | null
  onerror: ((event: any) => void) | null
  onresult: ((event: any) => void) | null
  onspeechend: ((event: Event) => void) | null
  start(): void
  stop(): void
  abort(): void
}

interface SpeechRecognitionConstructor {
  new (): SpeechRecognitionLike
}

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
  }
}

const mapSpeechError = (code?: string) => {
  switch (code) {
    case 'not-allowed':
    case 'service-not-allowed':
      return '语音识别权限被拒绝，请允许浏览器使用麦克风。'
    case 'audio-capture':
      return '未检测到可用麦克风，请检查设备后重试。'
    case 'network':
      return '语音识别网络异常，请稍后重试。'
    case 'no-speech':
      return '没有识别到语音，请再说一次。'
    default:
      return '语音识别暂时不可用，请改用文字输入。'
  }
}

/** Web Speech 输入和轮流免提；每轮识别独立，取消后的旧事件不能写入新输入。 */
export function useVoiceRecognition(chatInput: Ref<string>, options: {
  canListen?: () => boolean
  beforeListen?: () => void
  onSubmit?: () => void | Promise<void>
} = {}) {
  const voiceSupported = ref(false)
  const voiceListening = ref(false)
  const recognitionState = ref<VoiceRecognitionState>('idle')
  const voiceInterimText = ref('')
  const voiceError = ref('')
  const handsFree = ref(false)
  let recognition: SpeechRecognitionLike | null = null
  let resumeTimer: ReturnType<typeof setTimeout> | undefined
  let disposed = false

  const clearResume = () => {
    clearTimeout(resumeTimer)
    resumeTimer = undefined
  }
  const initVoiceRecognition = () => {
    voiceSupported.value = typeof window !== 'undefined'
      && !!(window.SpeechRecognition || window.webkitSpeechRecognition)
  }
  const stopVoiceRecognition = () => {
    clearResume()
    const previous = recognition
    recognition = null // abort 也可能触发回调，先使旧轮次失效。
    voiceListening.value = false
    recognitionState.value = 'idle'
    voiceInterimText.value = ''
    try { previous?.abort() } catch { /* 已结束 */ }
  }
  const disableHandsFree = () => {
    handsFree.value = false
    stopVoiceRecognition()
  }
  const scheduleResume = () => {
    clearResume()
    if (disposed || !handsFree.value || recognition || options.canListen?.() === false) return
    // 留出音频输出收尾时间，避免把最后一瞬的扬声器声音作为下一句输入。
    resumeTimer = setTimeout(() => {
      if (!disposed && handsFree.value && options.canListen?.() !== false) startVoiceRecognition()
    }, 350)
  }
  const startVoiceRecognition = () => {
    if (disposed || !voiceSupported.value || recognition) return
    clearResume()
    voiceError.value = ''
    voiceInterimText.value = ''
    options.beforeListen?.()
    const RecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!RecognitionCtor) return
    const current = new RecognitionCtor()
    recognition = current
    voiceListening.value = true // 包含等待权限/onstart 的阶段，防止重复启动。
    recognitionState.value = 'starting'
    const autoSend = handsFree.value
    let hasFinalText = false
    let failed = false
    current.continuous = !autoSend
    current.interimResults = true
    current.lang = 'zh-CN'
    current.onstart = () => {
      if (recognition === current) { voiceError.value = ''; recognitionState.value = 'listening' }
    }
    current.onresult = (event: any) => {
      if (recognition !== current || failed) return
      let finalText = ''
      let interimText = ''
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        const transcript = event.results[i][0]?.transcript || ''
        if (event.results[i].isFinal) finalText += transcript
        else interimText += transcript
      }
      if (finalText.trim()) {
        hasFinalText = true
        chatInput.value = [chatInput.value.trim(), finalText.trim()].filter(Boolean).join('\n')
      }
      voiceInterimText.value = interimText.trim()
    }
    current.onspeechend = () => {
      if (recognition !== current || !autoSend) return
      // stop 保留最终结果；必须等 onend 才提交，不能发中间识别文本。
      try { current.stop() } catch { /* 浏览器已结束 */ }
    }
    current.onerror = (event: any) => {
      if (recognition !== current) return
      failed = true
      voiceError.value = mapSpeechError(event?.error)
      disableHandsFree() // 权限、网络和静音错误均停止自动重启，保留已识别的草稿。
    }
    current.onend = async () => {
      if (recognition !== current) return
      recognition = null
      voiceListening.value = false
      recognitionState.value = 'idle'
      voiceInterimText.value = ''
      if (!autoSend || !handsFree.value || failed) return
      if (!hasFinalText || !chatInput.value.trim()) {
        voiceError.value = mapSpeechError('no-speech')
        disableHandsFree()
        return
      }
      try {
        await options.onSubmit?.()
      } catch {
        voiceError.value = '发送失败，免提已停止，请检查后重试。'
        disableHandsFree()
      }
      scheduleResume()
    }
    try {
      current.start()
    } catch {
      voiceError.value = '语音识别启动失败，请稍后重试。'
      disableHandsFree()
    }
  }
  const toggleHandsFree = () => {
    if (handsFree.value) { disableHandsFree(); return }
    if (!voiceSupported.value) return
    stopVoiceRecognition()
    handsFree.value = true
    startVoiceRecognition()
  }
  const stopWatching = watch(() => options.canListen?.(), allowed => {
    if (!handsFree.value) return
    if (allowed === false) stopVoiceRecognition()
    else scheduleResume()
  }, { flush: 'post' })
  const cleanupVoiceRecognition = () => {
    disposed = true
    stopWatching()
    disableHandsFree()
  }

  return {
    voiceSupported, voiceListening, recognitionState, voiceInterimText, voiceError, handsFree,
    initVoiceRecognition, startVoiceRecognition, stopVoiceRecognition,
    toggleHandsFree, disableHandsFree, cleanupVoiceRecognition,
  }
}
