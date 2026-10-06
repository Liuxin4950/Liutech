package chat.liuxin.liutech.controller.internal;

import chat.liuxin.liutech.common.GlobalExceptionHandler;
import chat.liuxin.liutech.req.CommunityReq;
import chat.liuxin.liutech.resp.CommunityResp;
import chat.liuxin.liutech.service.CommunityService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityInternalMetadataControllerTest {
    @Test void metadataReturnsOnlyCompactDisplayFactsInUnifiedResult() throws Exception {
        CommunityService service=mock(CommunityService.class);
        when(service.metadata(new CommunityReq.Metadata(List.of(2L),List.of(7L))))
            .thenReturn(new CommunityResp.Metadata(List.of(new CommunityResp.PostMetadata(2L,"标题")),
                List.of(new CommunityResp.CommentMetadata(7L,"评论","作者"))));
        var mvc=MockMvcBuilders.standaloneSetup(new CommunityInternalController(service)).build();
        mvc.perform(post("/internal/community/metadata").contentType(MediaType.APPLICATION_JSON)
                .content("{\"postIds\":[2],\"commentIds\":[7]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.posts[0].title").value("标题"))
            .andExpect(jsonPath("$.data.comments[0].authorName").value("作者"))
            .andExpect(jsonPath("$.data.comments[0].user").doesNotExist());
    }

    @Test void metadataRejectsUnboundedOrInvalidIdsBeforeCallingService() throws Exception {
        CommunityService service=mock(CommunityService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new CommunityInternalController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String body : List.of(
                "{\"postIds\":["+String.join(",",Collections.nCopies(101,"1"))+"],\"commentIds\":[]}",
                "{\"postIds\":[],\"commentIds\":["+String.join(",",Collections.nCopies(201,"1"))+"]}",
                "{\"postIds\":[0],\"commentIds\":[]}","{\"postIds\":[],\"commentIds\":[null]}")) {
            mvc.perform(post("/internal/community/metadata").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(service);
    }
}
