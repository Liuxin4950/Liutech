package chat.liuxin.liutech.model;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 资源下载记录实体类
 * 
 * @author 刘鑫
 * @date 2025-01-15
 */
@Data
@TableName("download_logs")
public class ResourceDownloads {
    
    /**
     * 下载记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 资源ID
     */
    private Long resourceId;
    
    /**
     * 使用积分
     */
    @TableField("points_used")
    private BigDecimal pointsUsed;
    
    /**
     * 下载时间
     */
    @TableField("downloaded_at")
    private Date downloadedAt;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
    
    /**
     * 删除时间（软删除）
     */
    private Date deletedAt;
}