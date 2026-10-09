import { beforeEach, expect, it, vi } from 'vitest'
const post = vi.hoisted(() => vi.fn())
vi.mock('../src/services/aiClient', () => ({ aiApi: { post } }))
vi.mock('../src/services/serviceConfig', () => ({ getAiBaseUrl: () => '/ai' }))
import { testTtsSpeech, uploadSiliconFlowVoice } from '../src/services/tts'

beforeEach(() => { post.mockReset() })
it('音色上传按 AI 控制器的原始 DTO 契约读取模型和 URI', async () => {
  const voice = { model: 'model-a', uri: 'speech:reference', customName: 'reference' }
  post.mockResolvedValue({ data: voice })
  const result = await uploadSiliconFlowVoice(new File(['audio'], 'reference.wav'), 'model-a', 'reference', '对应文本')
  expect(result).toEqual(voice)
  expect(post.mock.calls[0][1].get('model')).toBe('model-a')
})
it('试听取消信号传到请求层，失败不转换为成功响应', async () => {
  const controller = new AbortController()
  post.mockRejectedValue(new Error('cancelled'))
  await expect(testTtsSpeech('测试', controller.signal)).rejects.toThrow('cancelled')
  expect(post).toHaveBeenCalledWith('/admin/tts/test-speech', { text: '测试' }, { signal: controller.signal })
})
