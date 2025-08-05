package chat.liuxin.liutech.model;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公告表
 * @TableName announcements
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("announcements")
public class Announcements extends BaseEntity {
    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容
     */
    private String content;

    /**
     * 公告类型(1系统,2活动,3维护,4其他)
     */
    private Integer type;

    /**
     * 优先级(1低,2中,3高,4紧急)
     */
    private Integer priority;

    /**
     * 状态(0草稿,1发布,2下线)
     */
    private Integer status;

    /**
     * 开始显示时间
     */
    private Date startTime;

    /**
     * 结束显示时间
     */
    private Date endTime;

    /**
     * 是否置顶(0否,1是)
     */
    private Integer isTop;

    /**
     * 查看次数
     */
    private Integer viewCount;
}