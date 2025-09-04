package chat.liuxin.liutech.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 用户签到记录实体类
 * 
 * @author 刘鑫
 * @date 2025-01-04
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_checkins")
public class UserCheckin implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 签到日期
     */
    @TableField("checkin_date")
    private LocalDate checkinDate;

    /**
     * 获得积分
     */
    @TableField("points_earned")
    private BigDecimal pointsEarned;

    /**
     * 连续签到天数
     */
    @TableField("consecutive_days")
    private Integer consecutiveDays;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Date createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}