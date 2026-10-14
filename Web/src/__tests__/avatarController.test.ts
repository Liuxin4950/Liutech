import { describe, expect, it } from 'vitest'
import { createAvatarController, deriveAvatarState, type AvatarExpression } from '@/composables/avatarController'
import { createLipEnvelope, defaultLipSyncConfig } from '@/composables/lipEnvelope'

function modelFixture() {
  let time = 0
  let update = () => {}
  const ids = ['ParamMouthOpenY', 'ParamMouthForm', 'Param17', 'ParamAngleZ', 'ParamBrowLY', 'ParamEyeROpen']
  const defaults = new Float32Array([0, 0, 0, 0, 0, 1])
  const values = defaults.slice()
  const core = {
    getModel: () => ({ parameters: { ids, defaultValues: defaults, minimumValues: new Float32Array([0, -1, 0, -30, -1, 0]), maximumValues: new Float32Array([1, 1, 1, 30, 1, 1]) } }),
    getParameterValueByIndex: (i: number) => values[i]!,
    setParameterValueByIndex: (i: number, value: number) => { values[i] = value },
  }
  const internal = { coreModel: core, focusController: { x: 0, y: 0, targetX: 0, targetY: 0 },
    on: (_: string, callback: () => void) => { update = callback },
    off: () => { update = () => {} },
  }
  const avatar = createAvatarController(internal, () => time)
  const resources = new Map<string, AvatarExpression>([
    ['开心1', { Parameters: [{ Id: 'ParamMouthForm', Value: 0.5 }, { Id: 'missing', Value: 30 }] }],
    ['思考', { Parameters: [{ Id: 'Param17', Value: 30 }] }],
    ['挑眉', { Parameters: [{ Id: 'ParamBrowLY', Value: 1 }] }],
  ])
  avatar.setExpressions(resources)
  return { avatar, values, internal, frame: (elapsed: number) => {
    time += elapsed
    values.set(defaults) // Cubism 每次绘制后 loadParameters，不保存控制器叠加值。
    update()
  } }
}

describe('统一角色参数', () => {
  it('只在模型帧写入；表情强度和开口独立，多帧不累积', () => {
    const { avatar, values, frame } = modelFixture()
    avatar.applyCue({ expression: 'happy', intensity: 0.4, skipResetTimer: true })
    avatar.setState('speaking')
    avatar.setMouth(0.55)
    expect(values[0]).toBe(0)
    frame(250)
    expect(values[0]).toBeCloseTo(0.55)
    expect(values[1]).toBeCloseTo(0.2)
    for (let i = 0; i < 100; i++) frame(16)
    expect(values[1]).toBeCloseTo(0.2)
    avatar.applyCue({ expression: 'happy', intensity: 1 })
    frame(250)
    expect(values[1]).toBeCloseTo(0.3)
  })
  it('思考使用原生范围内的手部姿态；说话和结束平滑撤销，取消下一帧闭嘴', () => {
    const { avatar, values, frame } = modelFixture()
    avatar.setState('thinking')
    frame(250)
    expect(values[2]).toBe(1)
    expect(values[3]).toBe(-3)
    avatar.setState('speaking')
    avatar.setMouth(0.6)
    frame(350)
    expect(values[2]).toBe(0)
    avatar.reset()
    frame(16)
    expect(values[0]).toBe(0)
  })
  it('过期表情不因资源晚到重演；calm 不触发特殊效果，同名可以再次生效', () => {
    const { avatar, values, frame } = modelFixture()
    avatar.applyCue({ expression: 'happy', durationMs: 1600 })
    frame(250)
    expect(values[1]).toBeGreaterThan(0)
    frame(1700)
    frame(350)
    expect(values[1]).toBe(0)
    avatar.applyCue({ expression: 'happy' })
    frame(250)
    expect(values[1]).toBeGreaterThan(0)
    avatar.applyCue({ expression: 'calm' })
    frame(350)
    expect(values[1]).toBe(0)
  })
  it('点击有冷却，交流中不抢 cue；减少动态时停止额外姿态和注视', () => {
    const { avatar, values, frame, internal } = modelFixture()
    avatar.tap()
    frame(250)
    expect(values[1]).toBeGreaterThan(0)
    avatar.tap()
    frame(250)
    expect(values[4]).toBe(0)
    avatar.point(1, 1)
    frame(16)
    expect(internal.focusController.targetX).toBe(0.18)
    frame(1600)
    expect(internal.focusController.targetX).toBe(0)
    avatar.setReduced(true)
    avatar.setState('thinking')
    avatar.point(1, 1)
    frame(250)
    expect(values[2]).toBe(0)
    expect(values[3]).toBe(0)
    expect(internal.focusController.targetX).toBe(0)
  })
  it('隐藏和销毁拒绝迟到 cue/参数更新', () => {
    const { avatar, values, frame } = modelFixture()
    avatar.applyCue({ expression: 'happy' })
    avatar.setEnabled(false)
    avatar.applyCue({ expression: 'happy' })
    avatar.setMouth(0.7)
    avatar.setEnabled(true)
    frame(350)
    expect(values[0]).toBe(0)
    expect(values[1]).toBe(0)
    avatar.destroy()
    avatar.applyCue({ expression: 'happy' })
    frame(350)
    expect(values[1]).toBe(0)
  })
})

describe('口型包络', () => {
  function run(rms: number, hz: number) {
    const envelope = createLipEnvelope({ ...defaultLipSyncConfig })
    let open = 0
    for (let t = 0; t < 300; t += 1000 / hz) open = envelope.sample(rms, 1000 / hz)
    return { envelope, open }
  }
  it('噪声不被放大；轻声/普通/响声都有层次且不持续满嘴', () => {
    expect(run(0.005, 60).open).toBe(0)
    const light = run(0.02, 60).open
    const normal = run(0.08, 60).open
    const loud = run(0.4, 60).open
    expect(light).toBeGreaterThan(0.1)
    expect(normal).toBeGreaterThan(light)
    expect(loud).toBeGreaterThan(normal)
    expect(loud).toBeLessThan(0.92)
  })
  it.each([30, 60, 120])('%sHz 下静音 100ms 内闭嘴，重置不残留峰值', hz => {
    const { envelope } = run(0.4, hz)
    let closed = 1
    for (let t = 0; t < 100; t += 1000 / hz) closed = envelope.sample(0, 1000 / hz)
    expect(closed).toBeLessThan(0.05)
    envelope.reset()
    expect(envelope.sample(0, 1000 / hz)).toBe(0)
  })
  it('刷新率改变不改变包络响应', () => {
    expect(run(0.08, 30).open).toBeCloseTo(run(0.08, 120).open, 3)
  })
})

it('角色状态使用真实识别与 playing，等待音频不假装讲话', () => {
  const input = { recognition: 'starting' as const, playback: 'idle', loading: false, awaiting: false, pending: 0 }
  expect(deriveAvatarState(input)).toBe('idle')
  expect(deriveAvatarState({ ...input, recognition: 'listening' })).toBe('listening')
  expect(deriveAvatarState({ ...input, recognition: 'listening', playback: 'playing' })).toBe('speaking')
  expect(deriveAvatarState({ ...input, awaiting: true })).toBe('preparing')
  expect(deriveAvatarState({ ...input, playback: 'buffering' })).toBe('buffering')
})
