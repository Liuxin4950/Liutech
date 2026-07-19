package chat.liuxin.liutech.controller.admin;

import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.SystemSetting;
import chat.liuxin.liutech.service.SystemSettingsAdminService;

/**
 * 系统设置管理控制器（管理端，类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
 */
@Slf4j
@RestController
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SystemSettingsAdminController extends BaseAdminController {

    private final SystemSettingsAdminService settingsAdminService;

    /** 获取所有系统设置 */
    @GetMapping
    public Result<List<SystemSetting>> listAll() {
        return Result.success(settingsAdminService.listAll());
    }

    /** 根据 key 获取单个设置 */
    @GetMapping("/{key}")
    public Result<SystemSetting> getByKey(@PathVariable String key) {
        SystemSetting setting = settingsAdminService.getByKey(key);
        if (setting == null) {
            return Result.fail(ErrorCode.NOT_FOUND, "设置项不存在: " + key);
        }
        return Result.success(setting);
    }

    /** 更新单个设置（请求体: { "value": "新值", "description": "可选" }） */
    @OperationLog(action = "update", targetType = "system_setting", description = "更新系统设置", targetName = "#key")
    @PutMapping("/{key}")
    public Result<Boolean> updateByKey(@PathVariable String key, @RequestBody Map<String, String> body) {
        settingsAdminService.updateByKey(key, body.get("value"), body.get("description"));
        return Result.success(true);
    }

    /** 批量更新设置 */
    @OperationLog(action = "update", targetType = "system_setting", description = "批量更新系统设置")
    @PostMapping("/batch")
    public Result<Boolean> batchUpdate(@RequestBody List<Map<String, String>> settings) {
        settingsAdminService.batchUpdate(settings);
        return Result.success(true);
    }

    /** 按分组获取设置（用于前端分组展示） */
    @GetMapping("/grouped")
    public Result<Map<String, List<SystemSetting>>> getGrouped() {
        return Result.success(settingsAdminService.getGroupedSettings());
    }
}
