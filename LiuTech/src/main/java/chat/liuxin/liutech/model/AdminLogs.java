package chat.liuxin.liutech.model;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统操作日志表
 * 注意：日志实体不继承 BaseEntity，因为日志是不可变记录，不需要 updated_at、created_by、updated_by、deleted_at
 * @TableName system_logs
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_logs")
public class AdminLogs extends IdEntity {
    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 操作人用户名
     */
    private String operator;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作类型(登录/创建/更新/删除/恢复/发布/下线等)
     */
    private String action;

    /**
     * 目标类型(post/user/category/tag/announcement等)
     */
    private String targetType;

    /**
     * 目标ID
     */
    private Long targetId;

    /**
     * 目标名称
     */
    private String targetName;

    /**
     * 操作描述
     */
    private String description;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 浏览器User-Agent
     */
    private String userAgent;

    /**
     * 状态(0失败/1成功)
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMessage;
}
