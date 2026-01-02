package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.AdminLogsMapper;
import chat.liuxin.liutech.model.AdminLogs;

import lombok.extern.slf4j.Slf4j;

/**
 * 操作日志服务
 */
@Slf4j
@Service
public class LogService extends ServiceImpl<AdminLogsMapper, AdminLogs> {

    @Autowired
    private AdminLogsMapper adminLogsMapper;

    /**
     * 保存日志
     *
     * @param log 日志对象
     * @return 是否保存成功
     */
    public boolean saveLog(AdminLogs adminLog) {
        try {
            log.info("保存操作日志: action={}, operator={}, targetName={}", adminLog.getAction(), adminLog.getOperator(), adminLog.getTargetName());
            boolean result = this.save(adminLog);
            log.info("操作日志保存结果: {}", result);
            return result;
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
            return false;
        }
    }

    /**
     * 分页查询日志列表（带筛选条件）
     *
     * @param page       页码
     * @param size       每页大小
     * @param operator   操作人用户名（可选）
     * @param action     操作类型（可选）
     * @param targetType 目标类型（可选）
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @param status     状态（可选）
     * @return 分页结果
     */
    public IPage<AdminLogs> getLogList(int page, int size, String operator, String action,
            String targetType, String startTime, String endTime, Integer status) {
        Page<AdminLogs> pageParam = new Page<>(page, size);
        return adminLogsMapper.selectLogList(pageParam, operator, action, targetType, startTime, endTime, status);
    }

    /**
     * 统计各操作类型的数量
     *
     * @return 操作类型及数量列表
     */
    public List<Map<String, Object>> countByAction() {
        return adminLogsMapper.countByAction();
    }

    /**
     * 统计指定时间范围内的日志数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 日志数量
     */
    public Integer countByTimeRange(String startTime, String endTime) {
        return adminLogsMapper.countByTimeRange(startTime, endTime);
    }

    /**
     * 清理旧日志
     *
     * @param days 天数，删除多少天之前的日志
     * @return 影响的行数
     */
    public int cleanupOldLogs(int days) {
        return adminLogsMapper.deleteLogsOlderThanDays(days);
    }

    /**
     * 根据操作人ID查询日志数量
     *
     * @param operatorId 操作人ID
     * @return 日志数量
     */
    public Integer countByOperatorId(Long operatorId) {
        return adminLogsMapper.countByOperatorId(operatorId);
    }
}
