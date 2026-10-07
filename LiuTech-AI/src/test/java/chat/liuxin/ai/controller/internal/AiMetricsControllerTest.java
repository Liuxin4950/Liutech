package chat.liuxin.ai.controller.internal;

import chat.liuxin.ai.infra.filter.InternalServiceTokenFilter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiMetricsControllerTest {
    @Test
    void prometheusRequiresInternalTokenEvenWithoutUserAuthentication() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            registry.counter("review_ai_request_total").increment();
            InternalServiceTokenFilter filter = new InternalServiceTokenFilter(new ObjectMapper());
            ReflectionTestUtils.setField(filter, "expectedToken", "test-internal-token");
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiMetricsController(registry)).addFilters(filter).build();
            mvc.perform(get("/ai/internal/metrics/prometheus"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/ai/internal/metrics/prometheus").header("X-LiuTech-Internal-Token", "wrong"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/ai/internal/metrics/prometheus").header("X-LiuTech-Internal-Token", "test-internal-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("review_ai_request_total")));
        } finally {
            registry.close();
        }
    }
}
