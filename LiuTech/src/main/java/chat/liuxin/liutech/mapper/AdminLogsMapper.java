package chat.liuxin.liutech.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import chat.liuxin.liutech.model.AdminLogs;

/**
 * 管理端操作日志Mapper接口
 */
@Mapper
public interface AdminLogsMapper extends BaseMapper<AdminLogs> {

    /**
     * 分页查询日志列表（带筛选条件）
     *
     * @param page        分页参数
     * @param operator    操作人用户名（可选）
     * @param action      操作类型（可选）
     * @param targetType  目标类型（可选）
     * @param startTime   开始时间（可选）
     * @param endTime     结束时间（可选）
     * @param status      状态（可选）
     * @return 日志列表
     */
    IPage<AdminLogs> selectLogList(Page<AdminLogs> page,
            @Param("operator") String operator,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("status") Integer status);

    /**
     * 根据操作人ID查询日志数量
     *
     * @param operatorId 操作人ID
     * @return 日志数量
     */
    Integer countByOperatorId(@Param("operatorId") Long operatorId);

    /**
     * 统计各操作类型的数量
     *
     * @return 操作类型及数量Map
     */
    List<Map<String, Object>> countByAction();

    /**
     * 统计指定时间范围内的日志数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 日志数量
     */
    Integer countByTimeRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

    /**
     * 删除指定天数之前的日志（清理旧日志）
     *
     * @param days 天数
     * @return 影响的行数
     */
    int deleteLogsOlderThanDays(@Param("days") Integer days);
}
