/** SSE cue 与本地交互共用的角色契约；不向服务端增加必填字段。 */
export interface AvatarCue {
  expression?: string
  motion?: string | null
  intensity?: number
  durationMs?: number
  text?: string
  skipResetTimer?: boolean
  immediate?: boolean
}
export type VoiceRecognitionState = 'idle' | 'starting' | 'listening'
export type AvatarState = 'idle' | 'listening' | 'thinking' | 'preparing' | 'speaking' | 'buffering' | 'music'

export function deriveAvatarState(input: {
  recognition?: VoiceRecognitionState
  playback: string
  loading: boolean
  awaiting: boolean
  pending: number
}): AvatarState {
  if (input.playback === 'playing') return 'speaking'
  if (input.recognition === 'listening') return 'listening'
  if (input.playback === 'buffering') return 'buffering'
  if (input.playback === 'preparing' || input.awaiting || input.pending > 0) return 'preparing'
  if (input.loading) return 'thinking'
  return 'idle'
}

interface ExpressionParameter { Id: string; Value: number; Blend?: string }
export interface AvatarExpression { Parameters: ExpressionParameter[] }
const expressionNames: Record<string, string | undefined> = {
  happy: '开心1', sad: '伤心1', angry: '生气', thinking: '思考',
  shy: '害羞', surprised: '挑眉', confused: '半眼',
}
const safeNames = new Set(Object.values(expressionNames))
const expressionCache = new Map<string, Promise<AvatarExpression>>()

/** 只加载温和表情；失败可重试。加载结果仅补充资源，不携带旧 cue。 */
export async function loadAvatarExpressions(definitions: { Name: string; File: string }[], base: string) {
  const result = new Map<string, AvatarExpression>()
  await Promise.all(definitions.filter(d => safeNames.has(d.Name)).map(async d => {
    const url = new URL(d.File, new URL(base, window.location.href)).href
    let promise = expressionCache.get(url)
    if (!promise) {
      promise = fetch(url).then(response => {
        if (!response.ok) throw new Error('表情资源未加载')
        return response.json() as Promise<AvatarExpression>
      })
      expressionCache.set(url, promise)
      void promise.catch(() => expressionCache.delete(url))
    }
    try { result.set(d.Name, await promise) }
    catch (error) { console.warn('[Live2D] 温和表情暂不可用', d.Name, error) }
  }))
  return result
}

// Cubism 的第三方运行时没有随全局脚本提供 TS 声明，只在此边界使用结构类型。
interface CoreModel {
  getModel(): { parameters: { ids: string[]; minimumValues: Float32Array; maximumValues: Float32Array; defaultValues: Float32Array } }
  getParameterValueByIndex(index: number): number
  setParameterValueByIndex(index: number, value: number): void
}
interface InternalModel {
  coreModel: CoreModel
  on(event: string, callback: () => void): void
  off(event: string, callback: () => void): void
  focusController: { x: number; y: number; targetX: number; targetY: number }
}

