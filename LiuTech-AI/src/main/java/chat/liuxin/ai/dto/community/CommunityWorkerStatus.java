package chat.liuxin.ai.dto.community;

import java.time.Instant;

/** 本实例执行事实与数据库租约、队列快照；数据库倒计时始终由 SQL 时钟计算。 */
public record CommunityWorkerStatus(
    String instanceId, Instant observedAt, Instant startedAt,
    Instant lastHeartbeatAt, long heartbeatAgeMs, boolean schedulerAlive,
    long pollIntervalMs, long initialDelayMs,
    Instant lastPollStartedAt, Instant lastPollFinishedAt, Instant lastSuccessfulPollAt,
    boolean busy, String state, String phase, Instant phaseStartedAt, long phaseElapsedMs,
    Instant lastProgressAt, long progressAgeMs, long progressWarningMs,
    long modelTimeoutMs, Long phaseTimeoutMs, Instant phaseDeadlineAt,
    String previousPhase, Instant previousPhaseStartedAt, Instant previousPhaseFinishedAt,
    String currentTaskId, Long currentBotId, Long currentPostId, Long currentEventId,
    Instant currentTaskStartedAt,
    String currentTaskStatus, String currentModel,
    boolean leaseOwned, String leaseState, Long leaseRemainingSeconds,
    String blockReason, String lastError, Instant lastErrorAt, int consecutivePollFailures,
    String lastTaskId, String lastTaskOutcome, Instant lastTaskStartedAt, Instant lastTaskFinishedAt,
    Queue queue, Database database) {
    public record Queue(long readyCount, long delayedCount, long leasedCount, long failedCount,
                        Long nextDueSeconds, long oldestReadySeconds) {}
    public record Database(boolean available, String now, String sessionTimeZone, String systemTimeZone) {}
}
