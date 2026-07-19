package chat.liuxin.liutech.controller.admin;

import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.resp.ImageUsageReconcileResp;
import chat.liuxin.liutech.resp.PageResp;
import chat.liuxin.liutech.service.ImageUsageReconcileService;
import chat.liuxin.liutech.service.ImagesAdminService;
import chat.liuxin.liutech.utils.ValidationUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理端图片控制器（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
 *
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequestMapping("/admin/images")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ImagesAdminController extends BaseAdminController {

    private final ImageUsageReconcileService imageUsageReconcileService;

    private final ImagesAdminService imagesAdminService;

    /** 图片引用对账 */
    @PostMapping("/reconcile-usage")
    public Result<ImageUsageReconcileResp> reconcileUsageCount() {
        return Result.success(imageUsageReconcileService.reconcileUsageCount());
    }

    /** 分页查询图片列表 */
    @GetMapping
    public Result<PageResp<Images>> getImageList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        return Result.success(imagesAdminService.getImageListForAdmin(page, size, fileName, mimeType, status, includeDeleted));
    }

    /** 根据ID查询图片详情 */
    @GetMapping("/{id}")
    public Result<Images> getImageById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "图片ID");
        return checkResourceExists(imagesAdminService.getImageById(id), ErrorCode.NOT_FOUND);
    }

    /** 软删除图片 */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "image", description = "软删除图片", targetName = "#id")
    public Result<Map<String, Object>> softDeleteImage(@PathVariable Long id) {
        ValidationUtil.validateId(id, "图片ID");
        return Result.success(imagesAdminService.softDeleteImage(id));
    }

    /** 批量软删除图片 */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "image", description = "批量软删除图片")
    public Result<Map<String, Object>> batchSoftDeleteImages(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "图片ID列表");
        return Result.success(imagesAdminService.batchSoftDeleteImages(ids));
    }

    /** 恢复已删除的图片 */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "image", description = "恢复图片", targetName = "#id")
    public Result<String> restoreImage(@PathVariable Long id) {
        ValidationUtil.validateId(id, "图片ID");
        return handleOperationResult(imagesAdminService.restoreImage(id), "图片恢复成功", "图片恢复");
    }

    /** 物理删除图片（同时删除文件系统中的文件） */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "image", description = "彻底删除图片", targetName = "#id")
    public Result<String> permanentDeleteImage(@PathVariable Long id) {
        ValidationUtil.validateId(id, "图片ID");
        return handleOperationResult(imagesAdminService.permanentDeleteImage(id), "图片彻底删除成功", "图片彻底删除");
    }

    /** 批量物理删除图片 */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "image", description = "批量彻底删除图片")
    public Result<String> batchPermanentDeleteImages(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "图片ID列表");
        return handleOperationResult(imagesAdminService.batchPermanentDeleteImages(ids), "批量彻底删除图片成功", "批量彻底删除图片");
    }

    /** 查询孤立图片（usage_count = 0） */
    @GetMapping("/orphans")
    public Result<List<Images>> getOrphanImages() {
        return Result.success(imagesAdminService.getOrphanImages());
    }

    /** 清理孤立图片 */
    @PostMapping("/cleanup-orphans")
    @OperationLog(action = "delete", targetType = "image", description = "清理孤立图片")
    public Result<Integer> cleanupOrphanImages() {
        return Result.success(imagesAdminService.cleanupOrphanImages());
    }
}
