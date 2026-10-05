package chat.liuxin.ai.service.community;

import chat.liuxin.ai.mapper.CommunityMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityStoreTest {
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
