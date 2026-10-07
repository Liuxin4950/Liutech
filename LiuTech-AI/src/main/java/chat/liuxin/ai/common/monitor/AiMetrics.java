package chat.liuxin.ai.common.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** AI 请求、流式延迟和供应商真实 Token 用量。 */
@Component
public class AiMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> activeStreams = new ConcurrentHashMap<>();

    public AiMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAiRequest(String model, boolean success, long responseTime, int tokenCount) {
        doRecord(model, success, "none", responseTime, tokenCount);
    }

    private void doRecord(String model, boolean success, String errorType, long responseTime, long tokenCount) {
        Counter.builder("ai_requests_total").description("AI服务请求总数")
                .tag("model", model).tag("status", success ? "success" : "failure")
                .tag("error_type", errorType).register(meterRegistry).increment();
        Timer.builder("ai_response_duration_seconds").description("AI服务响应时间")
                .tag("model", model).register(meterRegistry).record(Math.max(0, responseTime), TimeUnit.MILLISECONDS);
        if (tokenCount > 0) {
            DistributionSummary.builder("ai_token_usage").description("AI模型Token消耗量")
                    .tag("model", model).register(meterRegistry).record(tokenCount);
        }
        if (!success) {
            Counter.builder("ai_errors_total").description("AI服务错误总数")
                    .tag("model", model).tag("status", "failure").tag("error_type", errorType)
                    .register(meterRegistry).increment();
        }
    }

    public void recordSuccess(String model, long responseTime, int tokenCount) {
        recordAiRequest(model, true, responseTime, tokenCount);
    }

    public void recordFailure(String model, long responseTime, String errorType) {
        doRecord(model, false, errorType == null ? "unknown" : errorType, responseTime, 0);
    }

    /** 非流式业务请求也记录失败前已返回的供应商用量，不能因决策校验失败抹掉消耗。 */
    public void recordCompleted(String model, String mode, boolean success, long responseTime, UsageTracker usage) {
        doRecord(model, success, success ? "none" : mode, responseTime, usage.inputTokens() + usage.outputTokens());
        recordUsage(model, mode, usage);
    }

    public void recordStreamStarted(String model, String mode) {
        active(model, mode).incrementAndGet();
        Counter.builder("ai_stream_requests_started_total").tag("model", model).tag("mode", mode)
                .register(meterRegistry).increment();
    }

    public void recordStreamFirstToken(String model, String mode, long elapsedMillis) {
        Timer.builder("ai_stream_first_token_seconds").description("AI 流式首个正文字符延迟")
                .tag("model", model).tag("mode", mode).register(meterRegistry)
                .record(Math.max(0, elapsedMillis), TimeUnit.MILLISECONDS);
    }

    /** 调用方保证每次请求只收尾一次；取消、超时也记真实已返回的 usage。 */
    public void recordStreamFinished(String model, String mode, String outcome, long elapsedMillis, UsageTracker usage) {
        active(model, mode).updateAndGet(value -> Math.max(0, value - 1));
        Counter.builder("ai_stream_requests_total").tag("model", model).tag("mode", mode).tag("outcome", outcome)
                .register(meterRegistry).increment();
        Timer.builder("ai_stream_duration_seconds").tag("model", model).tag("mode", mode).tag("outcome", outcome)
                .register(meterRegistry).record(Math.max(0, elapsedMillis), TimeUnit.MILLISECONDS);
        long input = usage == null ? 0 : usage.inputTokens();
        long output = usage == null ? 0 : usage.outputTokens();
        doRecord(model, "success".equals(outcome), "success".equals(outcome) ? "none" : outcome,
                elapsedMillis, input + output);
        recordUsage(model, mode, usage);
    }

    private void recordUsage(String model, String mode, UsageTracker usage) {
        if (usage == null) return;
        recordTokens(model, "input", usage.inputTokens());
        recordTokens(model, "output", usage.outputTokens());
        if (usage.turns() > 0) {
            DistributionSummary.builder("ai_model_turns").tag("model", model).tag("mode", mode)
                    .register(meterRegistry).record(usage.turns());
        }
    }

    private void recordTokens(String model, String direction, long count) {
        if (count > 0) {
            Counter.builder("ai_tokens_total").description("供应商返回的真实 Token 用量")
                    .tag("model", model).tag("direction", direction).register(meterRegistry).increment(count);
        }
    }

    private AtomicLong active(String model, String mode) {
        return activeStreams.computeIfAbsent(model + "\n" + mode, ignored -> {
            AtomicLong count = new AtomicLong();
            Gauge.builder("ai_stream_active_requests", count, AtomicLong::doubleValue)
                    .tag("model", model).tag("mode", mode).register(meterRegistry);
            return count;
        });
    }

    /** 经 ToolContext 传给模型客户端；用量只来自供应商，缺失与真实零值分别保留。 */
    public static final class UsageTracker {
        public static final String CONTEXT_KEY = "aiUsageTracker";
        private final AtomicLong input = new AtomicLong();
        private final AtomicLong output = new AtomicLong();
        private final AtomicLong turns = new AtomicLong();
        private final AtomicLong reportedTurns = new AtomicLong();
        private final AtomicLong completeTurns = new AtomicLong();

        public void record(long inputTokens, long outputTokens) {
            Round round = startRound();
            round.report(inputTokens, outputTokens);
            round.finish();
        }

        public Round startRound() {
            turns.incrementAndGet();
            return new Round();
        }

        public long inputTokens() { return input.get(); }
        public long outputTokens() { return output.get(); }
        public long turns() { return turns.get(); }
        public boolean tokenUsageAvailable() { return reportedTurns.get() > 0; }
        public boolean tokenUsageComplete() { return turns.get() > 0 && completeTurns.get() == turns.get(); }

        /** 同轮流式 usage 是累计快照；重复帧不相加，错误或取消也只收尾一次。 */
        public final class Round {
            private final AtomicLong roundInput = new AtomicLong(-1);
            private final AtomicLong roundOutput = new AtomicLong(-1);
            private final AtomicBoolean finished = new AtomicBoolean();

            public void report(Long inputTokens, Long outputTokens) {
                if (inputTokens != null && inputTokens >= 0) roundInput.set(inputTokens);
                if (outputTokens != null && outputTokens >= 0) roundOutput.set(outputTokens);
            }

            public Long outputTokens() { return roundOutput.get() < 0 ? null : roundOutput.get(); }

            public void finish() {
                if (!finished.compareAndSet(false, true)) return;
                long in = roundInput.get(), out = roundOutput.get();
                if (in >= 0) input.addAndGet(in);
                if (out >= 0) output.addAndGet(out);
                if (in >= 0 || out >= 0) reportedTurns.incrementAndGet();
                if (in >= 0 && out >= 0) completeTurns.incrementAndGet();
            }
        }
    }
}
