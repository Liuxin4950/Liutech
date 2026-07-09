package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.DownloadLogResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.resp.ResourceResp;
import chat.liuxin.liutech.service.ResourcesAdminService;
import chat.liuxin.liutech.utils.ValidationUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端资源控制器（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
 *
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequestMapping("/admin/resources")
@PreAuthorize("hasRole('ADMIN')")
public class ResourcesAdminController extends BaseAdminController {

    @Autowired
    private ResourcesAdminService resourcesAdminService;

    /** 分页查询资源列表 */
    @GetMapping
    public Result<PageResp<ResourceResp>> getResourceList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Integer downloadType,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        return Result.success(resourcesAdminService.getResourceListForAdmin(page, size, name, resourceType, downloadType, includeDeleted));
    }

    /** 根据ID查询资源详情 */
    @GetMapping("/{id}")
    public Result<ResourceResp> getResourceById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "资源ID");
        return checkResourceExists(resourcesAdminService.getResourceById(id), ErrorCode.NOT_FOUND);
    }

    /** 创建资源 */
    @PostMapping
    @OperationLog(action = "create", targetType = "resource", description = "创建资源: #resource.name", targetName = "#resource.name")
    public Result<String> createResource(@RequestBody ResourceResp resource) {
        ValidationUtil.validateNotNull(resource, "资源信息");
        return handleOperationResult(resourcesAdminService.createResource(resource), "资源创建成功", "资源创建");
    }

    /** 更新资源 */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "resource", description = "更新资源: #resource.name", targetName = "#resource.name")
    public Result<String> updateResource(@PathVariable Long id, @RequestBody ResourceResp resource) {
        ValidationUtil.validateId(id, "资源ID");
        ValidationUtil.validateNotNull(resource, "资源信息");
        resource.setId(id);
        return handleOperationResult(resourcesAdminService.updateResource(resource), "资源更新成功", "资源更新");
    }

    /** 删除资源（软删除） */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "resource", description = "删除资源", targetName = "#id")
    public Result<String> deleteResource(@PathVariable Long id) {
        ValidationUtil.validateId(id, "资源ID");
        return handleOperationResult(resourcesAdminService.removeByIds(List.of(id)), "资源删除成功", "资源删除");
    }

    /** 批量删除资源（软删除） */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "resource", description = "批量删除资源")
    public Result<String> batchDeleteResources(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "资源ID列表");
        return handleOperationResult(resourcesAdminService.removeByIds(ids), "批量删除资源成功", "批量删除资源");
    }

    /** 恢复已删除的资源 */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "resource", description = "恢复资源", targetName = "#id")
    public Result<String> restoreResource(@PathVariable Long id) {
        ValidationUtil.validateId(id, "资源ID");
        return handleOperationResult(resourcesAdminService.restoreResource(id), "资源恢复成功", "资源恢复");
    }

    /** 彻底删除资源（物理删除） */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "resource", description = "彻底删除资源", targetName = "#id")
    public Result<String> permanentDeleteResource(@PathVariable Long id) {
        ValidationUtil.validateId(id, "资源ID");
        return handleOperationResult(resourcesAdminService.permanentDeleteResource(id), "资源彻底删除成功", "资源彻底删除");
    }

    /** 批量彻底删除资源（物理删除） */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "resource", description = "批量彻底删除资源", targetName = "#ids")
    public Result<String> batchPermanentDeleteResources(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "资源ID列表");
        return handleOperationResult(resourcesAdminService.batchPermanentDeleteResources(ids), "批量彻底删除资源成功", "批量彻底删除资源");
    }

    /** 分页查询下载记录 */
    @GetMapping("/downloads")
    public Result<PageResp<DownloadLogResp>> getDownloadLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long resourceId) {
        return Result.success(resourcesAdminService.getDownloadLogsForAdmin(page, size, userId, resourceId));
    }
}
