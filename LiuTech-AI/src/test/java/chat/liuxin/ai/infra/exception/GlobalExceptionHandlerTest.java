package chat.liuxin.ai.infra.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import chat.liuxin.ai.controller.admin.CommunityAdminController;
import chat.liuxin.ai.service.community.CommunityService;
import chat.liuxin.ai.service.community.CommunityWorkerMonitor;
import jakarta.validation.Validation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private org.springframework.test.web.servlet.MockMvc validatedCommunity(CommunityService service) {
        var controller = new CommunityAdminController(service, mock(CommunityWorkerMonitor.class));
        var proxy = new ProxyFactory(controller);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor(Validation.buildDefaultValidatorFactory().getValidator()));
        return MockMvcBuilders.standaloneSetup(proxy.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void invalidRunIdIsRejectedBeforeBusinessWithoutEchoingInput() throws Exception {
        var service = mock(CommunityService.class);
        var mvc = validatedCommunity(service);
        mvc.perform(get("/ai/admin/community/runs/invalid-uuid-private-input").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-input"))));
        verifyNoInteractions(service);
    }

    @Test
    void negativeQueryIdIsRejectedBeforeBusiness() throws Exception {
        var service = mock(CommunityService.class);
        validatedCommunity(service).perform(get("/ai/admin/community/tasks").param("botId", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void validMissingRunPreservesNotFoundResponse() throws Exception {
        var service = mock(CommunityService.class);
        String id = "00000000-0000-0000-0000-000000000000";
        when(service.run(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "执行记录不存在"));
        validatedCommunity(service).perform(get("/ai/admin/community/runs/" + id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        verify(service).run(id);
    }
    @RestController
    static class JsonController {
        @PostMapping("/ai/chat/stream")
        Map<String, Object> json(@RequestBody Map<String, Object> request) { return request; }
    }

    @Test
    void malformedJsonReturns400BeforeSseStartsWithoutEchoingBody() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new JsonController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/ai/chat/stream").accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON).content("{private-prompt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-prompt"))));
    }
}
