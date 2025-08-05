package chat.liuxin.liutech.req;

import java.util.Date;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 公告请求数据
 * @author liuxin
 */
@Data
public class AnnouncementReq {
    /**
     * 公告ID（更新时需要）
     */
    private Long id;

    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 255, message = "公告标题长度不能超过255个字符")
    private String title;

    /**
     * 公告内容
     */
    @NotBlank(message = "公告内容不能为空")
    private String content;

    /**
     * 公告类型(1系统,2活动,3维护,4其他)
     */
    @NotNull(message = "公告类型不能为空")
    private Integer type;

    /**
     * 优先级(1低,2中,3高,4紧急)
     */
    @NotNull(message = "优先级不能为空")
    private Integer priority;

    /**
     * 状态(0草稿,1发布,2下线)
     */
    @NotNull(message = "状态不能为空")
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
}