/** 一模型一个控制器。只在渲染前写参数，所有叠加在 loadParameters 后自然撤销。 */
export function createAvatarController(internal: InternalModel, now = () => performance.now()) {
  const core = internal.coreModel
  const parameters = core.getModel().parameters
  const index = new Map(parameters.ids.map((id, i) => [id, i]))
  const current = new Float32Array(parameters.ids.length)
  const from = new Float32Array(parameters.ids.length)
  const target = new Float32Array(parameters.ids.length)
  const owned = new Set<number>()
  const headIndices = ['ParamAngleX', 'ParamAngleY', 'ParamAngleZ', 'ParamBodyAngleX'].map(id => index.get(id)).filter((i): i is number => i !== undefined)
  const mouthModifiers = new Set([index.get('Param25'), index.get('ParamMouthForm3')])
  let expressions = new Map<string, AvatarExpression>()
  let state: AvatarState = 'idle'
  let cue: AvatarCue = {}
  let cueUntil = 0
  let transitionAt = now()
  let transitionMs = 350
  let mouth = 0
  let enabled = true
  let reduced = false
  let destroyed = false
  let pointerX = 0
  let pointerY = 0
  let pointerUntil = 0
  let lastTap = -Infinity
  let tapCount = 0
  let lastMotion = -Infinity
  const clamp = (i: number, value: number) => Math.max(parameters.minimumValues[i]!, Math.min(parameters.maximumValues[i]!, value))
  const offset = (id: string, value: number) => {
    const i = index.get(id)
    if (i === undefined) return
    owned.add(i)
    target[i] = clamp(i, parameters.defaultValues[i]! + value) - parameters.defaultValues[i]!
  }
  const select = () => {
    from.set(current)
    target.fill(0)
    const raw = cue.expression || 'neutral'
    const thinking = state === 'thinking' || state === 'preparing'
    const name = expressionNames[raw] ?? (safeNames.has(raw) ? raw : undefined)
    const chosen = name ?? (thinking && (raw === 'neutral' || raw === 'calm') ? '思考' : undefined)
    const intensity = Math.max(0, Math.min(0.6, Number.isFinite(cue.intensity) ? cue.intensity! : 0.45))
    for (const p of expressions.get(chosen || '')?.Parameters ?? []) {
      const i = index.get(p.Id)
      if (i === undefined || p.Id === 'ParamMouthOpenY' || !Number.isFinite(p.Value)) continue
      const base = parameters.defaultValues[i]!
      const absolute = p.Blend === 'Multiply' ? base * p.Value : p.Blend === 'Overwrite' ? p.Value : base + p.Value
      owned.add(i)
      target[i] = (clamp(i, absolute) - base) * intensity
    }
    // 手部开关是离散姿态，不按强度留在两种姿态之间。
    if (thinking && !reduced) offset('Param17', parameters.maximumValues[index.get('Param17') ?? -1] ?? 0)
    else offset('Param17', 0)
    if (!reduced) {
      if (thinking) offset('ParamAngleZ', -3)
      if (state === 'listening') { offset('ParamAngleY', -2); offset('ParamBrowLY', 0.08) }
    }
    transitionAt = now()
    transitionMs = chosen ? 250 : 350
  }
  const applyCue = (value: AvatarCue) => {
    if (destroyed || !enabled) return
    cue = { ...value }
    cueUntil = value.skipResetTimer ? Infinity : now() + Math.max(1600, Math.min(6000, value.durationMs || 2600))
    select()
  }
  const update = () => {
    if (!enabled || destroyed) return
    const time = now()
    if (cueUntil && time >= cueUntil) { cue = {}; cueUntil = 0; select() }
    const progress = Math.max(0, Math.min(1, (time - transitionAt) / transitionMs))
    const blend = progress * progress * (3 - 2 * progress)
    for (const i of owned) {
      current[i] = from[i]! + (target[i]! - from[i]!) * blend
      let delta = current[i]!
      // 脸部状态不应遮住有效音频的开合。
      if ((state === 'speaking' || state === 'music') && mouthModifiers.has(i)) delta = 0
      core.setParameterValueByIndex(i, clamp(i, core.getParameterValueByIndex(i) + delta))
    }
    // 保留库原生呼吸/眨眼，限制大幅转头；不另开自然运动循环。
    for (const i of headIndices) {
      core.setParameterValueByIndex(i, clamp(i, Math.max(reduced ? -1 : -6, Math.min(reduced ? 1 : 6, core.getParameterValueByIndex(i)))))
    }
    const open = index.get('ParamMouthOpenY')
    if (open !== undefined) core.setParameterValueByIndex(open, clamp(open, mouth))
    const focus = internal.focusController
    const follow = !reduced && time < pointerUntil && state !== 'listening' && state !== 'thinking' && state !== 'preparing'
    focus.targetX = follow ? pointerX * (state === 'speaking' ? 0.4 : 1) : 0
    focus.targetY = follow ? pointerY * (state === 'speaking' ? 0.4 : 1) : 0
  }
  internal.on('beforeModelUpdate', update)
  return {
    applyCue,
    setExpressions(value: Map<string, AvatarExpression>) { if (!destroyed) { expressions = value; select() } },
    setMouth(value: number) { mouth = enabled && Number.isFinite(value) ? Math.max(0, Math.min(0.92, value)) : 0 },
    setState(value: AvatarState) { if (state !== value) { state = value; select() } },
    setReduced(value: boolean) { reduced = value; if (value) pointerUntil = 0; select() },
    point(x: number, y: number) { pointerX = Math.max(-0.18, Math.min(0.18, x)); pointerY = Math.max(-0.18, Math.min(0.18, y)); pointerUntil = now() + 1500 },
    clearPointer() { pointerUntil = 0 },
    tap() {
      if (!enabled || now() - lastTap < 3000) return
      lastTap = now()
      // 交流中的 cue 优先，点击只负责打开聊天。
      if (state !== 'idle') return
      applyCue({ expression: tapCount++ % 2 ? 'surprised' : 'happy', intensity: 0.35, durationMs: 1800 })
    },
    reset() { cue = {}; cueUntil = 0; mouth = 0; pointerUntil = 0; state = 'idle'; select() },
    setEnabled(value: boolean) {
      enabled = value
      if (!value) {
        mouth = 0; cue = {}; cueUntil = 0; pointerUntil = 0
        current.fill(0); from.fill(0); target.fill(0)
        internal.focusController.x = internal.focusController.y = 0
        internal.focusController.targetX = internal.focusController.targetY = 0
      } else select()
    },
    playMotion(name: string, settings: Record<string, unknown> | undefined, play: (name: string) => void) {
      if (enabled && settings?.[name] && now() - lastMotion >= 3000) { lastMotion = now(); play(name) }
    },
    destroy() { destroyed = true; mouth = 0; internal.off('beforeModelUpdate', update) },
  }
}
