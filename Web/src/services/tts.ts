import { get } from './api'

export interface TtsStatusDTO {
  enabled: boolean
  online: boolean
  baseUrl: string | null
  checkedAt: number
  message?: string | null
}

/**
 * 获取语音推理服务状态
 * - 用于前端决定“语音开关是否可用/是否默认开启”
 */
export const getTtsStatus = async (): Promise<TtsStatusDTO> => {
  const resp = await get<TtsStatusDTO>('/tts/status')
  return resp.data
}

