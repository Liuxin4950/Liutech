package chat.liuxin.liutech.controller.web;

import java.util.List;

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

import com.baomidou.mybatisplus.core.metadata.IPage;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.req.AnnouncementReq;
import chat.liuxin.liutech.resl.AnnouncementResl;
import chat.liuxin.liutech.service.AnnouncementsService;
// Swagger注解已移除，项目暂不使用API文档

/**
 * 公告控制器
 * @author liuxin
 */
@RestController
@RequestMapping("/announcements")
public class AnnouncementsController {

    @Autowired
    private AnnouncementsService announcementsService;

    /**
     * 分页查询有效公告（前台用户）
     * @param current 当前页
     * @param size 每页大小
     * @return 公告分页数据
     */
    @GetMapping("/list")
    public Result<IPage<AnnouncementResl>> getValidAnnouncements(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        IPage<AnnouncementResl> result = announcementsService.getValidAnnouncements(current, size);
        return Result.success(result);
    }

    /**
     * 获取置顶公告列表
     * @param limit 限制数量
     * @return 置顶公告列表
     */
    @GetMapping("/top")
    public Result<List<AnnouncementResl>> getTopAnnouncements(
            @RequestParam(defaultValue = "5") Integer limit) {
        List<AnnouncementResl> result = announcementsService.getTopAnnouncements(limit);
        return Result.success(result);
    }

    /**
     * 获取最新公告列表
     * @param limit 限制数量
     * @return 最新公告列表
     */
    @GetMapping("/latest")
    public Result<List<AnnouncementResl>> getLatestAnnouncements(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<AnnouncementResl> result = announcementsService.getLatestAnnouncements(limit);
        return Result.success(result);
    }

    /**
     * 根据ID获取公告详情
     * @param id 公告ID
     * @return 公告详情
     */
    @GetMapping("/{id}")
    public Result<AnnouncementResl> getAnnouncementById(
            @PathVariable Long id) {
        AnnouncementResl result = announcementsService.getAnnouncementById(id);
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
    public Result<IPage<AnnouncementResl>> getAllAnnouncements(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        IPage<AnnouncementResl> result = announcementsService.getAllAnnouncements(current, size, status, type, includeDeleted);
        return Result.success(result);
    }

    /**
     * 创建公告
     * @param req 公告请求数据
     * @return 公告ID
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
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
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> batchDeleteAnnouncements(
            @RequestBody List<Long> ids) {
        boolean success = announcementsService.batchDeleteAnnouncements(ids);
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
    public Result<Boolean> toggleAnnouncementTop(
            @PathVariable Long id,
            @RequestBody TopUpdateRequest request) {
        boolean success = announcementsService.toggleAnnouncementTop(id, request.getIsTop());
        return Result.success(success);
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
}