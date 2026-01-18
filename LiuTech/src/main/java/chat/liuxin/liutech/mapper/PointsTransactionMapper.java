package chat.liuxin.liutech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import chat.liuxin.liutech.model.PointsTransaction;

/**
 * 积分流水 Mapper 接口
 *
 * @author 刘鑫
 * @date 2025-01-18
 */
@Mapper
public interface PointsTransactionMapper extends BaseMapper<PointsTransaction> {
    // 基础CRUD由MyBatis-Plus提供
}
