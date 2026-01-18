package chat.liuxin.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型使用统计 DTO
 * 用于展示模型使用次数统计
 *
 * @author 刘鑫
 * @since 2025-01-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelUsageStats {
    /**
     * 模型名称
     */
    private String model;

    /**
     * 使用次数
     */
    private Long usageCount;
}
