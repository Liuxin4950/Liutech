package chat.liuxin.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 模型配置 DTO
 * 用于前端展示的模型配置信息
 *
 * @author 刘鑫
 * @since 2025-01-18
 */
@Data
public class ModelConfigDTO {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 模型名称（如 deepseek-ai/DeepSeek-V3.2）
     */
    private String modelName;

    /**
     * 显示名称（如 DeepSeek-V3.2）
     */
    private String displayName;

    /**
     * 提供商（siliconflow/openai/ollama等）
     */
    private String provider;

    /**
     * 是否启用
     */
    private Boolean isEnabled;

    /**
     * 是否为默认模型
     */
    private Boolean isDefault;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 单次输出上限（token），管理端配置值
     */
    private Integer maxTokens;

    /**
     * 模型上下文窗口（输入 + 输出总 token 上限），管理端配置值；未配置时为 null
     */
    private Integer contextWindow;

    /**
     * 默认温度参数
     */
    private BigDecimal temperature;

    /**
     * 模型描述
     */
    private String description;

    // ==================== 生效值（只读，供管理端展示"实际会按多少跑"） ====================

    /**
     * 输出上限实际生效值
     *
     * 与 maxTokens 不同时，说明被全局安全上限（model-policy-max-tokens-ceiling）夹小。
     */
    private Integer effectiveMaxTokens;

    /**
     * 上下文窗口实际生效值
     *
     * 未配置 contextWindow 时是全局默认值，方便管理端看出"没配会按多少算"。
     */
    private Integer effectiveContextWindow;

    /**
     * 输入预算（token）
     *
     * = 有效上下文 − 有效输出 − 安全余量，再受全局输入护栏约束。
     * 这是真正决定"一次能塞多少历史、草稿与文章正文"的数字。
     */
    private Integer inputBudgetTokens;

    /**
     * 输出上限是否被全局安全上限夹小
     *
     * true 表示管理端配置值大于 model-policy-max-tokens-ceiling，
     * 管理端应提示"实际按 N 生效"，避免再次出现"配了不生效且无提示"。
     */
    private Boolean outputClamped;

    /**
     * 输入预算是否被全局成本护栏夹小
     *
     * true 表示模型上下文窗口大于 model-policy-max-input-tokens，
     * 管理端应说明"上下文能力 205K，但单次请求输入预算被护栏限制为 96K"。
     */
    private Boolean inputCappedByPolicy;
}
