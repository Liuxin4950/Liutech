package chat.liuxin.liutech.controller.web;

import lombok.RequiredArgsConstructor;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.service.MusicService;
import lombok.extern.slf4j.Slf4j;

/**
 * 音乐控制器（用户端接口）
 * @author 刘鑫
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    /**
     * 获取启用的音乐列表（所有用户可访问）
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
}
