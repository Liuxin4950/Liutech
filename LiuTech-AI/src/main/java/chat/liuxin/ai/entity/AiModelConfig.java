package chat.liuxin.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI模型配置实体
 * 对应表 ai_model_config
 *
 * 作者：刘鑫
 * 时间：2025-01-18
 * 关系与设计：
 * - 存储可用的AI模型配置，供管理员管理
 * - 通过 is_default 字段标识用户前端使用的默认模型
 * - 通过 is_enabled 字段控制模型的启用/禁用状态
 * - 支持模型的排序、token限制、温度参数等配置
 */
@Data
@TableName("ai_model_config")
public class AiModelConfig {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模型名称（如 deepseek-ai/DeepSeek-V3.2）
     */
    @TableField("model_name")
    private String modelName;

    /**
     * 显示名称（如 DeepSeek-V3.2）
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 提供商（siliconflow/openai/ollama等）
     */
    @TableField("provider")
    private String provider;

    /**
     * 是否启用（0=禁用 1=启用）
     */
    @TableField("is_enabled")
    private Boolean isEnabled;

    /**
     * 是否为默认模型（0=否 1=是，只能有一个默认）
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * 排序顺序（数字越小越靠前）
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 最大token数限制
     */
    @TableField("max_tokens")
    private Integer maxTokens;

    /**
     * 默认温度参数
     */
    @TableField("temperature")
    private BigDecimal temperature;

    /**
     * 模型描述
     */
    @TableField("description")
    private String description;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
