import { get, post, put, axiosInstance } from './api'

export interface TtsConfigDTO {
  enabled: boolean
  baseUrl: string | null
  voiceModel: string | null
  provider?: 'GPT_SOVITS' | 'SILICONFLOW' | string
  siliconFlowModel?: string | null
  siliconFlowVoiceUri?: string | null
  responseFormat?: string | null
  sampleRate?: number | null
  speed?: number | null
}

export interface TtsStatusDTO {
  enabled: boolean
  online: boolean
  baseUrl: string | null
  voiceModel: string | null
  provider?: string | null
  siliconFlowModel?: string | null
  siliconFlowVoiceUri?: string | null
  responseFormat?: string | null
  sampleRate?: number | null
  speed?: number | null
  siliconFlowApiKeyConfigured?: boolean
  siliconFlowApiKeySource?: string | null
  checkedAt: number
  message?: string | null
}

export interface SiliconFlowVoiceDTO {
  model?: string | null
  customName?: string | null
  text?: string | null
  uri?: string | null
}

export interface TtsSpeechResponseDTO {
  audioUrl: string
  provider: string
  format: string
}

export const getTtsConfig = async (): Promise<TtsConfigDTO> => {
  const resp = await get<TtsConfigDTO>('/admin/tts/config')
  return resp.data
}

export const updateTtsConfig = async (config: TtsConfigDTO): Promise<void> => {
  await put('/admin/tts/config', config)
}

export const getTtsStatus = async (): Promise<TtsStatusDTO> => {
  const resp = await get<TtsStatusDTO>('/admin/tts/status')
  return resp.data
}

export const getTtsVoices = async (baseUrl?: string): Promise<string[]> => {
  const resp = await get<string[]>('/admin/tts/voices', baseUrl ? { baseUrl } : {})
  return resp.data || []
}

export const getSiliconFlowVoices = async (): Promise<SiliconFlowVoiceDTO[]> => {
  const resp = await get<SiliconFlowVoiceDTO[]>('/admin/tts/siliconflow/voices')
  return resp.data || []
}

export const uploadSiliconFlowVoice = async (
  file: File,
  model: string,
  customName: string,
  text: string
): Promise<SiliconFlowVoiceDTO> => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('model', model)
  formData.append('customName', customName)
  formData.append('text', text)
  const response = await axiosInstance.post('/admin/tts/siliconflow/voice', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data.data
}

export const testTtsSpeech = async (text: string): Promise<TtsSpeechResponseDTO> => {
  const resp = await post<TtsSpeechResponseDTO>('/admin/tts/test-speech', { text })
  return resp.data
}

export const resolveMainAudioUrl = (audioUrl?: string | null): string => {
  if (!audioUrl) return ''
  if (audioUrl.startsWith('http://') || audioUrl.startsWith('https://')) return audioUrl
  const base = String(axiosInstance.defaults.baseURL || '').replace(/\/$/, '')
  if (base.startsWith('/') && audioUrl.startsWith(`${base}/`)) return audioUrl
  if (audioUrl.startsWith('/')) return `${base}${audioUrl}`
  return `${base}/${audioUrl}`
}
