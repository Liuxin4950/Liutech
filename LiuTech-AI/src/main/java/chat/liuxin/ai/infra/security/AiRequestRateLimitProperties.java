package chat.liuxin.ai.infra.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.security.rate-limit")
public class AiRequestRateLimitProperties {
    private boolean enabled = true;
    private long windowSeconds = 60;
    private int guestMaxRequests = 20;
    private int userMaxRequests = 60;
    private int adminMaxRequests = 120;
    private int maxTrackedKeys = 10000;
}
