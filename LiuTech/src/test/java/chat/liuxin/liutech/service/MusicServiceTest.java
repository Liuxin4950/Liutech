package chat.liuxin.liutech.service;

import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.MusicMapper;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.utils.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MusicService 单元测试
 * 覆盖音乐列表查询等核心逻辑
 */
@ExtendWith(MockitoExtension.class)
class MusicServiceTest {

    @Mock
    private MusicMapper musicMapper;

    @Mock
    private FileUtil fileUtil;

    @Mock
    private FileUploadConfig fileUploadConfig;

    @Mock
    private ImagesService imagesService;

    @InjectMocks
    private MusicService musicService;

    // ========== getMusicList 测试 ==========

    @Test
    void getMusicList_shouldReturnEnabledMusicList() {
        Music music1 = new Music();
        music1.setId(1L);
        music1.setTitle("Song A");
        music1.setArtist("Artist A");
        music1.setStatus(1);
        music1.setSortOrder(1);

        Music music2 = new Music();
        music2.setId(2L);
        music2.setTitle("Song B");
        music2.setArtist("Artist B");
        music2.setStatus(1);
        music2.setSortOrder(2);

        when(musicMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(music1, music2));

        List<Music> result = musicService.getMusicList();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Song A", result.get(0).getTitle());
        assertEquals("Song B", result.get(1).getTitle());
        verify(musicMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void getMusicList_shouldReturnEmptyListWhenNoEnabledMusic() {
        when(musicMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        List<Music> result = musicService.getMusicList();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(musicMapper).selectList(any(LambdaQueryWrapper.class));
    }
}
