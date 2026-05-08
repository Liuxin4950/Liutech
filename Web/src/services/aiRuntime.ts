import { get } from './api'

export interface RuntimeTtsStatusDTO {
  enabled: boolean
  online: boolean
  provider?: string | null
  checkedAt: number
  message?: string | null
}

export interface AiRuntimeDTO {
  aiOnline: boolean
  aiMessage?: string | null
  defaultModel: string | null
  tts: RuntimeTtsStatusDTO
}

export const getAiRuntime = async (): Promise<AiRuntimeDTO> => {
  const resp = await get<AiRuntimeDTO>('/runtime/ai')
  return resp.data
}
