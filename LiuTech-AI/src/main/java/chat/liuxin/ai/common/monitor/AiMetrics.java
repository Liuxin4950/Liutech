package chat.liuxin.ai.common.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

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
     * 记录AI请求
     *
     * @param model AI模型名称
     * @param success 是否成功
     * @param responseTime 响应时间（毫秒）
     * @param tokenCount Token消耗量
     */
    public void recordAiRequest(String model, boolean success, long responseTime, int tokenCount) {
        // 记录请求次数
        Counter.builder("ai_requests_total")
                .description("AI服务请求总数")
                .tag("model", model)
                .tag("status", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();

        // 记录响应时间
        Timer.builder("ai_response_duration_seconds")
                .description("AI服务响应时间")
                .tag("model", model)
                .register(meterRegistry)
                .record(responseTime, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 记录Token消耗
        if (tokenCount > 0) {
            DistributionSummary.builder("ai_token_usage")
                    .description("AI模型Token消耗量")
                    .tag("model", model)
                    .register(meterRegistry)
                    .record(tokenCount);
        }

        // 记录错误
        if (!success) {
            Counter.builder("ai_errors_total")
                    .description("AI服务错误总数")
                    .tag("model", model)
                    .tag("status", "failure")
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * 增加并发请求计数
     */
    public void incrementConcurrentRequests() {
        Counter.builder("ai_concurrent_requests")
                .description("AI服务并发请求量-增加")
                .tag("action", "increment")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 减少并发请求计数
     */
    public void decrementConcurrentRequests() {
        Counter.builder("ai_concurrent_requests")
                .description("AI服务并发请求量-减少")
                .tag("action", "decrement")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录AI请求成功
     *
     * @param model AI模型名称
     * @param responseTime 响应时间（毫秒）
     * @param tokenCount Token消耗量
     */
    public void recordSuccess(String model, long responseTime, int tokenCount) {
        recordAiRequest(model, true, responseTime, tokenCount);
    }

    /**
     * 记录AI请求失败
     *
     * @param model AI模型名称
     * @param responseTime 响应时间（毫秒）
     * @param errorType 错误类型
     */
    public void recordFailure(String model, long responseTime, String errorType) {
        Counter.builder("ai_requests_total")
                .description("AI服务请求总数")
                .tag("model", model)
                .tag("status", "failure")
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();

        Timer.builder("ai_response_duration_seconds")
                .description("AI服务响应时间")
                .tag("model", model)
                .register(meterRegistry)
                .record(responseTime, java.util.concurrent.TimeUnit.MILLISECONDS);

        Counter.builder("ai_errors_total")
                .description("AI服务错误总数")
                .tag("model", model)
                .tag("status", "failure")
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录自定义指标
     *
     * @param name 指标名称
     * @param value 指标值
     * @param tagKey 标签键
     * @param tagValue 标签值
     */
    public void recordCustomMetric(String name, double value, String tagKey, String tagValue) {
        Counter customCounter = Counter.builder(name)
                .description("自定义指标")
                .tag(tagKey, tagValue)
                .register(meterRegistry);
        customCounter.increment(value);
    }
}
