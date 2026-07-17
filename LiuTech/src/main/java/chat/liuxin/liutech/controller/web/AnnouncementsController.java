package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.AnnouncementResp;
import chat.liuxin.liutech.service.AnnouncementsService;

/**
 * 公告控制器（用户前台）
 * 仅提供公开公告查询接口，管理端接口见 {@link chat.liuxin.liutech.controller.admin.AnnouncementsAdminController}（/admin/announcements）。
 * @author 刘鑫
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
}
