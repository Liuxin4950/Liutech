package chat.liuxin.ai.common.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AI服务监控指标
 * 收集和记录AI服务的关键性能指标
 *
 * 指标包括：
 * - AI请求次数计数器
 * - AI响应时间计时器
 * - 错误率计量器
 * - 并发请求量测量器
 * - Token消耗统计
 *
 * 作者：刘鑫
 * 时间：2025-12-12
 */
@Component
public class AiMetrics {

    private final MeterRegistry meterRegistry;

    public AiMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录一次 AI 调用的全量指标：请求计数、响应时长、Token 用量、失败计数。
     *
     * 每次调用都会为对应模型累加 ai_requests_total 计数,并把耗时写入
     * ai_response_duration_seconds Timer。tokenCount 大于 0 时写入用量直方图。
     * success 为 false 时额外累加 ai_errors_total,方便 Grafana 计算错误率。
     */
    public void recordAiRequest(String model, boolean success, long responseTime, int tokenCount) {
        doRecord(model, success, null, responseTime, tokenCount);
    }

    /**
     * 统一记录指标：请求计数、响应时长、Token 用量、错误计数。
     * errorType 非 null 时额外打 error_type 标签，便于按异常类型拆分错误面板。
     */
    private void doRecord(String model, boolean success, String errorType, long responseTime, int tokenCount) {
        Counter.Builder requestCounter = Counter.builder("ai_requests_total")
                .description("AI服务请求总数")
                .tag("model", model)
                .tag("status", success ? "success" : "failure");
        if (errorType != null) {
            requestCounter.tag("error_type", errorType);
        }
        requestCounter.register(meterRegistry).increment();

        Timer.builder("ai_response_duration_seconds")
                .description("AI服务响应时间")
                .tag("model", model)
                .register(meterRegistry)
                .record(responseTime, TimeUnit.MILLISECONDS);

        if (tokenCount > 0) {
            DistributionSummary.builder("ai_token_usage")
                    .description("AI模型Token消耗量")
                    .tag("model", model)
                    .register(meterRegistry)
                    .record(tokenCount);
        }

        if (!success) {
            Counter.Builder errorCounter = Counter.builder("ai_errors_total")
                    .description("AI服务错误总数")
                    .tag("model", model)
                    .tag("status", "failure");
            if (errorType != null) {
                errorCounter.tag("error_type", errorType);
            }
            errorCounter.register(meterRegistry).increment();
        }
    }

    /**
     * 成功路径的快捷入口,内部委托 {@link #recordAiRequest} 并将 success 置为 true。
     */
    public void recordSuccess(String model, long responseTime, int tokenCount) {
        recordAiRequest(model, true, responseTime, tokenCount);
    }

    /**
     * 失败路径的快捷入口,与 {@link #recordAiRequest} 相比多带一个 error_type 标签,
     * 便于按异常类型(如 timeout、rate_limit、upstream_error)拆分错误面板。
     */
    public void recordFailure(String model, long responseTime, String errorType) {
        doRecord(model, false, errorType, responseTime, 0);
    }

}
