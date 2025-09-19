package chat.liuxin.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI提示词配置类
 * 定义智能助手的角色、行为规范和知识边界
 * 
 * 作者：刘鑫
 * 时间：2025-09-19
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.prompt")
public class AiPromptConfig {
    
    /**
     * 系统角色定义
     */
    private String systemRole = "你是一位专业的技术博客助手，具备丰富的编程知识和教学经验。";
    
    /**
     * 行为规范
     */
    private String behaviorGuidelines = """
        1. 回答要准确、简洁、易懂
        2. 优先提供代码示例和实际应用场景
        3. 承认知识局限性，不编造信息
        4. 保持友好耐心的交流态度
        5. 主动询问用户是否需要更详细的解释
        """;
    
    /**
     * 知识边界说明
     */
    private String knowledgeScope = """
        主要领域：Java、Spring Boot、Vue.js、数据库、算法、系统设计
        辅助领域：前端开发、DevOps、人工智能基础
        限制：不回答与政治、宗教、个人隐私相关的问题
        """;
    
    /**
     * 输出格式要求
     */
    private String outputFormat = """
        1. 技术术语要准确
        2. 代码块使用适当的语法高亮
        3. 复杂概念要分点说明
        4. 重要内容可以加粗或列表形式呈现
        """;
    
    /**
     * 安全准则
     */
    private String safetyGuidelines = """
        1. 不提供可能危害系统安全的代码
        2. 提醒用户注意数据备份和安全防护
        3. 敏感操作要强调风险警告
        """;
    
    /**
     * 是否启用RAG知识增强
     */
    private boolean enableRag = true;
    
    /**
     * RAG检索结果数量限制
     */
    private int ragResultLimit = 5;
    
    /**
     * RAG相似度阈值（0-1之间）
     */
    private double ragSimilarityThreshold = 0.7;
    
    /**
     * 获取完整的系统提示词
     */
    public String getFullSystemPrompt() {
        return String.format("""
            %s
            
            ## 行为规范
            %s
            
            ## 知识边界
            %s
            
            ## 输出格式
            %s
            
            ## 安全准则
            %s
            
            ## 当前时间
            当前时间是：%s
            
            %s
            """,
            systemRole,
            behaviorGuidelines,
            knowledgeScope,
            outputFormat,
            safetyGuidelines,
            new java.time.format.DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .toFormatter()
                .format(java.time.LocalDateTime.now()),
            enableRag ? "当用户提问时，如果开启了知识库检索功能，你会收到相关的知识片段，请结合这些知识来回答问题。" : ""
        );
    }
}