package chat.liuxin.ai.controller.internal;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 复用内部令牌过滤器保护指标抓取；公网 Nginx 已阻断 /ai/internal/**。 */
@RestController
@RequestMapping("/ai/internal/metrics")
@RequiredArgsConstructor
public class AiMetricsController {
    private final PrometheusMeterRegistry meterRegistry;

    @GetMapping(value = "/prometheus", produces = "text/plain; version=0.0.4; charset=utf-8")
    public String prometheus() {
        return meterRegistry.scrape();
    }
}
