package chat.liuxin.ai.infra.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

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
    /** 只有这些地址/CIDR 对应的直连代理才能提供 X-Real-IP；默认全部不信任。 */
    private List<String> trustedProxies = new ArrayList<>();
}
