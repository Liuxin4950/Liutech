import type { TtsConfigDTO, TtsStatusDTO } from '@/services/tts'

/** 只归一化成功读取的配置；异常响应不得变成可提交的默认配置。 */
export function copyTtsConfig(config: TtsConfigDTO): TtsConfigDTO {
  if (!config || typeof config.enabled !== 'boolean') throw new Error('语音配置响应格式不正确')
  return {
    enabled: config.enabled,
    baseUrl: config.baseUrl?.trim() || '',
    voiceModel: config.voiceModel?.trim() || '',
    provider: config.provider || 'GPT_SOVITS',
    siliconFlowModel: config.siliconFlowModel?.trim() || 'FunAudioLLM/CosyVoice2-0.5B',
    siliconFlowVoiceUri: config.siliconFlowVoiceUri?.trim() || '',
    responseFormat: config.responseFormat || 'mp3',
    sampleRate: config.sampleRate ?? 44100,
    speed: config.speed ?? 1
  }
}

export function sameTtsConfig(draft: TtsConfigDTO, saved: TtsConfigDTO | null): boolean {
  return !!saved && JSON.stringify(copyTtsConfig(draft)) === JSON.stringify(copyTtsConfig(saved))
}

export function ttsStatusLabel(status: TtsStatusDTO | null, failed = false): string {
  if (failed) return '检测失败'
  if (!status) return '尚未检测'
  if (!status.enabled) return '已关闭'
  if (status.online) return '在线'
  if (status.configured && status.onlineVerified === false) return '待确认'
  return '离线'
}
