package chat.liuxin.ai.service.community;

import chat.liuxin.ai.controller.admin.CommunityAdminController;
import chat.liuxin.ai.dto.community.CommunityTaskCancelResult;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityAdminControllerTest {
    @Test void workerEndpointReportsRealSchedulingFactsInsteadOfHealthUp() throws Exception {
        var mapper=mock(chat.liuxin.ai.mapper.CommunityMapper.class);
        when(mapper.workerStatus()).thenReturn(java.util.Map.of("leaseOccupied",0));
        when(mapper.queueStatus()).thenReturn(java.util.Map.of("readyCount",0,"delayedCount",1,"nextDueSeconds",28880));
        var monitor=new CommunityWorkerMonitor(mapper,new chat.liuxin.ai.infra.config.AiChatProperties(),5000,15000,60000);
        monitor.heartbeat();
        var mvc=MockMvcBuilders.standaloneSetup(new CommunityAdminController(mock(CommunityService.class),monitor)).build();
        mvc.perform(get("/ai/admin/community/worker")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("WAITING_TASK"))
            .andExpect(jsonPath("$.data.schedulerAlive").value(true))
            .andExpect(jsonPath("$.data.currentTaskId").isEmpty())
            .andExpect(jsonPath("$.data.queue.nextDueSeconds").value(28880));
    }
    @Test void articleAndStatusFiltersReachTheQueueAndCancellationReturnsThePublicationWinner() throws Exception {
        var service=mock(CommunityService.class);
        when(service.tasks(1L,2L,"READY",50)).thenReturn(List.of());
        when(service.runs(1L,2L,50)).thenReturn(List.of());
        String task="2c8dc80c-b09c-4327-9a67-9ba53d4a6800";
        when(service.cancelTask(task)).thenReturn(new CommunityTaskCancelResult(false,"已经发表",9L));
        var mvc=MockMvcBuilders.standaloneSetup(new CommunityAdminController(service,mock(CommunityWorkerMonitor.class))).build();
        mvc.perform(get("/ai/admin/community/tasks").param("botId","1").param("postId","2").param("status","READY"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/ai/admin/community/runs").param("botId","1").param("postId","2"))
            .andExpect(status().isOk());
        mvc.perform(post("/ai/admin/community/tasks/"+task+"/cancel"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelled").value(false))
            .andExpect(jsonPath("$.data.publishedCommentId").value(9));
        verify(service).tasks(1L,2L,"READY",50);verify(service).runs(1L,2L,50);
        assertEquals("hasRole('ADMIN')",CommunityAdminController.class.getAnnotation(PreAuthorize.class).value());
    }
}
