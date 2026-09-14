package chat.liuxin.ai.controller.internal;

import chat.liuxin.ai.dto.UserDataPurgeRequest;
import chat.liuxin.ai.dto.UserDataPurgeResult;
import chat.liuxin.ai.service.MemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDataPurgeControllerTest {

    @Test
    void singlePurgeShouldReturnDeletedCounts() {
        MemoryService memoryService = mock(MemoryService.class);
        when(memoryService.clearAllMemory("7")).thenReturn(new MemoryService.PurgeCounts(2, 9));

        UserDataPurgeResult result = new UserDataPurgeController(memoryService).purgeOne(7L);

        assertEquals(2, result.conversationsDeleted());
        assertEquals(9, result.messagesDeleted());
    }

    @Test
    void batchPurgeShouldDeduplicateIds() {
        MemoryService memoryService = mock(MemoryService.class);
        when(memoryService.clearAllMemory("7")).thenReturn(new MemoryService.PurgeCounts(0, 0));
        UserDataPurgeRequest request = new UserDataPurgeRequest();
        request.setUserIds(List.of(7L, 7L));

        List<UserDataPurgeResult> results = new UserDataPurgeController(memoryService).purgeBatch(request);

        assertEquals(1, results.size());
        verify(memoryService, times(1)).clearAllMemory("7");
    }
}
