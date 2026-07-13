package chat.liuxin.liutech.controller.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
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
// Swagger注解已移除，项目暂不使用API文档

/**
 * 公告控制器
 * @author 刘鑫
 */
@RestController
@RequestMapping("/announcements")
public class AnnouncementsController {

    @Autowired
    private AnnouncementsService announcementsService;

    @Autowired
    private AnnouncementImportService announcementImportService;

    /**
     * 分页查询有效公告（前台用户）
     * @param current 当前页
     * @param size 每页大小
     * @return 公告分页数据
     */
    @GetMapping("/list")
    public Result<IPage<AnnouncementResp>> getValidAnnouncements(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        IPage<AnnouncementResp> result = announcementsService.getValidAnnouncements(current, size);
        return Result.success(result);
    }

    /**
     * 获取置顶公告列表
     * @param limit 限制数量
     * @return 置顶公告列表
     */
    @GetMapping("/top")
    public Result<List<AnnouncementResp>> getTopAnnouncements(
            @RequestParam(defaultValue = "5") Integer limit) {
        List<AnnouncementResp> result = announcementsService.getTopAnnouncements(limit);
        return Result.success(result);
    }

    /**
     * 获取最新公告列表
     * @param limit 限制数量
     * @return 最新公告列表
     */
    @GetMapping("/latest")
    public Result<List<AnnouncementResp>> getLatestAnnouncements(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<AnnouncementResp> result = announcementsService.getLatestAnnouncements(limit);
        return Result.success(result);
    }

    /**
     * 根据ID获取公告详情
     * @param id 公告ID
     * @return 公告详情
     */
    @GetMapping("/{id}")
    public Result<AnnouncementResp> getAnnouncementById(
            @PathVariable Long id) {
        AnnouncementResp result = announcementsService.getAnnouncementById(id);
        return Result.success(result);
    }

    /**
     * 管理员根据ID获取公告详情（不限状态）
     * @param id 公告ID
     * @return 公告详情
     */
    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AnnouncementResp> getAnnouncementByIdForAdmin(
            @PathVariable Long id) {
        AnnouncementResp result = announcementsService.getAnnouncementByIdForAdmin(id);
        return Result.success(result);
    }

    /**
     * 管理员分页查询所有公告
     * @param current 当前页
     * @param size 每页大小
     * @param status 状态筛选
     * @param type 类型筛选
     * @param includeDeleted 是否包含已删除的公告
     * @return 公告分页数据
     */
    @GetMapping("/admin/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<IPage<AnnouncementResp>> getAllAnnouncements(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        IPage<AnnouncementResp> result = announcementsService.getAllAnnouncements(current, size, status, type, keyword, includeDeleted);
        return Result.success(result);
    }

