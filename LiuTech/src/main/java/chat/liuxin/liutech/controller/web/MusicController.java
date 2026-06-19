package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.service.MusicService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 音乐控制器（用户端接口）
 * @author liuxin
 */
@Slf4j
@RestController
@RequiredArgsConstructor
 {

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
