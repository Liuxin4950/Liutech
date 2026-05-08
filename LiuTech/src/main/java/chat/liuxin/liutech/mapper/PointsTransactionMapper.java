package chat.liuxin.liutech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import chat.liuxin.liutech.model.PointsTransaction;
import chat.liuxin.liutech.resp.PointsTransactionResp;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 积分流水 Mapper 接口
 *
 * @author 刘鑫
 * @date 2025-01-18
 */
@Mapper
public interface PointsTransactionMapper extends BaseMapper<PointsTransaction> {

    /**
     * 管理端分页查询积分流水（关联用户名）
     *
     * @param offset          偏移量
     * @param limit           每页大小
     * @param userId          用户ID（可选）
     * @param transactionType 交易类型（可选）
     * @param startTime       开始时间（可选）
     * @param endTime         结束时间（可选）
     * @return 带用户名的积分流水列表
     */
    List<PointsTransactionResp> selectTransactionsForAdmin(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("userId") Long userId,
            @Param("transactionType") String transactionType,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime);

    /**
     * 管理端统计积分流水总数
     *
     * @param userId          用户ID（可选）
     * @param transactionType 交易类型（可选）
     * @param startTime       开始时间（可选）
     * @param endTime         结束时间（可选）
     * @return 总数
     */
    Long countTransactionsForAdmin(
            @Param("userId") Long userId,
            @Param("transactionType") String transactionType,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime);

    /**
     * 按多个交易类型求和（SQL 聚合，避免全量加载到内存）
     *
     * @param types 交易类型列表
     * @return 积分变动总额
     */
    BigDecimal sumPointsByTypes(@Param("types") List<String> types);

    /**
     * 按单个交易类型求和（SQL 聚合）
     *
     * @param type 交易类型
     * @return 积分变动总额
     */
    BigDecimal sumPointsByType(@Param("type") String type);

    /**
     * 查询所有用户的积分余额总和（SQL 聚合）
     *
     * @return 用户积分余额总和
     */
    BigDecimal sumTotalUserPoints();
}
