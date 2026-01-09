package chat.liuxin.liutech.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 轮播图请求数据
 * @author liuxin
 */
@Data
public class CarouselReq {
    /**
     * 轮播图ID（更新时需要）
     */
    private Long id;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过255个字符")
    private String title;

    /**
     * 图片URL
     */
    @NotBlank(message = "图片URL不能为空")
    @Size(max = 512, message = "图片URL长度不能超过512个字符")
    private String imageUrl;

    /**
     * 跳转链接
     */
    @Size(max = 512, message = "跳转链接长度不能超过512个字符")
    private String linkUrl;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态(0禁用,1启用)
     */
    private Integer status;
}
