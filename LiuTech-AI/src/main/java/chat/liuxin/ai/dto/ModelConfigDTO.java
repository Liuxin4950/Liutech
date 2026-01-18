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
     * 模型名称（如 zai-org/GLM-4.6）
     */
    private String modelName;

    /**
     * 显示名称（如 GLM-4.6）
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
     * 最大token数
     */
    private Integer maxTokens;

    /**
     * 默认温度参数
     */
    private BigDecimal temperature;

    /**
     * 模型描述
     */
    private String description;
}
