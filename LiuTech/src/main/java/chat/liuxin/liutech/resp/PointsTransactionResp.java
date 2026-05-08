package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.PointsTransaction;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分流水响应DTO
 * 继承 PointsTransaction 并添加用户名字段，用于管理端查询展示
 *
 * @author 刘鑫
 * @date 2025-01-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PointsTransactionResp extends PointsTransaction {

    /**
     * 用户名（关联 users 表）
     */
    private String username;
}
