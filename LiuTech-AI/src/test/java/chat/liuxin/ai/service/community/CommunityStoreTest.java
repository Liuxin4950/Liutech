package chat.liuxin.ai.service.community;

import chat.liuxin.ai.mapper.CommunityMapper;
import chat.liuxin.ai.dto.community.CommunityTask;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityStoreTest {
    @Test void onlyFailedTasksCanBeQueuedAndExistingDecisionIsPreserved() {
        var mapper=mock(CommunityMapper.class);var store=new CommunityStore(mapper,new ObjectMapper());
        var task=new CommunityTask();task.setId("task");task.setStatus("FAILED");task.setAttempts(3);
        String cached="{\"decision\":\"COMMENT\",\"content\":\"缓存评论\",\"contextVersion\":\"v1\"}";
        task.setDecisionJson(cached);task.setMemoryEpoch(1L);
        when(mapper.taskForRetry("task")).thenReturn(task);when(mapper.retryFailed("task",false)).thenReturn(1);
        var retry=store.retryFailed("task");assertTrue(retry.queued());assertEquals("publish",retry.retryKind());
        assertEquals(3,task.getAttempts());assertEquals(cached,task.getDecisionJson());assertEquals(1L,task.getMemoryEpoch());
        task.setStatus("SUCCEEDED");assertFalse(store.retryFailed("task").queued());
        task.setStatus("RUNNING");assertFalse(store.retryFailed("task").queued());
        verify(mapper,times(1)).retryFailed("task",false);
    }
    @Test void invalidCacheQueuesFreshGenerationAndConfirmedPublicationOnlyQueuesPostprocessing() {
        var mapper=mock(CommunityMapper.class);var store=new CommunityStore(mapper,new ObjectMapper());
        var task=new CommunityTask();task.setId("task");task.setStatus("FAILED");task.setDecisionJson("not-json");
        when(mapper.taskForRetry("task")).thenReturn(task);when(mapper.retryFailed("task",true)).thenReturn(1);
        assertEquals("regenerate",store.retryFailed("task").retryKind());
        verify(mapper).retryFailed("task",true);
        task.setDecisionJson("{\"publishedCommentId\":9}");when(mapper.retryFailed("task",false)).thenReturn(1);
        var retry=store.retryFailed("task");assertTrue(retry.queued());assertEquals("postprocess",retry.retryKind());
        assertEquals(9,task.getPublishedCommentId());
        assertTrue(retry.reason().contains("评论已发表"));
        verify(mapper).retryFailed("task",false);
    }
    @Test void savedSilenceOnlyRestoresCompletionRatherThanPublishingOrGenerating() {
        var mapper=mock(CommunityMapper.class);var store=new CommunityStore(mapper,new ObjectMapper());
        var task=new CommunityTask();task.setId("task");task.setStatus("FAILED");task.setDecisionJson("{\"decision\":\"SKIP\"}");
        when(mapper.taskForRetry("task")).thenReturn(task);when(mapper.retryFailed("task",false)).thenReturn(1);
        var retry=store.retryFailed("task");
        assertEquals("complete",retry.retryKind());assertTrue(retry.reason().contains("不调用模型或发表评论"));
        verify(mapper).retryFailed("task",false);
    }
    @Test void successfulTasksExposeReceiptWithoutARecoveryAction() {
        var mapper=mock(CommunityMapper.class);var store=new CommunityStore(mapper,new ObjectMapper());
        var task=new CommunityTask();task.setStatus("SUCCEEDED");task.setDecisionJson("{\"publishedCommentId\":9}");
        store.describeRetry(task);
        assertEquals(9,task.getPublishedCommentId());assertNull(task.getRetryKind());assertNull(task.getRetryReason());
    }
    @Test void failedLeaseDoesNotDispatchAnUnownedTask() {
        var mapper=mock(CommunityMapper.class);var store=new CommunityStore(mapper,new ObjectMapper());
        var task=new CommunityTask();task.setId("task");when(mapper.next()).thenReturn(task);
        when(mapper.lease("task",600)).thenReturn(0);
        assertNull(store.claim(600));
    }
    @Test void aSecondManualRetryCannotRequeueATaskAlreadyRecoveredByTheFirstRequest() {
        var mapper=mock(CommunityMapper.class);var store=new CommunityStore(mapper,new ObjectMapper());
        var failed=new CommunityTask();failed.setId("task");failed.setStatus("FAILED");
        var queued=new CommunityTask();queued.setId("task");queued.setStatus("READY");
        when(mapper.taskForRetry("task")).thenReturn(failed,queued);
        when(mapper.retryFailed("task",true)).thenReturn(1);
        assertTrue(store.retryFailed("task").queued());
        assertFalse(store.retryFailed("task").queued());
        verify(mapper,times(1)).retryFailed("task",true);
    }
    @Test void clearingEpochPreventsAnOlderTaskFromRestoringMemory() {
        var mapper=mock(CommunityMapper.class);var store=new CommunityStore(mapper,new ObjectMapper());
        when(mapper.epoch(1L)).thenReturn(2L);
        store.remember("task",1,1,3,4,"旧任务结果",List.of(),List.of(4L));
        verify(mapper,never()).memory(anyString(),anyString(),anyLong(),anyLong(),anyLong(),anyString());
        store.clearMemory(1);
        verify(mapper).advanceEpoch(1);
        verify(mapper).clearMemory(1);
    }
    @Test void purgedUserTombstoneSuppressesMemoryEvenWithoutExistingRecords() {
        var mapper=mock(CommunityMapper.class);var store=new CommunityStore(mapper,new ObjectMapper());
        when(mapper.epoch(1L)).thenReturn(0L);
        when(mapper.userPurged(7L)).thenReturn(true);
        store.remember("task",1,0,3,4,"包括已删除用户的互动",List.of(7L),List.of(4L));
        verify(mapper,never()).memory(anyString(),anyString(),anyLong(),anyLong(),anyLong(),anyString());
        store.purgeUser(7);
        verify(mapper).markUserPurged(7);
        verify(mapper).purgeParticipant(7);
    }
}
