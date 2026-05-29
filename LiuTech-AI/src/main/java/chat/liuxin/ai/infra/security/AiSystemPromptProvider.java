package chat.liuxin.ai.infra.security;

import chat.liuxin.ai.infra.config.AiPromptConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 统一系统提示词来源。
 *
 * Agent 路径和 legacy 聊天路径都必须从这里获取模型系统提示词，
 * 避免人格、能力边界和安全规则在多条链路中漂移。
 */
@Component
@RequiredArgsConstructor
public class AiSystemPromptProvider {

    private final AiPromptConfig aiPromptConfig;
    private final AiPromptSecurityPolicy promptSecurityPolicy;

    @Value("${spring.ai.agent.persona.name:看板娘}")
    private String personaName;

    public String buildSystemPrompt() {
        return promptSecurityPolicy.appendSystemRules(aiPromptConfig.getFullSystemPrompt() + "\n\n" + capabilityBoundaryRules());
    }

    private String capabilityBoundaryRules() {
        return """
                ## 模型能力与路径边界
                - Web 和 Admin 看板娘主聊天统一使用 Agent 路径：实时响应为 /ai/agent/stream，完整响应为 /ai/agent/chat。
                - /ai/chat、/ai/chat/stream、/ai/recommend 是 legacy 兼容路径，不承接新的看板娘能力。
                - 访客和普通用户只能使用聊天、公开文章读取、搜索、推荐和总结能力。
                - 管理员可以要求写作辅助、创建草稿、发布和下架，但创建草稿、发布、下架必须走服务端确认流程。
                - %s新生成的文章第一次只能保存为草稿；管理员审查并确认后，才允许进入发布流程。
                - 本期禁止删除文章、管理用户或角色、通过自然语言管理模型配置、直连数据库、执行 SQL 或 Shell。
                - 前端展示的 capability 只用于说明能力；真实授权永远以服务端认证、角色回查和工具策略为准。
                """.formatted(personaName).trim();
    }
}
