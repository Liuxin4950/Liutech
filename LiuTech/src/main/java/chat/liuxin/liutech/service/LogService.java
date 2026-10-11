package chat.liuxin.liutech.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import chat.liuxin.liutech.common.PageQuery;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.mapper.AdminLogsMapper;
import chat.liuxin.liutech.model.AdminLogs;
import chat.liuxin.liutech.resp.LogSummaryResp;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.req.AuditOperationReq;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 操作日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogService extends ServiceImpl<AdminLogsMapper, AdminLogs> {

    private static final java.time.ZoneId AUDIT_ZONE = java.time.ZoneId.of("Asia/Shanghai");

    private final AdminLogsMapper adminLogsMapper;

    public List<String> getActionTypes() { return adminLogsMapper.selectActionTypes(); }
    public List<String> getTargetTypes() { return adminLogsMapper.selectTargetTypes(); }

    /**
     * 保存日志
     *
     * @param adminLog 日志对象
     * @return 是否保存成功
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveLog(AdminLogs adminLog) {
        try {
            log.debug("保存操作日志: action={}, operator={}, targetName={}", adminLog.getAction(), adminLog.getOperator(), adminLog.getTargetName());
            boolean result = this.save(adminLog);
            log.debug("操作日志保存结果: {}", result);
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
        Page<AdminLogs> pageParam = PageQuery.of(page, size).toPage();
        Long start = timeBoundary(startTime, false), end = timeBoundary(endTime, true);
        validateTimeRange(start, end);
        return adminLogsMapper.selectLogList(pageParam, operator, action, targetType, start, end, status);
    }

    public AdminLogs getLogDetail(Long id) { return adminLogsMapper.selectLogDetail(id); }

    public LogSummaryResp getSummary(String operator, String action, String targetType,
            String startTime, String endTime, Integer status) {
        Long start = timeBoundary(startTime, false), end = timeBoundary(endTime, true);
        validateTimeRange(start, end);
        var today = java.time.LocalDate.now(AUDIT_ZONE);
        return adminLogsMapper.selectSummary(operator, action, targetType, start, end, status,
                today.atStartOfDay(AUDIT_ZONE).toEpochSecond(), today.plusDays(1).atStartOfDay(AUDIT_ZONE).toEpochSecond());
    }

    private Long timeBoundary(String value, boolean end) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() == 10) {
                var date = java.time.LocalDate.parse(value);
                return (end ? date.plusDays(1).atStartOfDay(AUDIT_ZONE).minusSeconds(1) : date.atStartOfDay(AUDIT_ZONE)).toEpochSecond();
            }
            return java.time.LocalDateTime.parse(value.replace(' ', 'T')).atZone(AUDIT_ZONE).toEpochSecond();
        } catch (java.time.DateTimeException error) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "日志时间格式不正确");
        }
    }

    private void validateTimeRange(Long start, Long end) {
        if (start != null && end != null && start > end) throw new BusinessException(ErrorCode.PARAMS_ERROR, "开始时间不能晚于结束时间");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acceptAiOperation(AuditOperationReq request) {
        // 方法自身也校验可信身份，不能仅依赖 MVC 参数校验。
        if (!"admin".equals(request.operatorRole()) || request.operatorId() == null || request.operatorId() <= 0
                || request.operator() == null || request.operator().isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "审计身份无效");
        }
        String path = request.path();
        if (path == null || !path.matches("(?:/ai/admin|/admin)/[a-zA-Z0-9/_-]{1,220}")
                || !java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.method())
                || request.status() == null || (request.status() != 0 && request.status() != 1)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审计操作无效");
        }
        String relative = path.replaceFirst("^/(?:ai/)?admin/", "");
        String domain = relative.split("/")[0];
        String target = switch (domain) {
            case "models" -> "ai_model";
            case "tts" -> "tts";
            case "community" -> "ai_community";
            default -> "ai_" + domain;
        };
        String label = switch (domain) {
            case "models" -> "AI 模型";
            case "tts" -> "语音服务";
            case "community" -> "AI 社区互动";
            default -> "AI 管理数据";
        };
        String action = switch (request.method()) {
            case "DELETE" -> "delete";
            case "PUT", "PATCH" -> "update";
            default -> "create";
        };
        String description = switch (action) {
            case "delete" -> "删除" + label;
            case "update" -> "更新" + label;
            default -> "创建" + label;
        };
        if (path.endsWith("/preview")) { action = "test"; description = "预览社区 AI 回复"; }
        else if (path.endsWith("/retry")) { action = "retry"; description = "重试社区 AI 任务"; }
        else if (path.endsWith("/cancel")) { action = "cancel"; description = "取消社区 AI 任务"; }
        else if (relative.startsWith("community/memory/")) { description = "清空社区 AI 记忆"; }
        else if (path.endsWith("/test-speech")) { action = "test"; description = "测试语音合成"; }
        else if (path.endsWith("/voice")) { action = "upload"; description = "上传语音音色"; }
        else if (path.endsWith("/default")) { description = "设置默认 AI 模型"; }
        else if (path.endsWith("/toggle")) { description = "切换 AI 模型启用状态"; }
        AdminLogs entry = new AdminLogs();
        entry.setOperatorId(request.operatorId());
        entry.setOperator(request.operator());
        entry.setTargetType(target);
        entry.setAction(action);
        entry.setDescription(description);
        java.util.regex.Matcher id = java.util.regex.Pattern.compile("/(\\d+|[0-9a-fA-F-]{36})(?:/|$)").matcher(relative);
        if (id.find()) entry.setTargetName(id.group(1));
        entry.setStatus(request.status());
        entry.setIp(request.ip());
        entry.setUserAgent(request.userAgent());
        if (request.status() == 0) entry.setErrorMessage("AI 操作未完成（错误码 "
                + (request.failureCode() == null ? 500 : request.failureCode()) + "）");
        if (!saveLog(entry)) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "审计保存失败");
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
