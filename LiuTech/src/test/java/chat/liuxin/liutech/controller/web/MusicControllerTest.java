package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.service.MusicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MusicControllerTest {

    private MusicController controller;
    private MusicService musicService;

    @BeforeEach
    void setUp() {
        controller = new MusicController();
        musicService = mock(MusicService.class);
        ReflectionTestUtils.setField(controller, "musicService", musicService);
    }

    // ========== getMusicList ==========

    @Test
    void getMusicList_shouldReturnList() {
        Music music = new Music();
        music.setTitle("Background Music");
        music.setArtist("Artist");
        when(musicService.getMusicList()).thenReturn(List.of(music));

        Result<List<Music>> result = controller.getMusicList();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("Background Music", result.getData().get(0).getTitle());
    }

    @Test
    void getMusicList_shouldReturnEmptyListWhenNoneExist() {
        when(musicService.getMusicList()).thenReturn(Collections.emptyList());

        Result<List<Music>> result = controller.getMusicList();

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ========== getMusicById ==========

    @Test
    void getMusicById_shouldReturnMusicWhenExists() {
        Music music = new Music();
        music.setId(1L);
        music.setTitle("Song");
        when(musicService.getMusicById(1L)).thenReturn(music);

        Result<Music> result = controller.getMusicById(1L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("Song", result.getData().getTitle());
    }

    @Test
    void getMusicById_shouldReturnNullDataWhenNotFound() {
        when(musicService.getMusicById(999L)).thenReturn(null);

        Result<Music> result = controller.getMusicById(999L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNull(result.getData());
    }
}
