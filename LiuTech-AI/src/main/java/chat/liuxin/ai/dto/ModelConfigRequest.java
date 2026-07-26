package chat.liuxin.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模型配置请求 DTO
 * 用于添加和编辑模型配置
 *
 * @author 刘鑫
 * @since 2025-01-18
 */
@Data
public class ModelConfigRequest {

    /**
     * 模型名称（如 deepseek-ai/DeepSeek-V3.2）
     */
    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /**
     * 显示名称（如 DeepSeek-V3.2）
     */
    @NotBlank(message = "显示名称不能为空")
    private String displayName;

    /**
     * 提供商（siliconflow/openai/ollama等）
     */
    @NotBlank(message = "提供商不能为空")
    private String provider;

    /**
     * 是否启用
     */
    @NotNull(message = "启用状态不能为空")
    private Boolean isEnabled;

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
