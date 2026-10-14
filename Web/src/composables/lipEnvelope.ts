/** 纯数值包络；时间单位为毫秒，30/60/120Hz 共用相同的响应速度。 */
export interface LipSyncConfig {
  noiseFloor: number
  gain: number
  curve: number
  attackMs: number
  releaseMs: number
  silenceMs: number
}

export const defaultLipSyncConfig: LipSyncConfig = {
  noiseFloor: 0.006, gain: 6, curve: 0.75,
  attackMs: 30, releaseMs: 45, silenceMs: 25,
}

export function createLipEnvelope(config: LipSyncConfig) {
  let value = 0
  let peak = 0
  let gate = false
  let quietMs = 0
  return {
    reset() { value = 0; peak = 0; gate = false; quietMs = 0 },
    sample(rms: number, elapsedMs: number) {
      const dt = Math.max(0, Math.min(100, elapsedMs))
      const floor = Math.max(0.001, config.noiseFloor)
      const level = Number.isFinite(rms) ? Math.max(0, rms) : 0
      // 滞回防止阈值附近开合抖动；持续低电平不能被归一化抬成讲话。
      if (level >= floor * 1.6) { gate = true; quietMs = 0 }
      else if (level < floor * 1.15) {
        quietMs += dt
        if (quietMs >= config.silenceMs) gate = false
      } else if (!gate) quietMs = 0
      peak = Math.max(level, peak * Math.exp(-dt / 1500))
      const gain = Math.max(2, Math.min(8, config.gain))
      const reference = Math.max(1 / gain, peak * 1.3)
      const target = gate ? Math.min(0.92, Math.pow(Math.max(0, (level - floor) / reference), Math.max(0.1, config.curve))) : 0
      const response = target > value ? config.attackMs : config.releaseMs
      // 响应时间指约 95% 的过渡完成时间，而非指数时间常数。
      value += (target - value) * (1 - Math.exp(-3 * dt / Math.max(1, response)))
      if (value < 0.001) value = 0
      return value
    },
  }
}
