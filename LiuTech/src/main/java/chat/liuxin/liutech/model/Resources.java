package chat.liuxin.liutech.model;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资源表
 * @TableName resources
 * @author 刘鑫
 * @date 2025-01-08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resources")
public class Resources extends BaseEntity {
    /**
     * 资源名称
     */
    private String name;

    /**
     * 资源描述
     */
    private String description;

    /**
     * 文件存储路径或URL
     */
    private String fileUrl;

    /**
     * 上传用户ID
     */
    private Long uploaderId;

    /**
     * 下载类型（0免费，1积分）
     */
    private Integer downloadType;

    /**
     * 下载所需积分
     */
    private BigDecimal pointsNeeded;
}