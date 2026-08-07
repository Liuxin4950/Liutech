package chat.liuxin.liutech.model;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 用户浏览历史实体类
 * 同一用户浏览同一篇文章仅保留一条记录，重复浏览只刷新浏览时间（列表置顶）。
 *
 * @author 刘鑫
 * @date 2026-08-07
 */
@Data
@TableName("user_view_history")
public class UserViewHistory {

    /**
     * 浏览记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 文章ID
     */
    private Long postId;

    /**
     * 浏览时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date viewedAt;
}
