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
     * @return 公告分页数据
     */
    @GetMapping("/admin/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<IPage<AnnouncementResl>> getAllAnnouncements(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type) {
        IPage<AnnouncementResl> result = announcementsService.getAllAnnouncements(current, size, status, type);
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
}