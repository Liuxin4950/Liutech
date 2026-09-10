package chat.liuxin.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
     * 单次输出上限（token）
     *
     * 不能超过 spring.ai.security.model-policy-max-tokens-ceiling，否则保存时直接报错。
     */
    @Min(value = 1, message = "最大 Token 必须大于 0")
    private Integer maxTokens;

    /**
     * 模型上下文窗口（输入 + 输出总 token 上限）
     *
     * 必须大于 maxTokens，否则输入预算为 0，任何请求都会因"输入内容过长"失败。
     * 可留空，留空时按全局默认上下文窗口兜底。
     */
    @Min(value = 1024, message = "上下文窗口至少 1024")
    private Integer contextWindow;

    /**
     * 默认温度参数
     */
    @DecimalMin(value = "0.0", message = "Temperature 不能小于 0")
    @DecimalMax(value = "1.0", message = "Temperature 不能大于 1")
    private BigDecimal temperature;

    /**
     * 模型描述
     */
    private String description;
}
