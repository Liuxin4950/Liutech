package chat.liuxin.ai.infra.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
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
