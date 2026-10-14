import { effectScope, nextTick, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useVoiceRecognition } from '@/composables/useVoiceRecognition'

class Recognition {
  static instances: Recognition[] = []
  continuous = false
  interimResults = false
  lang = ''
  onstart: ((event: Event) => void) | null = null
  onend: ((event: Event) => void) | null = null
  onerror: ((event: any) => void) | null = null
  onresult: ((event: any) => void) | null = null
  onspeechend: ((event: Event) => void) | null = null
  start = vi.fn()
  stop = vi.fn()
  abort = vi.fn()
  constructor() { Recognition.instances.push(this) }
  result(text: string, final = true) {
    this.onresult?.({ resultIndex: 0, results: [Object.assign([{ transcript: text }], { isFinal: final })] })
  }
  end() { return this.onend?.(new Event('end')) }
}
const cleanups: (() => void)[] = []
function setup() {
  const input = ref('')
  const busy = ref(false)
  const beforeListen = vi.fn(() => { busy.value = false })
  const onSubmit = vi.fn(() => { input.value = ''; busy.value = true })
  const scope = effectScope()
  const voice = scope.run(() => useVoiceRecognition(input, {
    canListen: () => !busy.value, beforeListen, onSubmit,
  }))!
  voice.initVoiceRecognition()
  cleanups.push(() => { voice.cleanupVoiceRecognition(); scope.stop() })
  return { input, busy, beforeListen, onSubmit, voice }
}
beforeEach(() => {
  vi.useFakeTimers()
  Recognition.instances = []
  window.SpeechRecognition = Recognition
})
afterEach(() => {
  cleanups.splice(0).forEach(clean => clean())
  delete window.SpeechRecognition
  vi.useRealTimers()
})
it('点击接话先打断，等待权限期间重复点击不创建第二个识别器；手动输入不自动发送', async () => {
  const { voice, beforeListen, onSubmit, input } = setup()
  voice.startVoiceRecognition()
  voice.startVoiceRecognition()
  expect(beforeListen).toHaveBeenCalledTimes(1)
  expect(Recognition.instances).toHaveLength(1)
  expect(voice.voiceListening.value).toBe(true)
  Recognition.instances[0]!.result('你好')
  await Recognition.instances[0]!.end()
  expect(input.value).toBe('你好')
  expect(onSubmit).not.toHaveBeenCalled()
})
it('免提只发送最终识别，等整轮回复含音频播完后重新监听', async () => {
  const { voice, busy, onSubmit } = setup()
  voice.toggleHandsFree()
  const first = Recognition.instances[0]!
  expect(first.continuous).toBe(false)
  first.result('中间结果', false)
  expect(onSubmit).not.toHaveBeenCalled()
  first.onspeechend?.(new Event('speechend'))
  expect(first.stop).toHaveBeenCalledOnce()
  first.result('最终结果')
  await first.end()
  expect(onSubmit).toHaveBeenCalledOnce()
  await vi.advanceTimersByTimeAsync(1000)
  expect(Recognition.instances).toHaveLength(1)
  busy.value = false
  await nextTick()
  await vi.advanceTimersByTimeAsync(350)
  expect(Recognition.instances).toHaveLength(2)
  await first.end()
  expect(onSubmit).toHaveBeenCalledOnce()
})
it('退出或切会话后丢弃迟到结果/end，并取消自动重新监听', async () => {
  const { voice, input, onSubmit } = setup()
  voice.toggleHandsFree()
  const previous = Recognition.instances[0]!
  voice.disableHandsFree()
  previous.result('迟到内容')
  await previous.end()
  await vi.advanceTimersByTimeAsync(1000)
  expect(input.value).toBe('')
  expect(onSubmit).not.toHaveBeenCalled()
  expect(previous.abort).toHaveBeenCalledOnce()
  expect(Recognition.instances).toHaveLength(1)
})
it.each(['not-allowed', 'network', 'no-speech'])('识别错误 %s 停止免提并保留草稿，不循环申请权限', async error => {
  const { voice, input, onSubmit } = setup()
  voice.toggleHandsFree()
  const current = Recognition.instances[0]!
  current.result('保留草稿')
  current.onerror?.({ error })
  await current.end()
  await vi.advanceTimersByTimeAsync(2000)
  expect(voice.handsFree.value).toBe(false)
  expect(voice.voiceError.value).not.toBe('')
  expect(input.value).toBe('保留草稿')
  expect(onSubmit).not.toHaveBeenCalled()
  expect(Recognition.instances).toHaveLength(1)
})
it('没有最终文本时不发送已有草稿，也不无限重启', async () => {
  const { voice, input, onSubmit } = setup()
  input.value = '尚未发送的草稿'
  voice.toggleHandsFree()
  await Recognition.instances[0]!.end()
  expect(voice.handsFree.value).toBe(false)
  expect(onSubmit).not.toHaveBeenCalled()
})
it('销毁时取消待启动计时，后续音频状态变化不能再开麦', async () => {
  const { voice, busy } = setup()
  voice.toggleHandsFree()
  Recognition.instances[0]!.result('你好')
  await Recognition.instances[0]!.end()
  busy.value = false
  await nextTick()
  voice.cleanupVoiceRecognition()
  await vi.advanceTimersByTimeAsync(1000)
  expect(Recognition.instances).toHaveLength(1)
})
