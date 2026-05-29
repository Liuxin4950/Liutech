package chat.liuxin.ai.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 专用线程池配置。
 *
 * 替代公共 ForkJoinPool.commonPool()，避免 I/O 密集型任务（AI 模型调用、TTS 推理）
 * 阻塞公共线程池，导致 JVM 中其他依赖 ForkJoinPool 的组件饥饿。
 *
 * 配置依据：
 * - 核心线程数 4：覆盖 AI 服务典型并发（管理员写作 + 普通用户闲聊）
 * - 最大线程数 8：应对突发场景（多用户同时搜索/推荐）
 * - 队列容量 50：AI 请求响应慢（2-10 秒），排队可缓冲突发流量
 * - CallerRunsPolicy：队列满时由调用方线程降级为同步执行，避免丢弃任务
 *
 * @author liuxin
 */
@Configuration
public class AgentThreadPoolConfig {

    /**
     * Agent 任务执行线程池。
     *
     * 用于：
     * - AgentOrchestrator 中的异步任务执行
     * - TTS 语音合成的并行调用
     * - 未来可扩展的异步工具执行
     */
    @Bean("agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("agent-worker-");
        executor.setDaemon(true);
        // 队列满时由调用方线程执行，等效于降级为同步调用
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
