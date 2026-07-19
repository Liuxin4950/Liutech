package chat.liuxin.ai.dto;

import lombok.Data;

/**
 * 标签 DTO（仅 id + name，供写作工具列出标签用）
 *
 * @author 刘鑫
 */
@Data
public class TagDTO {
    /** 标签ID */
    private Long id;
    /** 标签名 */
    private String name;
}
