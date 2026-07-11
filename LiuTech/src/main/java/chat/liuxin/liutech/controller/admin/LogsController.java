package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.AdminLogs;
import chat.liuxin.liutech.resp.LogResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.LogService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端操作日志控制器（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
 */
@RestController
@RequestMapping("/admin/logs")
@PreAuthorize("hasRole('ADMIN')")
public class LogsController extends BaseAdminController {

    @Autowired
    private LogService logService;

    /** 分页查询操作日志列表 */
    @GetMapping
    public Result<PageResp<LogResp>> getLogList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer status) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;

        var logPage = logService.getLogList(page, size, operator, action, targetType, startTime, endTime, status);
        List<LogResp> logs = logPage.getRecords().stream()
                .map(this::toLogResp)
                .collect(Collectors.toList());

        PageResp<LogResp> result = new PageResp<>();
        result.setRecords(logs);
        result.setTotal(logPage.getTotal());
        result.setCurrent(logPage.getCurrent());
        result.setSize(logPage.getSize());
        result.setPages(logPage.getPages());
        result.setHasNext(logPage.getCurrent() < logPage.getPages());
        result.setHasPrevious(logPage.getCurrent() > 1);
        return Result.success(result);
    }

    /** 获取日志详情 */
    @GetMapping("/{id}")
    public Result<LogResp> getLogById(@PathVariable Long id) {
        AdminLogs log = logService.getById(id);
        if (log == null) {
            return Result.fail(404, "日志不存在");
        }
        return Result.success(toLogResp(log));
    }

    /** 获取操作类型统计 */
    @GetMapping("/action-stats")
    public Result<List<Map<String, Object>>> getActionStats() {
        return Result.success(logService.countByAction());
    }

    /** 获取操作类型列表 */
    @GetMapping("/actions")
    public Result<List<String>> getActionTypes() {
        return Result.success(List.of(
                "login", "create", "update", "delete", "restore", "publish", "offline",
                "enable", "disable", "upload", "download", "review", "reply",
                "purchase", "checkin", "export", "import", "test"));
    }

    /** 获取目标类型列表 */
    @GetMapping("/target-types")
    public Result<List<String>> getTargetTypes() {
        return Result.success(List.of(
                "post", "user", "category", "tag", "announcement", "comment", "resource",
                "music", "image", "document", "attachment", "message", "carousel",
                "tts", "system_setting", "points"));
    }

    /** AdminLogs 转换为 LogResp */
    private LogResp toLogResp(AdminLogs log) {
        return LogResp.builder()
                .id(log.getId())
                .operator(log.getOperator())
                .action(log.getAction())
                .target(log.getTargetType())
                .description(log.getDescription())
                .ip(log.getIp())
                .status(log.getStatus() != null && log.getStatus() == 1 ? "成功" : "失败")
                .createdAt(log.getCreatedAt() != null ? log.getCreatedAt().toString() : null)
                .detail(log.getErrorMessage())
                .build();
    }
}
