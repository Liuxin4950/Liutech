package chat.liuxin.liutech.resp;

import java.util.Date;

import lombok.Data;

/**
 * 轮播图响应数据
 * 控制返回给前端的字段
 * @author 刘鑫
 */
@Data
public class CarouselResp {
    /**
     * 轮播图ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 图片URL
     */
    private String imageUrl;

    /**
     * 跳转链接
     */
    private String linkUrl;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态(0禁用,1启用)
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 删除状态
     */
    private String deleteStatus;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 删除时间
     */
    private Date deletedAt;
}
