package chat.liuxin.ai.service.community;

import chat.liuxin.ai.dto.community.CommunityTask;
import chat.liuxin.ai.mapper.CommunityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunityWorkerMonitorTest {
    final CommunityMapper mapper=mock(CommunityMapper.class);
    final MutableClock clock=new MutableClock();
    final CommunityWorkerMonitor monitor=new CommunityWorkerMonitor(mapper,5000,15000,120000,60000,clock);
    @BeforeEach void database() {
        doReturn(Map.of("databaseNow","2026-10-09 12:00:00","sessionTimeZone","SYSTEM","systemTimeZone","UTC","leaseOccupied",0)).when(mapper).workerStatus();
        doReturn(Map.of("readyCount",0,"delayedCount",0,"leasedCount",0,"failedCount",0)).when(mapper).queueStatus();
    }
    CommunityTask task(String id) {
        var task=new CommunityTask();task.setId(id);task.setBotId(1L);task.setPostId(2L);task.setEventId(3L);task.setStatus("RUNNING");return task;
    }
    @Test void applicationStartupDoesNotInventAWorkerHeartbeatAndMissingSchedulingBecomesStale() {
        assertEquals("STARTING",monitor.status().state());assertFalse(monitor.status().schedulerAlive());
        assertNull(monitor.status().lastSuccessfulPollAt());
        clock.advance(31000);
        assertEquals("STALE",monitor.status().state());
    }
    @Test void longModelWorkWithFreshHeartbeatUsesTheRealTimeoutRatherThanAnArbitraryDuration() {
        monitor.heartbeat();monitor.pollStarted();monitor.acquiredLease("ours");
        when(mapper.workerStatus()).thenReturn(Map.of("leaseToken","ours","leaseOccupied",1,"leaseRemainingSeconds",500));
        monitor.taskStarted(task("current"));monitor.model("current","configured-model");
        clock.advance(100000);monitor.heartbeat();
        var running=monitor.status();
        assertEquals("RUNNING",running.state());assertEquals("MODEL_GENERATION",running.phase());
        assertEquals("current",running.currentTaskId());assertEquals("configured-model",running.currentModel());
        assertEquals(100000,running.phaseElapsedMs());assertEquals(120000,running.phaseTimeoutMs());
        assertEquals(clock.instant().plusSeconds(20),running.phaseDeadlineAt());
        assertTrue(running.schedulerAlive());assertTrue(running.leaseOwned());
        clock.advance(21000);monitor.heartbeat();
        assertEquals("STALE",monitor.status().state());
        assertTrue(monitor.status().schedulerAlive());
    }
    @Test void cancellationAndCompletionRejectLateStageWritesWithoutChangingQueueState() {
        monitor.heartbeat();monitor.pollStarted();monitor.taskStarted(task("current"));monitor.model("current","model");
        monitor.cancelled("current");monitor.taskPhase("current","PUBLISHING");monitor.model("current","late-model");
        assertEquals("CANCELLING",monitor.status().state());assertEquals("CANCELLING",monitor.status().phase());
        monitor.taskFinished("current","SUCCEEDED");monitor.pollFinished();
        monitor.taskPhase("current","MODEL_GENERATION");
        assertNull(monitor.status().currentTaskId());assertEquals("CANCELLED",monitor.status().lastTaskOutcome());
        assertEquals("IDLE",monitor.status().phase());
        verify(mapper,never()).decide(anyString(),anyString(),anyString());
    }
    @Test void onlyDatabaseCountdownsDescribeDelayedWorkAndOtherInstancesAreExplicit() {
        monitor.heartbeat();
        when(mapper.queueStatus()).thenReturn(Map.of("readyCount",0,"delayedCount",7,"leasedCount",0,"failedCount",0,"nextDueSeconds",28880));
        assertEquals("WAITING_TASK",monitor.status().state());assertEquals(28880,monitor.status().queue().nextDueSeconds());
        when(mapper.workerStatus()).thenReturn(Map.of("leaseToken","another-instance","leaseOccupied",1,"leaseRemainingSeconds",300));
        var status=monitor.status();
        assertEquals("WAITING_LEASE",status.state());assertEquals("OWNED_ELSEWHERE",status.leaseState());
        assertFalse(status.leaseOwned());assertNull(status.currentTaskId());
    }
    @Test void aFreshSchedulerHeartbeatDoesNotHideAStalledEventIntake() {
        monitor.pollStarted();monitor.event(3,1,2);
        clock.advance(3600000);monitor.heartbeat();
        var status=monitor.status();
        assertTrue(status.schedulerAlive());assertEquals("STALE",status.state());
        assertEquals("INGESTING_EVENTS",status.phase());assertTrue(status.blockReason().contains("进展未更新"));
        assertEquals(3600000,status.progressAgeMs());
    }
    @Test void realBatchProgressPreventsFalseWarningsAndPreviewCannotRefreshWorkerProgress() {
        monitor.pollStarted();monitor.taskStarted(task("current"));monitor.taskPhase("current","PREPARING_CONTEXT");
        clock.advance(30000);monitor.progress("current");clock.advance(30000);monitor.progress("current");
        clock.advance(30000);monitor.heartbeat();
        assertEquals(90000,monitor.status().phaseElapsedMs());assertEquals("RUNNING",monitor.status().state());
        clock.advance(61000);monitor.progress("another-preview");monitor.heartbeat();
        assertEquals("STALE",monitor.status().state());
    }
    @Test void cancellingKeepsTheOriginalInFlightModelDeadlineUntilTheModelActuallyReturns() {
        monitor.pollStarted();monitor.taskStarted(task("current"));monitor.model("current","model");
        var originalDeadline=clock.instant().plusMillis(120000);
        clock.advance(90000);monitor.cancelled("current");monitor.heartbeat();
        assertEquals(originalDeadline,monitor.status().phaseDeadlineAt());assertEquals("CANCELLING",monitor.status().state());
        clock.advance(31000);monitor.heartbeat();
        assertEquals("STALE",monitor.status().state());assertTrue(monitor.status().blockReason().contains("在途模型"));
        monitor.modelFinished("current");
        assertNull(monitor.status().phaseDeadlineAt());assertEquals("CANCELLING",monitor.status().state());
    }
    @Test void databaseFailureAndPollErrorsCannotBePresentedAsAnIdleHealthyWorker() {
        monitor.heartbeat();
        when(mapper.workerStatus()).thenThrow(new RuntimeException("private diagnostic"));
        var status=monitor.status();assertEquals("ERROR",status.state());assertFalse(status.database().available());
        assertFalse(status.blockReason().contains("private diagnostic"));
        database();monitor.pollStarted();monitor.pollError("事件领取失败");monitor.pollFinished();
        assertEquals("ERROR",monitor.status().state());assertEquals(1,monitor.status().consecutivePollFailures());
        monitor.pollStarted();monitor.pollFinished();
        assertEquals(0,monitor.status().consecutivePollFailures());assertNotNull(monitor.status().lastSuccessfulPollAt());
    }
    @Test void lastTaskFactsRemainBoundToTheCompletedTaskAndShutdownRejectsLatePhases() {
        monitor.heartbeat();monitor.pollStarted();monitor.taskStarted(task("finished"));
        Instant firstStart=clock.instant();clock.advance(1000);monitor.taskFinished("finished","SUCCEEDED");
        monitor.taskStarted(task("new-current"));
        assertEquals("finished",monitor.status().lastTaskId());assertEquals(firstStart,monitor.status().lastTaskStartedAt());
        monitor.stopped();monitor.model("new-current","late-model");monitor.phase("PUBLISHING");
        assertEquals("STOPPED",monitor.status().phase());assertFalse(monitor.status().schedulerAlive());
    }
    static final class MutableClock extends Clock {
        Instant now=Instant.parse("2026-10-09T12:00:00Z");
        void advance(long ms) {now=now.plusMillis(ms);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
}
