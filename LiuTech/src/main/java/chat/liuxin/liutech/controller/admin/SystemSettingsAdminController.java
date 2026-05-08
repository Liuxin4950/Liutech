package chat.liuxin.liutech.controller.admin;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
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
 * 系统设置管理控制器（管理端）
 *
 * 提供系统配置的 CRUD 和分组查询接口，仅管理员可访问。
 */
@Slf4j
@RestController
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SystemSettingsAdminController extends BaseAdminController {

    @Autowired
    private SystemSettingsAdminService settingsAdminService;

    /**
     * 获取所有系统设置
     */
    @GetMapping
    public Result<List<SystemSetting>> listAll() {
        try {
            return Result.success(settingsAdminService.listAll());
        } catch (Exception e) {
            return handleException(e, "查询系统设置");
        }
    }

    /**
     * 根据 key 获取单个设置
     */
    @GetMapping("/{key}")
    public Result<SystemSetting> getByKey(@PathVariable String key) {
        try {
            SystemSetting setting = settingsAdminService.getByKey(key);
            if (setting == null) {
                return Result.fail(ErrorCode.NOT_FOUND, "设置项不存在: " + key);
            }
            return Result.success(setting);
        } catch (Exception e) {
            return handleException(e, "查询系统设置");
        }
    }

    /**
     * 更新单个设置
     *
     * 请求体: { "value": "新值", "description": "可选新描述" }
     */
    @OperationLog(action = "update", targetType = "system_setting", description = "更新系统设置", targetName = "#key")
    @PutMapping("/{key}")
    public Result<Boolean> updateByKey(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        try {
            String value = body.get("value");
            String description = body.get("description");
            settingsAdminService.updateByKey(key, value, description);
            return Result.success(true);
        } catch (Exception e) {
            return handleException(e, "更新系统设置");
        }
    }

    /**
     * 批量更新设置
     *
     * 请求体: [ { "key": "site.name", "value": "新名称", "description": "可选" }, ... ]
     */
    @OperationLog(action = "update", targetType = "system_setting", description = "批量更新系统设置")
    @PostMapping("/batch")
    public Result<Boolean> batchUpdate(@RequestBody List<Map<String, String>> settings) {
        try {
            settingsAdminService.batchUpdate(settings);
            return Result.success(true);
        } catch (Exception e) {
            return handleException(e, "批量更新系统设置");
        }
    }

    /**
     * 按分组获取设置（用于前端分组展示）
     *
     * 返回: { "site": [...], "comment": [...], ... }
     */
    @GetMapping("/grouped")
    public Result<Map<String, List<SystemSetting>>> getGrouped() {
        try {
            return Result.success(settingsAdminService.getGroupedSettings());
        } catch (Exception e) {
            return handleException(e, "查询系统设置分组");
        }
    }
}
