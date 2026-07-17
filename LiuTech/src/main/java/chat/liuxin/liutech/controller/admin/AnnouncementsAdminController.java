package chat.liuxin.liutech.controller.admin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.metadata.IPage;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.AnnouncementReq;
import chat.liuxin.liutech.resp.AnnouncementResp;
import chat.liuxin.liutech.service.AnnouncementImportService;
import chat.liuxin.liutech.service.AnnouncementsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * 管理端公告控制器
 * 类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底，方法内不再 try-catch。
 * @author 刘鑫
 */
@RestController
@RequestMapping("/admin/announcements")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AnnouncementsAdminController {

    private final AnnouncementsService announcementsService;
    private final AnnouncementImportService announcementImportService;

    /** 管理员分页查询所有公告 */
    @GetMapping
    public Result<IPage<AnnouncementResp>> getAllAnnouncements(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        return Result.success(announcementsService.getAllAnnouncements(current, size, status, type, keyword, includeDeleted));
    }

    /** 管理员根据ID获取公告详情（不限状态） */
    @GetMapping("/{id}")
    public Result<AnnouncementResp> getAnnouncementByIdForAdmin(@PathVariable Long id) {
        return Result.success(announcementsService.getAnnouncementByIdForAdmin(id));
    }

    /** 创建公告 */
    @PostMapping
    @OperationLog(action = "create", targetType = "announcement", description = "创建公告")
    public Result<Long> createAnnouncement(@Valid @RequestBody AnnouncementReq req) {
        return Result.success(announcementsService.createAnnouncement(req));
    }

    /** 更新公告 */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "announcement", description = "更新公告")
    public Result<Boolean> updateAnnouncement(@PathVariable Long id, @Valid @RequestBody AnnouncementReq req) {
        req.setId(id);
        return Result.success(announcementsService.updateAnnouncement(req));
    }

    /** 删除公告 */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "announcement", description = "删除公告")
    public Result<Boolean> deleteAnnouncement(@PathVariable Long id) {
        return Result.success(announcementsService.deleteAnnouncement(id));
    }

    /** 批量删除公告 */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "announcement", description = "批量删除公告")
    public Result<Boolean> batchDeleteAnnouncements(@RequestBody List<Long> ids) {
        return Result.success(announcementsService.batchDeleteAnnouncements(ids));
    }

    /** 物理删除单条公告（彻底删除，不可恢复） */
    @DeleteMapping("/{id}/permanent")
    @OperationLog(action = "delete", targetType = "announcement", description = "物理删除公告")
    public Result<Boolean> permanentDeleteAnnouncement(@PathVariable Long id) {
        return Result.success(announcementsService.permanentDeleteAnnouncement(id));
    }

    /** 批量物理删除公告（彻底删除，不可恢复） */
    @PostMapping("/batch/permanent")
    @OperationLog(action = "delete", targetType = "announcement", description = "批量物理删除公告")
    public Result<Boolean> batchPermanentDeleteAnnouncements(@RequestBody List<Long> ids) {
        return Result.success(announcementsService.batchPermanentDeleteAnnouncements(ids));
    }

    /** 更新公告状态 */
    @PutMapping("/{id}/status")
    @OperationLog(action = "update", targetType = "announcement", description = "更新公告状态")
    public Result<Boolean> updateAnnouncementStatus(@PathVariable Long id, @RequestBody AnnouncementStatusUpdateReq request) {
        return Result.success(announcementsService.updateAnnouncementStatus(id, request.getStatus()));
    }

    /** 批量更新公告状态 */
    @PutMapping("/batch/status")
    @OperationLog(action = "update", targetType = "announcement", description = "批量更新公告状态")
    public Result<Boolean> batchUpdateAnnouncementStatus(@RequestBody AnnouncementBatchStatusReq request) {
        return Result.success(announcementsService.batchUpdateAnnouncementStatus(request.getIds(), request.getStatus()));
    }

    /** 恢复已删除的公告 */
    @PutMapping("/{id}/restore")
    @OperationLog(action = "restore", targetType = "announcement", description = "恢复公告")
    public Result<Boolean> restoreAnnouncement(@PathVariable Long id) {
        return Result.success(announcementsService.restoreAnnouncement(id));
    }

    /** 置顶/取消置顶公告 */
    @PutMapping("/{id}/top")
    @OperationLog(action = "update", targetType = "announcement", description = "切换公告置顶")
    public Result<Boolean> toggleAnnouncementTop(@PathVariable Long id, @RequestBody AnnouncementTopUpdateReq request) {
        return Result.success(announcementsService.toggleAnnouncementTop(id, request.getIsTop()));
    }

    /** 批量置顶/取消置顶公告 */
    @PutMapping("/batch/top")
    @OperationLog(action = "update", targetType = "announcement", description = "批量切换公告置顶")
    public Result<Boolean> batchToggleAnnouncementTop(@RequestBody AnnouncementBatchTopReq request) {
        return Result.success(announcementsService.batchToggleAnnouncementTop(request.getIds(), request.getIsTop()));
    }

    /** 导出公告数据为Excel */
    @PostMapping("/export")
    @OperationLog(action = "export", targetType = "announcement", description = "导出公告")
    public void exportAnnouncements(@RequestBody(required = false) AnnouncementExportReq request,
                                    HttpServletResponse response) throws IOException {
        Integer status = (request != null) ? request.getStatus() : null;
        Integer type = (request != null) ? request.getType() : null;
        String keyword = (request != null) ? request.getKeyword() : null;
        Boolean includeDeleted = (request != null && request.getIncludeDeleted() != null) ? request.getIncludeDeleted() : false;

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("公告数据", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
        announcementImportService.exportToExcel(status, type, keyword, includeDeleted, response.getOutputStream());
        response.getOutputStream().flush();
    }

    /** 从Excel导入公告 */
    @PostMapping("/import")
    @OperationLog(action = "import", targetType = "announcement", description = "导入公告")
    public Result<Map<String, Object>> importAnnouncements(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只支持 .xlsx 或 .xls 格式的Excel文件");
        }
        return Result.success(announcementImportService.importFromExcel(file));
    }

    // ==================== 请求 DTO（字段少，就近定义为内部类） ====================

    @Data
    public static class AnnouncementExportReq {
        private Integer status;
        private Integer type;
        private String keyword;
        private Boolean includeDeleted;
    }

    @Data
    public static class AnnouncementStatusUpdateReq {
        private Integer status;
    }

    @Data
    public static class AnnouncementBatchStatusReq {
        private List<Long> ids;
        private Integer status;
    }

    @Data
    public static class AnnouncementTopUpdateReq {
        private Integer isTop;
    }

    @Data
    public static class AnnouncementBatchTopReq {
        private List<Long> ids;
        private Integer isTop;
    }
}
