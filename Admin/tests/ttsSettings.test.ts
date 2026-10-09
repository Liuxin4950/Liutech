import { expect, it } from 'vitest'
import { copyTtsConfig, sameTtsConfig, ttsStatusLabel } from '../src/utils/ttsSettings'
import type { TtsConfigDTO, TtsStatusDTO } from '../src/services/tts'

const config: TtsConfigDTO = { enabled: true, provider: 'SILICONFLOW', baseUrl: '', voiceModel: '', siliconFlowModel: 'model-a', siliconFlowVoiceUri: 'speech:saved', responseFormat: 'mp3', sampleRate: 44100, speed: 1 }

it('失败或异常配置响应不能变成可提交的默认配置', () => {
  expect(() => copyTtsConfig(null as unknown as TtsConfigDTO)).toThrow()
  expect(() => copyTtsConfig({ code: 503 } as unknown as TtsConfigDTO)).toThrow()
  expect(sameTtsConfig(config, null)).toBe(false)
})

it('修改草稿的音色/模型组合不会改变已保存快照', () => {
  const saved = copyTtsConfig(config)
  const draft = copyTtsConfig(saved)
  draft.siliconFlowModel = 'model-b'
  draft.siliconFlowVoiceUri = 'speech:new'
  expect(saved.siliconFlowModel).toBe('model-a')
  expect(saved.siliconFlowVoiceUri).toBe('speech:saved')
  expect(sameTtsConfig(draft, saved)).toBe(false)
  expect(sameTtsConfig(copyTtsConfig(saved), saved)).toBe(true)
})

it('成功读到的关闭开关和音频参数不得被兜底覆盖', () => {
  expect(copyTtsConfig({ ...config, enabled: false, responseFormat: 'opus', sampleRate: 16000, speed: 0.25 })).toMatchObject({ enabled: false, responseFormat: 'opus', sampleRate: 16000, speed: 0.25 })
})

it('配置齐全但未真实合成时显示待确认，最新检测失败不能沿用旧在线标签', () => {
  const status = { enabled: true, configured: true, online: false, onlineVerified: false } as TtsStatusDTO
  expect(ttsStatusLabel(status)).toBe('待确认')
  expect(ttsStatusLabel({ ...status, online: true, onlineVerified: true })).toBe('在线')
  expect(ttsStatusLabel({ ...status, online: true }, true)).toBe('检测失败')
  expect(ttsStatusLabel({ ...status, enabled: false })).toBe('已关闭')
})
