package chat.liuxin.liutech.resp;

import java.util.Date;

import lombok.Data;

/**
 * 公告响应数据
 * @author liuxin
 */
@Data
public class AnnouncementResp {
    /**
     * 公告ID
     */
    private Long id;

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
     * 公告类型名称
     */
    private String typeName;

    /**
     * 优先级(1低,2中,3高,4紧急)
     */
    private Integer priority;

    /**
     * 优先级名称
     */
    private String priorityName;

    /**
     * 状态(0草稿,1发布,2下线)
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

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

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 是否有效（在有效期内）
     */
    private Boolean isValid;
}
