import { get, put } from './api'

export interface TtsConfigDTO {
  enabled: boolean
  baseUrl: string | null
  voiceModel: string | null
}

export interface TtsStatusDTO {
  enabled: boolean
  online: boolean
  baseUrl: string | null
  voiceModel: string | null
  checkedAt: number
  message?: string | null
}

export const getTtsConfig = async (): Promise<TtsConfigDTO> => {
  const resp = await get<TtsConfigDTO>('/admin/tts/config')
  return resp.data
}

export const updateTtsConfig = async (config: TtsConfigDTO): Promise<void> => {
  await put('/admin/tts/config', config)
}

export const getTtsStatus = async (): Promise<TtsStatusDTO> => {
  const resp = await get<TtsStatusDTO>('/tts/status')
  return resp.data
}

export const getTtsVoices = async (baseUrl?: string): Promise<string[]> => {
  const resp = await get<string[]>('/admin/tts/voices', baseUrl ? { baseUrl } : {})
  return resp.data || []
}
