package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.AdminLogs;
import chat.liuxin.liutech.resp.LogResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.LogService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端操作日志控制器
 * 提供系统操作日志查询接口
 */
@RestController
@RequestMapping("/admin/logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
 extends BaseAdminController {

    private final LogService logService;

    /**
     * 分页查询操作日志列表
     *
     * @param page       页码，默认1
     * @param size       每页大小，默认10
     * @param operator   操作人（可选）
     * @param action     操作类型（可选）
     * @param targetType 目标类型（可选）
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @param status     状态（可选，1成功，0失败）
     * @return 分页操作日志列表
     */
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

        try {
            // 验证分页参数
            if (page < 1) page = 1;
            if (size < 1) size = 10;
            if (size > 100) size = 100;

            // 查询真实日志数据
            var logPage = logService.getLogList(page, size, operator, action, targetType, startTime, endTime, status);

            // 转换为响应对象
            List<LogResp> logs = logPage.getRecords().stream()
                    .map(this::toLogResp)
                    .collect(Collectors.toList());

            // 构建分页结果
            PageResp<LogResp> result = new PageResp<>();
            result.setRecords(logs);
            result.setTotal(logPage.getTotal());
            result.setCurrent(logPage.getCurrent());
            result.setSize(logPage.getSize());
            result.setPages(logPage.getPages());
            result.setHasNext(logPage.getCurrent() < logPage.getPages());
            result.setHasPrevious(logPage.getCurrent() > 1);

            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询操作日志列表");
        }
    }

    /**
     * 获取日志详情
     *
     * @param id 日志ID
     * @return 日志详情
     */
    @GetMapping("/{id}")
    public Result<LogResp> getLogById(@PathVariable Long id) {
        try {
            AdminLogs log = logService.getById(id);
            if (log == null) {
                return Result.fail(404, "日志不存在");
            }
            return Result.success(toLogResp(log));
        } catch (Exception e) {
            return handleException(e, "查询日志详情");
        }
    }

    /**
     * 获取操作类型统计
     *
     * @return 操作类型及数量列表
     */
    @GetMapping("/action-stats")
    public Result<List<Map<String, Object>>> getActionStats() {
        try {
            List<Map<String, Object>> stats = logService.countByAction();
            return Result.success(stats);
        } catch (Exception e) {
            return handleException(e, "获取操作类型统计");
        }
    }

    /**
     * 获取操作类型列表
     *
     * @return 操作类型列表
     */
    @GetMapping("/actions")
    public Result<List<String>> getActionTypes() {
        List<String> actions = new ArrayList<>();
        actions.add("login");
        actions.add("create");
        actions.add("update");
        actions.add("delete");
        actions.add("restore");
        actions.add("publish");
        actions.add("offline");
        actions.add("enable");
        actions.add("disable");
        actions.add("upload");
        actions.add("download");
        actions.add("review");
        actions.add("reply");
        actions.add("purchase");
        actions.add("checkin");
        actions.add("export");
        actions.add("import");
        actions.add("test");
        return Result.success(actions);
    }

    /**
     * 获取目标类型列表
     *
     * @return 目标类型列表
     */
    @GetMapping("/target-types")
    public Result<List<String>> getTargetTypes() {
        List<String> targetTypes = new ArrayList<>();
        targetTypes.add("post");
        targetTypes.add("user");
        targetTypes.add("category");
        targetTypes.add("tag");
        targetTypes.add("announcement");
        targetTypes.add("comment");
        targetTypes.add("resource");
        targetTypes.add("music");
        targetTypes.add("image");
        targetTypes.add("document");
        targetTypes.add("attachment");
        targetTypes.add("message");
        targetTypes.add("carousel");
        targetTypes.add("tts");
        targetTypes.add("system_setting");
        targetTypes.add("points");
        return Result.success(targetTypes);
    }

    /**
     * AdminLogs 转换为 LogResp
     */
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
