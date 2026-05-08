package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图片表
 * 用于存储上传的图片信息，支持去重
 * @TableName images
 * @author 刘鑫
 * @date 2025-01-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("images")
public class Images extends BaseEntity {

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 图片访问URL
     */
    private String fileUrl;

    /**
     * 文件存储相对路径
     */
    private String filePath;

    /**
     * 文件哈希值（SHA-256，用于去重）
     */
    private String fileHash;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 文件扩展名
     */
    private String extension;

    /**
     * 图片宽度（像素）
     */
    private Integer width;

    /**
     * 图片高度（像素）
     */
    private Integer height;

    /**
     * 上传用户ID
     */
    private Long uploaderId;

    /**
     * 引用次数（被多少篇文章使用）
     */
    private Integer usageCount;

    /**
     * 状态（0禁用，1正常）
     */
    private Integer status;

    /**
     * 上传者用户名（非数据库字段，通过 JOIN 查询获取）
     */
    @TableField(exist = false)
    private String uploaderUsername;
}
