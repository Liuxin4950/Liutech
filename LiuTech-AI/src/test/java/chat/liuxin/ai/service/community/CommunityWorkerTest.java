package chat.liuxin.ai.service.community;

import chat.liuxin.ai.common.client.BackendApiTransport;
import chat.liuxin.ai.dto.community.CommunityTask;
import chat.liuxin.ai.infra.config.AiChatProperties;
import chat.liuxin.ai.infra.exception.AIServiceException;
import chat.liuxin.ai.mapper.CommunityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityWorkerTest {
    private final ObjectMapper json=new ObjectMapper();
    private final BackendApiTransport transport=mock(BackendApiTransport.class);
    private final CommunityStore store=mock(CommunityStore.class);
    private final CommunityMapper mapper=mock(CommunityMapper.class);
    private final CommunityService service=mock(CommunityService.class);
    private final CommunityWorker worker=new CommunityWorker(transport,store,mapper,service,json,new AiChatProperties());
    @BeforeEach void activeTask() {
        when(mapper.attempt(anyString(),anyLong())).thenReturn(1);
        when(mapper.decide(anyString(),anyString(),anyString())).thenReturn(1);
        when(transport.internalGet(startsWith("/internal/community/events/")))
            .thenReturn(json.readTree("{\"eventType\":\"ARTICLE_PUBLISHED\"}"));
    }
    private CommunityTask decided() {
        var task=new CommunityTask();task.setId("00000000-0000-0000-0000-000000000001");task.setBotId(1L);task.setPostId(2L);
        task.setEventId(3L);task.setRootEventId("root");task.setAttempts(1);task.setFailures(0);task.setMemoryEpoch(0L);
        task.setDecisionJson("{\"modelRunId\":\"model-run\",\"decision\":\"COMMENT\",\"content\":\"一次评论\",\"contextVersion\":\"v1\",\"memoryEpoch\":0,\"participants\":[],\"sourceCommentIds\":[],\"inputTokens\":50,\"outputTokens\":20}");
        return task;
    }
    @Test void scheduledTickDoesNotBlockOrQueueMultipleWorkers() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(transport.internalPost(eq("/internal/community/events/claim"), any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(3, java.util.concurrent.TimeUnit.SECONDS);
            return json.readTree("[]");
        });
        try {
            worker.tick();
            assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS));
            worker.tick();
            verify(transport, times(1)).internalPost(eq("/internal/community/events/claim"), any());
        } finally { release.countDown(); worker.shutdown(); }
    }

    @Test void persistedDecisionIsRepublishedWithSameIdAfterLostResponseWithoutRegeneration() {
        var task=decided();
        when(transport.internalPost(eq("/internal/community/comments"),any())).thenThrow(new AIServiceException.ConnectionException("连接丢失"))
                .thenReturn(json.readTree("{\"commentId\":4,\"duplicate\":true}"));
        when(transport.internalPost(eq("/internal/community/visibility"),any())).thenReturn(json.readTree("{\"visiblePostIds\":[2],\"visibleCommentIds\":[4]}"));
        worker.execute(task);worker.execute(task);
        verifyNoInteractions(service);
        verify(transport,times(2)).internalPost(eq("/internal/community/comments"),argThat(body->task.getId().equals(((Map<?,?>)body).get("taskId"))));
        verify(mapper).retry(eq(task.getId()),anyString(),eq(false),eq(30));
        verify(mapper).finish(task.getId(),"SUCCEEDED",null);
        verify(mapper).updateRun(eq("model-run"),eq(task.getId()),eq(1L),eq("FAILED"),anyString(),anyString());
        verify(mapper).updateRun(eq("model-run"),eq(task.getId()),eq(1L),eq("SUCCEEDED"),argThat(saved -> json.readTree(saved).path("publishedCommentId").asLong()==4
                && json.readTree(saved).path("inputTokens").asLong()==50),isNull());
        verify(mapper,never()).run(anyString(),anyString(),anyLong(),anyLong(),anyString(),anyString(),any());
    }
    @Test void staleContextClearsDecisionForAChargedRegenerationAndOtherFailuresAreBounded() {
        var task=decided();
        when(transport.internalPost(eq("/internal/community/comments"),any())).thenThrow(new BackendApiTransport.InternalBusinessException(1701,"文章发生变化"));
        worker.execute(task);
        verify(mapper).retry(task.getId(),"文章发生变化",true,30);
        task.setFailures(2);worker.execute(task);
        verify(mapper).fail(task.getId(),"FAILED","文章发生变化",true);
        verifyNoInteractions(service);
    }
    @Test void memoryFailureAfterConfirmedPublicationDoesNotHideTheActualPublishedComment() {
        var task=decided();
        when(transport.internalPost(eq("/internal/community/comments"),any())).thenReturn(json.readTree("{\"commentId\":4,\"createdAt\":\"2026-10-06T00:00:00\"}"));
        when(transport.internalPost(eq("/internal/community/visibility"),any())).thenThrow(new AIServiceException.ConnectionException("来源核对暂时失败"));
        worker.execute(task);
        verify(mapper).updateRun(eq("model-run"),eq(task.getId()),eq(1L),eq("SUCCEEDED"),argThat(saved -> json.readTree(saved).path("publishedCommentId").asLong()==4),isNull());
        verify(mapper,never()).updateRun(anyString(),anyString(),anyLong(),eq("FAILED"),anyString(),anyString());
        verify(mapper).retry(eq(task.getId()),anyString(),eq(false),eq(30));
        assertEquals(4,json.readTree(task.getDecisionJson()).path("publishedCommentId").asLong());
        when(transport.internalPost(eq("/internal/community/visibility"),any())).thenReturn(json.readTree("{\"visiblePostIds\":[2],\"visibleCommentIds\":[4]}"));
        worker.execute(task);
        verify(transport,times(1)).internalPost(eq("/internal/community/comments"),any());
        verifyNoInteractions(service);
        verify(mapper).finish(task.getId(),"SUCCEEDED",null);
    }
    @Test void confirmationSurvivesRunAuditFailureAndResumesWithoutRepublishing() {
        var task=decided();
        when(transport.internalPost(eq("/internal/community/comments"),any())).thenReturn(json.readTree("{\"commentId\":4}"));
        when(mapper.updateRun(eq("model-run"),eq(task.getId()),eq(1L),eq("SUCCEEDED"),anyString(),isNull()))
                .thenThrow(new RuntimeException("audit unavailable")).thenReturn(1);
        when(transport.internalPost(eq("/internal/community/visibility"),any())).thenReturn(json.readTree("{\"visiblePostIds\":[2],\"visibleCommentIds\":[4]}"));
        worker.execute(task);worker.execute(task);
        verify(transport,times(1)).internalPost(eq("/internal/community/comments"),any());
        verify(mapper,never()).updateRun(anyString(),anyString(),anyLong(),eq("FAILED"),anyString(),anyString());
        verify(mapper).finish(task.getId(),"SUCCEEDED",null);
    }
    @Test void receiptPersistenceFailureRecoversTheOriginalPublicationWithoutFreshInference() {
        var task=decided();
        when(transport.internalPost(eq("/internal/community/comments"),any()))
                .thenReturn(json.readTree("{\"commentId\":4}"),json.readTree("{\"commentId\":4,\"duplicate\":true}"));
        when(mapper.decide(eq(task.getId()),anyString(),eq("v1")))
                .thenThrow(new RuntimeException("receipt persistence unavailable")).thenReturn(1);
        when(transport.internalPost(eq("/internal/community/visibility"),any()))
                .thenReturn(json.readTree("{\"visiblePostIds\":[2],\"visibleCommentIds\":[4]}"));
        worker.execute(task);
        assertFalse(json.readTree(task.getDecisionJson()).has("publishedCommentId"));
        worker.execute(task);
        verify(transport,times(2)).internalPost(eq("/internal/community/comments"),
                argThat(body -> task.getId().equals(((Map<?,?>)body).get("taskId"))));
        verifyNoInteractions(service);
        verify(mapper,never()).attempt(anyString(),anyLong());
        verify(mapper,never()).updateRun(anyString(),anyString(),anyLong(),eq("FAILED"),anyString(),anyString());
        verify(mapper).retry(eq(task.getId()),anyString(),eq(false),eq(30));
        verify(mapper).finish(task.getId(),"SUCCEEDED",null);
    }
    @Test void malformedCachedDecisionIsRegeneratedWithSameTaskAndNextAttempt() {
        var task=decided();task.setDecisionJson("{\"decision\":\"REPLY\",\"targetCommentId\":\"invalid\",\"content\":\"旧内容\",\"contextVersion\":\"v1\"}");
        when(service.generate(task.getId(),1,2,null,2,false,0)).thenReturn(Map.of("decision","SKIP","contextVersion","v2"));
        worker.execute(task);
        verify(service).generate(task.getId(),1,2,null,2,false,0);
        verify(mapper).attempt(task.getId(),0);
        verify(mapper).finish(task.getId(),"SKIPPED",null);
        verify(transport,never()).internalPost(eq("/internal/community/comments"),any());
    }
    @Test void withdrawnPostTerminatesWithoutRegenerationOrRetry() {
        var task=decided();task.setDecisionJson(null);
        when(store.epoch(1)).thenReturn(0L);
        when(service.generate(task.getId(),1,2,null,2,false,0)).thenThrow(new BackendApiTransport.InternalBusinessException(1101,"文章不存在"));
        worker.execute(task);
        verify(mapper).fail(task.getId(),"SKIPPED","文章不存在",false);
        verify(mapper,never()).retry(anyString(),anyString(),anyBoolean(),anyInt());
        verify(transport,never()).internalPost(eq("/internal/community/comments"),any());
    }
    @Test void regeneratingAnOldTaskKeepsItsOriginalEpochAfterMemoryClear() {
        var task=decided();task.setDecisionJson(null);task.setMemoryEpoch(5L);
        when(service.generate(task.getId(),1,2,null,2,false,5)).thenReturn(Map.of("decision","SKIP","contextVersion","v2"));
        worker.execute(task);
        verify(service).generate(task.getId(),1,2,null,2,false,5);
        verify(store,never()).epoch(anyLong());
        verify(mapper).attempt(task.getId(),5);
    }
    @Test void skipCompletesWithoutPublishingAndAttemptsIncrementBeforeEveryGeneration() {
        var task=decided();task.setDecisionJson(null);task.setAttempts(2);task.setMemoryEpoch(null);
        when(store.epoch(1)).thenReturn(9L);
        when(service.generate(task.getId(),1,2,null,3,false,9)).thenReturn(Map.of("decision","SKIP","contextVersion","v1"));
        worker.execute(task);
        verify(mapper).attempt(task.getId(),9L);
        verify(service).generate(task.getId(),1,2,null,3,false,9);
        verify(mapper).finish(task.getId(),"SKIPPED",null);
        verify(transport,never()).internalPost(eq("/internal/community/comments"),any());
    }
    @Test void cancelledTaskAndCancelledSourceDoNotSpendAnotherModelAttempt() {
        var task=decided();task.setDecisionJson(null);
        when(mapper.cancelled(task.getId())).thenReturn(true);
        worker.execute(task);
        verifyNoInteractions(service);
        verify(mapper,never()).attempt(anyString(),anyLong());
        when(mapper.cancelled(task.getId())).thenReturn(false);
        when(transport.internalGet("/internal/community/events/3")).thenReturn(json.readTree("{\"eventType\":\"CANCELLED\"}"));
        worker.execute(task);
        verify(store).cancel(task.getId());
        verifyNoInteractions(service);
        verify(mapper,never()).attempt(anyString(),anyLong());
    }
    @Test void cancellationDuringGenerationDiscardsTheResultWithoutPublishingOrRevivingTask() {
        var task=decided();task.setDecisionJson(null);
        when(service.generate(task.getId(),1,2,null,2,false,0)).thenReturn(Map.of("decision","COMMENT","content","late result","contextVersion","v1"));
        // 取消已经把状态改成 CANCELLED；条件更新不能重新写成 DECIDED。
        when(mapper.decide(eq(task.getId()),anyString(),eq("v1"))).thenReturn(0);
        worker.execute(task);
        verify(transport,never()).internalPost(eq("/internal/community/comments"),any());
        verify(mapper,never()).finish(anyString(),anyString(),any());
    }
    @Test void authorityCancellationFenceDoesNotEnterTheRetryLoop() {
        var task=decided();
        when(transport.internalPost(eq("/internal/community/comments"),any()))
            .thenThrow(new BackendApiTransport.InternalBusinessException(1706,"社区任务已取消"));
        worker.execute(task);
        verify(store).cancel(task.getId());
        verify(mapper,never()).retry(anyString(),anyString(),anyBoolean(),anyInt());
        verify(mapper,never()).fail(anyString(),anyString(),anyString(),anyBoolean());
        verify(mapper).updateRun(eq("model-run"),eq(task.getId()),eq(1L),eq("CANCELLED"),anyString(),anyString());
    }
}
