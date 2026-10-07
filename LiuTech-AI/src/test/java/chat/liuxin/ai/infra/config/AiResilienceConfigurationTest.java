package chat.liuxin.ai.infra.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerAspectExt;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerConfiguration;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerConfigurationProperties;
import io.github.resilience4j.spring6.fallback.configure.FallbackConfiguration;
import io.github.resilience4j.spring6.spelresolver.configure.SpelResolverConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.*;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiResilienceConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {}

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({CircuitBreakerConfiguration.class, FallbackConfiguration.class, SpelResolverConfiguration.class})
    static class CircuitConfig {
        @Bean CircuitBreakerConfigurationProperties properties() { return new CircuitBreakerConfigurationProperties(); }
        @Bean StreamProbe streamProbe() { return new StreamProbe(); }
    }

    public static class StreamProbe {
        final AtomicInteger attempts = new AtomicInteger();

        @CircuitBreaker(name = "streamProbe")
        public Flux<String> request() {
            return Flux.defer(() -> {
                attempts.incrementAndGet();
                return Flux.error(new IllegalStateException("upstream failure after subscription"));
            });
        }
    }

    @Test
    void applicationImportsExternalCircuitBreakerParameters() {
        SpringApplication app = new SpringApplication(EmptyConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setLogStartupInfo(false);
        try (var context = app.run("--spring.config.location=classpath:application.yml",
                "--spring.profiles.active=dev", "--spring.main.banner-mode=off")) {
            var environment = context.getEnvironment();
            assertEquals("classpath:resilience4j-config.yml", environment.getProperty("spring.config.import"));
            assertEquals(20, environment.getProperty("resilience4j.circuitbreaker.instances.aiService.minimumNumberOfCalls", Integer.class));
            assertEquals("300s", environment.getProperty("resilience4j.circuitbreaker.instances.aiService.slowCallDurationThreshold"));
            assertNull(environment.getProperty("resilience4j.config.import"));
        }
    }

    @Test
    void subscribedFluxFailureCountsAsFailureWithoutAutomaticRetry() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(CircuitConfig.class)) {
            assertFalse(context.getBeansOfType(CircuitBreakerAspectExt.class).isEmpty(), "必须启用 Reactor 熔断扩展");
            StreamProbe probe = context.getBean(StreamProbe.class);
            assertThrows(IllegalStateException.class, () -> probe.request().blockLast());
            var metrics = context.getBean(CircuitBreakerRegistry.class).circuitBreaker("streamProbe").getMetrics();
            assertEquals(1, metrics.getNumberOfFailedCalls());
            assertEquals(0, metrics.getNumberOfSuccessfulCalls());
            assertEquals(1, ((StreamProbe) ((org.springframework.aop.framework.Advised) probe).getTargetSource().getTarget()).attempts.get(), "错误流不得自动重放模型/工具调用");
        }
    }
}
