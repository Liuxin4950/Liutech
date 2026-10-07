package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.GlobalExceptionHandler;
import chat.liuxin.liutech.service.CommunityService;
import chat.liuxin.liutech.req.CommunityReq;
import chat.liuxin.liutech.resp.CommunityResp;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityAdminReviewControllerTest {
    @Test void missingCommentReturns404ThroughUnifiedHandlerAndReviewControllerRemainsAdminOnly() throws Exception {
        CommunityService service=mock(CommunityService.class);
        when(service.commentThreadForAdmin(99L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND,"评论不存在"));
        var mvc=MockMvcBuilders.standaloneSetup(new CommunityAdminController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/admin/community/comments/99/thread"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("评论不存在"));
        assertEquals("hasRole('ADMIN')",CommunityAdminController.class.getAnnotation(PreAuthorize.class).value());
    }

    @Test void backfillReturnsRealQueuedCountsAndEmptyBodyUsesDefaultBatchSize() throws Exception {
        CommunityService service=mock(CommunityService.class);
        when(service.backfill(new CommunityReq.Backfill(10,null))).thenReturn(new CommunityResp.Backfill(8,2,10));
        var mvc=MockMvcBuilders.standaloneSetup(new CommunityAdminController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/admin/community/backfill").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.queued").value(8))
            .andExpect(jsonPath("$.data.skipped").value(2)).andExpect(jsonPath("$.data.postCount").value(10));
        verify(service).backfill(new CommunityReq.Backfill(10,null));
    }

    @Test void backfillValidatesBatchAndRoleLimitsBeforeCallingService() throws Exception {
        CommunityService service=mock(CommunityService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new CommunityAdminController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String body : new String[]{"{\"limit\":21}","{\"limit\":0}","{\"botIds\":[1,2,3]}","{\"botIds\":[null]}"}) {
            mvc.perform(post("/admin/community/backfill").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test void manualInviteDoesNotPretendNoMatchingRoleCreatedAnEvent() throws Exception {
        CommunityService service=mock(CommunityService.class);
        when(service.invite(2L,null)).thenReturn(new CommunityResp.Queued(0));
        var mvc=MockMvcBuilders.standaloneSetup(new CommunityAdminController(service)).build();
        mvc.perform(post("/admin/community/posts/2/invite").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.queued").value(0));
    }
}
