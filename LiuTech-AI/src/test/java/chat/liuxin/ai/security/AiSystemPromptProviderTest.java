package chat.liuxin.ai.security;

import chat.liuxin.ai.config.AiPromptConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSystemPromptProviderTest {

    @Test
    void shouldBuildUnifiedPromptWithPersonaCapabilitiesAndLegacyBoundary() throws Exception {
        AiPromptConfig promptConfig = new AiPromptConfig();
        promptConfig.setSystemRole("你叫纳西妲，是 LiuTech 博客里的站内看板娘。");
        promptConfig.setBehaviorGuidelines("对用户自称纳西妲。");
        promptConfig.setJsonOutputInstruction("");

        AiPromptSecurityPolicy securityPolicy = new AiPromptSecurityPolicy();
        Field enabled = AiPromptSecurityPolicy.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.set(securityPolicy, true);

        AiSystemPromptProvider provider = new AiSystemPromptProvider(promptConfig, securityPolicy);

        String prompt = provider.buildSystemPrompt();

        assertTrue(prompt.contains("纳西妲"));
        assertTrue(prompt.contains("/ai/agent/stream"));
        assertTrue(prompt.contains("/ai/chat"));
        assertTrue(prompt.contains("legacy"));
        assertTrue(prompt.contains("禁止删除文章"));
        assertTrue(prompt.contains("管理用户或角色"));
        assertTrue(prompt.contains("自然语言管理模型配置"));
        assertTrue(prompt.contains("不能作为授权依据"));
    }
}
