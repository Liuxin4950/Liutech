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
 * 积分流水实体类
 * 记录所有积分变动（签到、消费、退款、管理员调整等）
 *
 * @author 刘鑫
 * @date 2025-01-18
 */
@Data
@TableName("points_transactions")
public class PointsTransaction {

    /**
     * 流水ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 交易类型：checkin(签到), consumption(消费), refund(退款), admin_adjust(管理员调整)
     */
    private String transactionType;

    /**
     * 变动金额（正数为增加，负数为减少）
     */
    private BigDecimal amount;

    /**
     * 变动后余额
     */
    private BigDecimal balanceAfter;

    /**
     * 来源类型：resource_download, admin_manual, system_reward等
     */
    private String sourceType;

    /**
     * 来源ID（资源ID等）
     */
    private Long sourceId;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
