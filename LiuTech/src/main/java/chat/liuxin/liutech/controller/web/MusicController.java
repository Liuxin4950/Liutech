package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.service.MusicService;
import lombok.extern.slf4j.Slf4j;

/**
 * 音乐控制器
 * @author liuxin
 */
@Slf4j
@RestController
public class MusicController {

    @Autowired
    private MusicService musicService;

    /**
     * 获取音乐列表（所有用户可访问）
     * @return 音乐列表
     */
    @GetMapping("/music/list")
    public Result<List<Music>> getMusicList() {
        List<Music> result = musicService.getMusicList();
        return Result.success(result);
    }

    /**
     * 获取音乐详情（所有用户可访问）
     * @param id 音乐ID
     * @return 音乐详情
     */
    @GetMapping("/music/{id}")
    public Result<Music> getMusicById(@PathVariable Long id) {
        Music result = musicService.getMusicById(id);
        return Result.success(result);
    }

    /**
     * 上传音乐（仅Admin）
     * @param title 歌曲名
     * @param artist 艺术家
     * @param cover 封面图
     * @param fullAudio 完整音频
     * @param vocalAudio 人声音频
     * @return 音乐ID
     */
    @PostMapping("/admin/music")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Long> uploadMusic(
            @RequestParam String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) MultipartFile cover,
            @RequestParam MultipartFile fullAudio,
            @RequestParam MultipartFile vocalAudio) {
        try {
            Long id = musicService.uploadMusic(title, artist, cover, fullAudio, vocalAudio);
            return Result.success(id);
        } catch (Exception e) {
            log.error("上传音乐失败", e);
            return Result.fail(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 更新音乐信息（仅Admin）
     * @param id 音乐ID
     * @param title 歌曲名
     * @param artist 艺术家
     * @param sortOrder 排序
     * @param status 状态
     * @return 是否成功
     */
    @PutMapping("/admin/music/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> updateMusic(
            @PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) Integer status) {
        try {
            boolean success = musicService.updateMusic(id, title, artist, sortOrder, status);
            return Result.success(success);
        } catch (Exception e) {
            log.error("更新音乐失败", e);
            return Result.fail(500, "更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除音乐（仅Admin）
     * @param id 音乐ID
     * @return 是否成功
     */
    @DeleteMapping("/admin/music/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> deleteMusic(@PathVariable Long id) {
        try {
            boolean success = musicService.deleteMusic(id);
            return Result.success(success);
        } catch (Exception e) {
            log.error("删除音乐失败", e);
            return Result.fail(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 更新排序（仅Admin）
     * @param ids 排序后的ID列表
     * @return 是否成功
     */
    @PutMapping("/admin/music/sort")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> updateSortOrder(@RequestParam List<Long> ids) {
        try {
            boolean success = musicService.updateSortOrder(ids);
            return Result.success(success);
        } catch (Exception e) {
            log.error("更新排序失败", e);
            return Result.fail(500, "更新排序失败: " + e.getMessage());
        }
    }
}
