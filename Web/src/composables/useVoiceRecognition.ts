import { ref, type Ref } from 'vue'

interface SpeechRecognitionLike {
  continuous: boolean
  interimResults: boolean
  lang: string
  onstart: ((event: Event) => void) | null
  onend: ((event: Event) => void) | null
  onerror: ((event: any) => void) | null
  onresult: ((event: any) => void) | null
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

/**
 * 语音识别 composable
 *
 * 职责：封装 Web Speech API，提供开始/停止语音识别、
 * 实时中间结果和最终文本拼接能力。
 * 从 AiChat.vue 中提取。
 */
export function useVoiceRecognition(chatInput: Ref<string>) {
  const voiceSupported = ref(false)
  const voiceListening = ref(false)
  const voiceInterimText = ref('')
  const voiceError = ref('')
  const recognition = ref<SpeechRecognitionLike | null>(null)

  const initVoiceRecognition = () => {
    if (typeof window === 'undefined') return
    const RecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!RecognitionCtor) {
      voiceSupported.value = false
      return
    }

    voiceSupported.value = true
    const speechRecognition = new RecognitionCtor()
    speechRecognition.continuous = true
    speechRecognition.interimResults = true
    speechRecognition.lang = 'zh-CN'

    speechRecognition.onstart = () => {
      voiceListening.value = true
      voiceError.value = ''
    }

    speechRecognition.onresult = (event: any) => {
      let finalText = ''
      let interimText = ''
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        const transcript = event.results[i][0]?.transcript || ''
        if (event.results[i].isFinal) {
          finalText += transcript
        } else {
          interimText += transcript
        }
      }

      if (finalText.trim()) {
        chatInput.value = [chatInput.value.trim(), finalText.trim()].filter(Boolean).join(chatInput.value.trim() ? '\n' : '')
      }
      voiceInterimText.value = interimText.trim()
    }

    speechRecognition.onerror = (event: any) => {
      voiceError.value = mapSpeechError(event?.error)
      voiceListening.value = false
      voiceInterimText.value = ''
    }

    speechRecognition.onend = () => {
      voiceListening.value = false
      voiceInterimText.value = ''
    }

    recognition.value = speechRecognition
  }

  const startVoiceRecognition = () => {
    if (!voiceSupported.value || voiceListening.value || !recognition.value) return
    voiceError.value = ''
    voiceInterimText.value = ''
    try {
      recognition.value.start()
    } catch {
      voiceError.value = '语音识别启动失败，请稍后重试。'
    }
  }

  const stopVoiceRecognition = () => {
    if (!voiceListening.value || !recognition.value) return
    try {
      recognition.value.stop()
    } catch {
      // 忽略
    }
  }

  const cleanupVoiceRecognition = () => {
    stopVoiceRecognition()
    recognition.value?.abort()
  }

  return {
    voiceSupported,
    voiceListening,
    voiceInterimText,
    voiceError,
    initVoiceRecognition,
    startVoiceRecognition,
    stopVoiceRecognition,
    cleanupVoiceRecognition,
  }
}
