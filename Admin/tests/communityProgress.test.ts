import { describe, expect, it } from 'vitest'
import { queueCountdown, workerObservation } from '@/utils/communityProgress'
import type { CommunityWorkerStatus } from '@/services/community'

const snapshot = (changes: Partial<CommunityWorkerStatus> = {}): CommunityWorkerStatus => ({
  instanceId: 'test-instance', state: 'RUNNING', phase: 'MODEL_GENERATION', busy: true, schedulerAlive: true,
  pollIntervalMs: 5000, observedAt: '2026-10-09T12:00:00Z', lastHeartbeatAt: '2026-10-09T11:59:59Z', phaseElapsedMs: 30000,
  ...changes,
})
describe('社区执行事实与倒计时', () => {
  it('客户端日期相差数天也只累加响应后经过的时间', () => {
    const receivedAt = Date.UTC(2026, 10, 1)
    const state = workerObservation(snapshot(), receivedAt, receivedAt + 4000)
    expect(state.online).toBe(true)
    expect(state.phaseElapsedSeconds).toBe(34)
    expect(state.heartbeatAgeSeconds).toBe(5)
  })
  it('服务返回健康但调度心跳停止时，不能显示执行器正常', () => {
    expect(workerObservation(snapshot({ schedulerAlive: false }), 1000, 2000).online).toBe(false)
    expect(workerObservation(snapshot({ state: 'STALE' }), 1000, 2000).online).toBe(false)
    expect(workerObservation(snapshot({ state: 'ERROR' }), 1000, 2000).online).toBe(false)
  })
  it('旧的执行中状态在查询失败或超过刷新阈值后不可冒充当前状态', () => {
    expect(workerObservation(snapshot(), 1000, 25000).fresh).toBe(false)
    expect(workerObservation(snapshot(), 1000, 1100, 'AI 服务不可用').online).toBe(false)
  })
  it('没有执行器数据不会把空队列解释成正常运行', () => {
    expect(workerObservation(undefined, 0, 3000).online).toBe(false)
  })
  it('服务端直接提供心跳年龄时不重新解释其日期格式', () => {
    const state = workerObservation(snapshot({ heartbeatAgeMs: 2500, lastHeartbeatAt: 'invalid' }), 1000, 2000)
    expect(state.heartbeatAgeSeconds).toBe(3)
  })
  it('跨过到期边界后保留负数，以显示已经等待的时间', () => {
    expect(queueCountdown(20, 100000, 109000)).toBe(11)
    expect(queueCountdown(20, 100000, 125000)).toBe(-5)
  })
  it('未知到期或租约时间不能归为已到期', () => {
    expect(queueCountdown(undefined, 1000, 5000)).toBeUndefined()
    expect(queueCountdown(null, 1000, 5000)).toBeUndefined()
  })
  it('客户端时钟短暂回拨不增加等待时间或产生负阶段耗时', () => {
    expect(queueCountdown(20, 1000, 0)).toBe(20)
    expect(workerObservation(snapshot(), 1000, 0).phaseElapsedSeconds).toBe(30)
  })
})