    /**
     * 创建公告
     * @param req 公告请求数据
     * @return 公告ID
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "create", targetType = "announcement", description = "创建公告")
    public Result<Long> createAnnouncement(@Valid @RequestBody AnnouncementReq req) {
        Long id = announcementsService.createAnnouncement(req);
        return Result.success(id);
    }

    /**
     * 更新公告
     * @param id 公告ID
     * @param req 公告请求数据
     * @return 是否成功
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "update", targetType = "announcement", description = "更新公告")
    public Result<Boolean> updateAnnouncement(
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementReq req) {
        req.setId(id);
        boolean success = announcementsService.updateAnnouncement(req);
        return Result.success(success);
    }

    /**
     * 删除公告
     * @param id 公告ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "delete", targetType = "announcement", description = "删除公告")
    public Result<Boolean> deleteAnnouncement(
            @PathVariable Long id) {
        boolean success = announcementsService.deleteAnnouncement(id);
        return Result.success(success);
    }

    /**
     * 批量删除公告
     * @param ids 公告ID列表
     * @return 是否成功
     */
    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "delete", targetType = "announcement", description = "批量删除公告")
    public Result<Boolean> batchDeleteAnnouncements(
            @RequestBody List<Long> ids) {
        boolean success = announcementsService.batchDeleteAnnouncements(ids);
        return Result.success(success);
    }

    /**
     * 物理删除单条公告（彻底删除，不可恢复）
     * @param id 公告ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "delete", targetType = "announcement", description = "物理删除公告")
    public Result<Boolean> permanentDeleteAnnouncement(
            @PathVariable Long id) {
        boolean success = announcementsService.permanentDeleteAnnouncement(id);
        return Result.success(success);
    }

    /**
     * 批量物理删除公告（彻底删除，不可恢复）
     * @param ids 公告ID列表
     * @return 是否成功
     */
    @PostMapping("/batch/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "delete", targetType = "announcement", description = "批量物理删除公告")
    public Result<Boolean> batchPermanentDeleteAnnouncements(
            @RequestBody List<Long> ids) {
        boolean success = announcementsService.batchPermanentDeleteAnnouncements(ids);
        return Result.success(success);
    }

    /**
     * 更新公告状态
     * @param id 公告ID
     * @param request 状态更新请求
     * @return 是否成功
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "update", targetType = "announcement", description = "更新公告状态")
    public Result<Boolean> updateAnnouncementStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {
        boolean success = announcementsService.updateAnnouncementStatus(id, request.getStatus());
        return Result.success(success);
    }

    /**
     * 批量更新公告状态
     * @param request 批量状态更新请求
     * @return 是否成功
     */
    @PutMapping("/batch/status")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "update", targetType = "announcement", description = "批量更新公告状态")
    public Result<Boolean> batchUpdateAnnouncementStatus(
            @RequestBody BatchStatusUpdateRequest request) {
        boolean success = announcementsService.batchUpdateAnnouncementStatus(request.getIds(), request.getStatus());
        return Result.success(success);
    }

    /**
     * 恢复已删除的公告
     * @param id 公告ID
     * @return 是否成功
     */
    @PutMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "restore", targetType = "announcement", description = "恢复公告")
    public Result<Boolean> restoreAnnouncement(
            @PathVariable Long id) {
        boolean success = announcementsService.restoreAnnouncement(id);
        return Result.success(success);
    }

    /**
     * 置顶/取消置顶公告
     * @param id 公告ID
     * @param request 置顶状态更新请求
     * @return 是否成功
     */
    @PutMapping("/{id}/top")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "update", targetType = "announcement", description = "切换公告置顶")
    public Result<Boolean> toggleAnnouncementTop(
            @PathVariable Long id,
            @RequestBody TopUpdateRequest request) {
        boolean success = announcementsService.toggleAnnouncementTop(id, request.getIsTop());
        return Result.success(success);
    }

    /**
     * 批量置顶/取消置顶公告
     * @param request 批量置顶状态更新请求
     * @return 是否成功
     */
    @PutMapping("/batch/top")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "update", targetType = "announcement", description = "批量切换公告置顶")
    public Result<Boolean> batchToggleAnnouncementTop(
            @RequestBody BatchTopUpdateRequest request) {
        boolean success = announcementsService.batchToggleAnnouncementTop(request.getIds(), request.getIsTop());
        return Result.success(success);
    }

    /**
     * 导出公告数据为Excel
     * @param request 导出筛选条件
     * @param response HTTP响应
     */
    @PostMapping("/admin/export")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "export", targetType = "announcement", description = "导出公告")
    public void exportAnnouncements(
            @RequestBody(required = false) ExportRequest request,
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

    /**
     * 从Excel导入公告
     * @param file 上传的Excel文件
     * @return 导入结果（成功数、失败数、错误信息）
     */
    @PostMapping("/admin/import")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(action = "import", targetType = "announcement", description = "导入公告")
    public Result<Map<String, Object>> importAnnouncements(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只支持 .xlsx 或 .xls 格式的Excel文件");
        }
        Map<String, Object> result = announcementImportService.importFromExcel(file);
        return Result.success(result);
    }

    /**
     * 导出请求参数
     */
    public static class ExportRequest {
        private Integer status;
        private Integer type;
        private String keyword;
        private Boolean includeDeleted;

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public Integer getType() {
            return type;
        }

        public void setType(Integer type) {
            this.type = type;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public Boolean getIncludeDeleted() {
            return includeDeleted;
        }

        public void setIncludeDeleted(Boolean includeDeleted) {
            this.includeDeleted = includeDeleted;
        }
    }

    /**
     * 状态更新请求
     */
    public static class StatusUpdateRequest {
        private Integer status;

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    /**
     * 批量状态更新请求
     */
    public static class BatchStatusUpdateRequest {
        private List<Long> ids;
        private Integer status;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    /**
     * 置顶状态更新请求
     */
    public static class TopUpdateRequest {
        private Integer isTop;

        public Integer getIsTop() {
            return isTop;
        }

        public void setIsTop(Integer isTop) {
            this.isTop = isTop;
        }
    }

    /**
     * 批量置顶状态更新请求
     */
    public static class BatchTopUpdateRequest {
        private List<Long> ids;
        private Integer isTop;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }

        public Integer getIsTop() {
            return isTop;
        }

        public void setIsTop(Integer isTop) {
            this.isTop = isTop;
        }
    }
}
