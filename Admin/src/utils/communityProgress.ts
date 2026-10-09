import type { CommunityWorkerStatus } from '@/services/community'

/** 查询时已确认的服务端事实，加上本次响应之后经过的时间；不依赖浏览器和服务器时钟对齐。 */
export function workerObservation(status: CommunityWorkerStatus | undefined, receivedAt: number, now: number, error = '') {
  const readAgeMs = Math.max(0, now - receivedAt)
  const fresh = !!status && !error && readAgeMs < Math.max(20000, (status.pollIntervalMs || 5000) * 3)
  const online = fresh && !!status.schedulerAlive && !['STOPPED', 'STALE', 'ERROR'].includes(status.state)
  const phaseElapsedSeconds = Math.floor((Math.max(0, status?.phaseElapsedMs || 0) + readAgeMs) / 1000)
  const heartbeatAge = status?.heartbeatAgeMs ?? (status?.lastHeartbeatAt
    ? new Date(status.observedAt).getTime() - new Date(status.lastHeartbeatAt).getTime() : undefined)
  const heartbeatAgeSeconds = heartbeatAge !== undefined && Number.isFinite(heartbeatAge)
    ? Math.floor((Math.max(0, heartbeatAge) + readAgeMs) / 1000) : undefined
  return { readAgeMs, fresh, online, phaseElapsedSeconds, heartbeatAgeSeconds }
}

export function queueCountdown(seconds: number | undefined | null, sampledAt: number, now: number) {
  return seconds === undefined || seconds === null ? undefined : seconds - Math.floor(Math.max(0, now - sampledAt) / 1000)
}
