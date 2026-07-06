package chat.liuxin.ai.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 聊天服务配置。
 *
 * 合并自 AiModelPolicy / PromptService / StreamingChatService 中的 @Value 注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai")
public class AiChatProperties {

    /** 默认模型名称 */
    private String defaultModel = "zai-org/GLM-4.6";

    /** SSE 超时时间（毫秒） */
    private long sseTimeout = 120000;

    /** 聊天历史消息限制 */
    private int chatHistoryLimit = 14;

    /** TTS 流式并发数 */
    private int ttsStreamConcurrency = 1;

    private final Security security = new Security();
    private final Agent agent = new Agent();
    private final Persona persona = new Persona();

    @Data
    public static class Security {
        /** 安全提示词守卫开关 */
        private boolean promptGuardEnabled = true;

        /** 模型策略：严格白名单模式 */
        private boolean modelPolicyStrictWhitelist = true;

        /** 模型策略：maxTokens 上限 */
        private int modelPolicyMaxTokensCeiling = 4096;
    }

    @Data
    public static class Agent {
    }

    @Data
    public static class Persona {
        /** 看板娘角色名称 */
        private String name = "看板娘";
    }
}
