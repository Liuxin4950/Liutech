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

    /** 下载类型：免费 */
    public static final int DOWNLOAD_TYPE_FREE = 0;
    /** 下载类型：积分下载 */
    public static final int DOWNLOAD_TYPE_PAID = 1;

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
     * 外部链接（网盘、其他网站等）
     */
    private String externalLink;

    /**
     * 资源类型：file=上传文件，link=外部链接，both=两者都有
     */
    private String resourceType;

    /**
     * 购买后显示的说明（提取码、使用说明等）
     */
    private String purchasedNote;

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