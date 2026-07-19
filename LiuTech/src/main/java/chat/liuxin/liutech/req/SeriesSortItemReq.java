package chat.liuxin.liutech.req;

import lombok.Data;

/**
 * 系列内文章排序项（拖拽排序批量更新用）
 *
 * 替代原先 Controller 接收 List<Map<String,Object>> 后在 Service 层手动类型转换。
 *
 * @author 刘鑫
 */
@Data
public class SeriesSortItemReq {
    /** 文章ID */
    private Long postId;
    /** 系列内排序值（升序，越小越靠前） */
    private Integer seriesSort;
}
