package chat.liuxin.ai.infra.security;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiCapabilityContext {
    private String role;
    private boolean authenticated;
    private boolean admin;
    private Long userId;
    private String username;
    private List<String> capabilities;
}
