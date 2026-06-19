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
 * 管理端资源控制器
 * 需要管理员权限才能访问
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

    /**
     * 分页查询资源列表
     */
    @GetMapping
    public Result<PageResp<ResourceResp>> getResourceList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Integer downloadType,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        try {
            PageResp<ResourceResp> result = resourcesAdminService.getResourceListForAdmin(
                    page, size, name, resourceType, downloadType, includeDeleted);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询资源列表");
        }
    }

    /**
     * 根据ID查询资源详情
     */
    @GetMapping("/{id}")
    public Result<ResourceResp> getResourceById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "资源ID");
        try {
            ResourceResp resource = resourcesAdminService.getResourceById(id);
            return checkResourceExists(resource, ErrorCode.NOT_FOUND);
        } catch (Exception e) {
            return handleException(e, "查询资源详情");
        }
    }

    /**
     * 创建资源
     */
    @PostMapping
    @OperationLog(action = "create", targetType = "resource", description = "创建资源: #resource.name", targetName = "#resource.name")
    public Result<String> createResource(@RequestBody ResourceResp resource) {
        ValidationUtil.validateNotNull(resource, "资源信息");
        try {
            boolean success = resourcesAdminService.createResource(resource);
            return handleOperationResult(success, "资源创建成功", "资源创建");
        } catch (Exception e) {
            return handleException(e, "资源创建");
        }
    }

    /**
     * 更新资源
     */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "resource", description = "更新资源: #resource.name", targetName = "#resource.name")
    public Result<String> updateResource(@PathVariable Long id, @RequestBody ResourceResp resource) {
        ValidationUtil.validateId(id, "资源ID");
        ValidationUtil.validateNotNull(resource, "资源信息");
        try {
            resource.setId(id);
            boolean success = resourcesAdminService.updateResource(resource);
            return handleOperationResult(success, "资源更新成功", "资源更新");
        } catch (Exception e) {
            return handleException(e, "资源更新");
        }
    }

    /**
     * 删除资源（软删除）
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "resource", description = "删除资源", targetName = "#id")
    public Result<String> deleteResource(@PathVariable Long id) {
        ValidationUtil.validateId(id, "资源ID");
        try {
            boolean success = resourcesAdminService.removeByIds(List.of(id));
            return handleOperationResult(success, "资源删除成功", "资源删除");
        } catch (Exception e) {
            return handleException(e, "资源删除");
        }
    }

    /**
     * 批量删除资源（软删除）
     */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "resource", description = "批量删除资源")
    public Result<String> batchDeleteResources(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "资源ID列表");
        try {
            boolean success = resourcesAdminService.removeByIds(ids);
            return handleOperationResult(success, "批量删除资源成功", "批量删除资源");
        } catch (Exception e) {
            return handleException(e, "批量删除资源");
        }
    }

    /**
     * 恢复已删除的资源
     */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "resource", description = "恢复资源", targetName = "#id")
    public Result<String> restoreResource(@PathVariable Long id) {
        ValidationUtil.validateId(id, "资源ID");
        try {
            boolean success = resourcesAdminService.restoreResource(id);
            return handleOperationResult(success, "资源恢复成功", "资源恢复");
        } catch (Exception e) {
            return handleException(e, "资源恢复");
        }
    }

    /**
     * 彻底删除资源（物理删除）
     */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "resource", description = "彻底删除资源", targetName = "#id")
    public Result<String> permanentDeleteResource(@PathVariable Long id) {
        ValidationUtil.validateId(id, "资源ID");
        try {
            boolean success = resourcesAdminService.permanentDeleteResource(id);
            return handleOperationResult(success, "资源彻底删除成功", "资源彻底删除");
        } catch (Exception e) {
            return handleException(e, "资源彻底删除");
        }
    }

    /**
     * 批量彻底删除资源（物理删除）
     */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "resource", description = "批量彻底删除资源", targetName = "#ids")
    public Result<String> batchPermanentDeleteResources(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "资源ID列表");
        try {
            boolean success = resourcesAdminService.batchPermanentDeleteResources(ids);
            return handleOperationResult(success, "批量彻底删除资源成功", "批量彻底删除资源");
        } catch (Exception e) {
            return handleException(e, "批量彻底删除资源");
        }
    }

    /**
     * 分页查询下载记录
     */
    @GetMapping("/downloads")
    public Result<PageResp<DownloadLogResp>> getDownloadLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long resourceId) {
        try {
            PageResp<DownloadLogResp> result = resourcesAdminService.getDownloadLogsForAdmin(
                    page, size, userId, resourceId);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询下载记录");
        }
    }
}
