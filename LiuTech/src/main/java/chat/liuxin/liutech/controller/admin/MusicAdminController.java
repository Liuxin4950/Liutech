package chat.liuxin.liutech.controller.admin;

import lombok.RequiredArgsConstructor;
import java.util.List;

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

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.service.MusicService;
import chat.liuxin.liutech.utils.ValidationUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理端音乐控制器（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
 */
@Slf4j
@RestController
@RequestMapping("/admin/music")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MusicAdminController extends BaseAdminController {

    private final MusicService musicService;

    /** 获取音乐列表（支持状态和关键词筛选） */
    @GetMapping("/list")
    public Result<List<Music>> getMusicList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(musicService.getAdminMusicList(status, keyword));
    }

    /** 获取音乐详情（管理端，不限状态） */
    @GetMapping("/{id}")
    public Result<Music> getMusicById(@PathVariable Long id) {
        ValidationUtil.validateId(id, "音乐ID");
        return Result.success(musicService.getAdminMusicById(id));
    }

    /** 上传音乐 */
    @PostMapping
    @OperationLog(action = "create", targetType = "music", description = "上传音乐: #title", targetName = "#title")
    public Result<Long> uploadMusic(
            @RequestParam String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String coverUrl,
            @RequestParam MultipartFile fullAudio,
            @RequestParam MultipartFile vocalAudio) {
        ValidationUtil.validateNotNull(title, "歌曲名");
        ValidationUtil.validateNotNull(fullAudio, "完整音频");
        ValidationUtil.validateNotNull(vocalAudio, "人声音频");
        return Result.success(musicService.uploadMusic(title, artist, coverUrl, fullAudio, vocalAudio));
    }

    /** 更新音乐信息 */
    @PutMapping("/{id}")
    @OperationLog(action = "update", targetType = "music", description = "更新音乐: #id", targetName = "#id")
    public Result<String> updateMusic(
            @PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String coverUrl,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) Integer status) {
        ValidationUtil.validateId(id, "音乐ID");
        return handleOperationResult(musicService.updateMusic(id, title, artist, coverUrl, sortOrder, status), "音乐更新成功", "更新音乐");
    }

    /** 删除音乐（硬删除 + 清理文件） */
    @DeleteMapping("/{id}")
    @OperationLog(action = "delete", targetType = "music", description = "删除音乐", targetName = "#id")
    public Result<String> deleteMusic(@PathVariable Long id) {
        ValidationUtil.validateId(id, "音乐ID");
        return handleOperationResult(musicService.deleteMusic(id), "音乐删除成功", "删除音乐");
    }

    /** 批量删除音乐（硬删除 + 清理文件） */
    @PostMapping("/batch")
    @OperationLog(action = "delete", targetType = "music", description = "批量删除音乐")
    public Result<String> batchDelete(@RequestBody List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "音乐ID列表");
        return handleOperationResult(musicService.batchDelete(ids), "批量删除音乐成功", "批量删除音乐");
    }

    /** 更新排序 */
    @PutMapping("/sort")
    @OperationLog(action = "update", targetType = "music", description = "更新音乐排序")
    public Result<String> updateSortOrder(@RequestParam List<Long> ids) {
        ValidationUtil.validateNotEmpty(ids, "音乐ID列表");
        return handleOperationResult(musicService.updateSortOrder(ids), "排序更新成功", "更新排序");
    }
}
