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
    private String defaultModel = "deepseek-ai/DeepSeek-V3.2";

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

        /**
         * 模型策略：单次输出上限的全局安全上限
         *
         * 管理端为单个模型配置的 max_tokens 不得超过此值（保存时就报错），
         * 读取时若仍超限会被夹小并打 WARN —— 不再像过去那样静默夹取。
         */
        private int modelPolicyMaxTokensCeiling = 65536;

        /**
         * 模型策略：未配置 context_window 时的兜底上下文窗口
         *
         * 管理端没填的模型按此值算输入预算。取 32768 是保守选择：
         * 主流开源模型上下文都 ≥ 32K，宁可少塞也不越界。
         */
        private int modelPolicyDefaultContextWindow = 32768;

        /**
         * 模型策略：单次请求输入预算的全局护栏（token）
         *
         * 与「模型上下文窗口」是两件事：上下文窗口是模型能力上限，
         * 本值是成本护栏 —— 即使模型支持 200K 上下文，也不希望一次请求就烧掉大量额度。
         * 设为 0 或负数表示不限制。
         */
        private int modelPolicyMaxInputTokens = 96000;
    }

    /** Agent 相关限制：注入模型的内容体积控制 */
    @Data
    public static class Agent {
        /**
         * 单个工具结果注入上下文的上限（字符）
         *
         * 主要针对"按 ID 读整篇文章"这类工具：正文动辄上万字，
         * 整篇直接塞进上下文正是"模型卡住"的根因。超出部分截断并显式标注。
         */
        private int maxToolResultChars = 12000;

        /** 草稿快照 / 站点上下文注入上限（字符），超出截断 */
        private int maxContextChars = 6000;

        /** article-results 事件最多抽取几篇文章 */
        private int maxArticleResults = 8;
    }

    @Data
    public static class Persona {
        /** 看板娘角色名称 */
        private String name = "看板娘";
    }
}
