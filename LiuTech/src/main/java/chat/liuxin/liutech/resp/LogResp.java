package chat.liuxin.liutech.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作日志响应类
 *
 * @author 刘鑫
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogResp {

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 操作类型
     */
    private String action;

    /**
     * 操作目标
     */
    private String target;

    /**
     * 操作描述
     */
    private String description;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 操作状态（成功/失败）
     */
    private String status;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 详细信息（JSON格式）
     */
    private String detail;
}
