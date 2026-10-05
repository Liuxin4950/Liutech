package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.GlobalExceptionHandler;
import chat.liuxin.liutech.service.CommunityService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
