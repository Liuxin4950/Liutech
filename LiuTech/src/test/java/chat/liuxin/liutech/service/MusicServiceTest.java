package chat.liuxin.liutech.service;

import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.MusicMapper;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.utils.FileUtil;
import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.storage.FileStorage;
import chat.liuxin.liutech.storage.StorageFileCleanup;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.AfterEach;
import java.io.InputStream;
import java.io.IOException;
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

    @Mock private FileStorage fileStorage;
    @Mock private ImageReferenceService imageReferenceService;
    @Mock private StorageFileCleanup storageFileCleanup;

    @InjectMocks
    private MusicService musicService;

    @BeforeEach
    void tableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "music-test"), Music.class);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private Music existing() {
        Music music = new Music();
        music.setId(1L); music.setTitle("原歌曲"); music.setArtist("原作者"); music.setCoverUrl("/uploads/images/old.png");
        music.setFullAudioUrl("/uploads/music/full-old.mp3"); music.setVocalUrl("/uploads/music/vocal-old.mp3");
        music.setSortOrder(7); music.setStatus(1);
        return music;
    }

    private MockMultipartFile audio(String name) {
        return new MockMultipartFile("fullAudio", name, "audio/mpeg", new byte[] {1, 2, 3});
    }

    private void transaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void storageSuccess() throws IOException {
        when(fileUploadConfig.getMaxFileSize()).thenReturn(100_000L);
        when(fileUploadConfig.getMusicPath()).thenReturn("music");
        when(fileStorage.save(any(InputStream.class), anyLong(), eq("music"), anyString())).thenReturn("music/new.mp3");
        when(fileStorage.generateUrl("music/new.mp3")).thenReturn("/uploads/music/new.mp3");
    }

    @Test
    void metadataOnlyEditDoesNotTouchAudioOrOverwriteUnrelatedColumns() {
        when(musicMapper.selectForUpdate(1L)).thenReturn(existing());
        when(musicMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        musicService.updateMusic(1L, "新标题", null, null, null, 0);
        var wrapper = org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(musicMapper).update(isNull(), wrapper.capture());
        String set = wrapper.getValue().getSqlSet();
        assertTrue(set.contains("title=")); assertTrue(set.contains("status="));
        assertFalse(set.contains("full_audio_url")); assertFalse(set.contains("vocal_url"));
        assertFalse(set.contains("sort_order")); assertFalse(set.contains("cover_url"));
        verifyNoInteractions(fileStorage, imageReferenceService);
    }

    @Test
    void successfulReplacementCleansOldTrackOnlyAfterCommitAndKeepsNewTrack() throws Exception {
        storageSuccess();
        when(musicMapper.selectForUpdate(1L)).thenReturn(existing());
        when(musicMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        transaction();
        musicService.updateMusic(1L, null, null, null, null, null, audio("full.mp3"), null);
        verify(storageFileCleanup).afterCommit(List.of("/uploads/music/full-old.mp3"));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void databaseFailureRollsBackNewFilesAndDoesNotScheduleOldFileDeletion() throws Exception {
        storageSuccess();
        when(musicMapper.selectForUpdate(1L)).thenReturn(existing());
        when(musicMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        transaction();
        assertThrows(BusinessException.class, () -> musicService.updateMusic(1L, null, null, null, null, null, audio("full.mp3"), null));
        verifyNoInteractions(storageFileCleanup);
        verify(fileStorage, never()).delete(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(fileStorage).delete("music/new.mp3");
    }

    @Test
    void uploadSecondTrackFailureCompensatesFirstTrack() throws Exception {
        when(fileUploadConfig.getMaxFileSize()).thenReturn(100_000L);
        when(fileUploadConfig.getMusicPath()).thenReturn("music");
        when(fileStorage.save(any(InputStream.class), anyLong(), eq("music"), anyString()))
                .thenReturn("music/first.mp3").thenThrow(new IOException("private storage detail"));
        when(fileStorage.generateUrl("music/first.mp3")).thenReturn("/uploads/music/first.mp3");
        BusinessException failure = assertThrows(BusinessException.class, () -> musicService.uploadMusic("歌曲", null, null, audio("full.mp3"), audio("vocal.mp3")));
        assertFalse(failure.getMessage().contains("private storage detail"));
        verify(fileStorage).delete("music/first.mp3");
        verifyNoInteractions(musicMapper, imageReferenceService);
    }

    @Test
    void invalidFilesAndMetadataAreRejectedBeforeLockOrStorage() {
        when(fileUploadConfig.getMaxFileSize()).thenReturn(100_000L);
        assertThrows(BusinessException.class, () -> musicService.updateMusic(1L, null, null, null, null, null, audio("bad.exe"), null));
        assertThrows(BusinessException.class, () -> musicService.updateMusic(1L, "  ", null, null, null, null));
        assertThrows(BusinessException.class, () -> musicService.updateMusic(1L, null, null, null, null, 2));
        verifyNoInteractions(musicMapper, fileStorage);
    }

    @Test
    void replacingOneTrackDoesNotDeleteFileSharedByRetainedTrack() throws Exception {
        storageSuccess();
        Music music = existing(); music.setVocalUrl(music.getFullAudioUrl());
        when(musicMapper.selectForUpdate(1L)).thenReturn(music);
        when(musicMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        musicService.updateMusic(1L, null, null, null, null, null, audio("full.mp3"), null);
        verify(storageFileCleanup).afterCommit(List.of());
    }

    @Test
    void deletionFailureKeepsOriginalFilesAndCoverReferences() {
        when(musicMapper.selectForUpdate(1L)).thenReturn(existing());
        when(musicMapper.deletePermanently(1L)).thenReturn(0);
        assertThrows(BusinessException.class, () -> musicService.deleteMusic(1L));
        verifyNoInteractions(storageFileCleanup, imageReferenceService, fileStorage);
    }

    @Test
    void filteredOrStalePlaylistCannotOverwriteSortPositions() {
        Music second = existing(); second.setId(2L);
        when(musicMapper.selectAllForUpdate()).thenReturn(List.of(existing(), second));
        assertThrows(BusinessException.class, () -> musicService.updateSortOrder(List.of(1L)));
        verify(musicMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void fullPlaylistOrderOnlyUpdatesSortAndTimeColumns() {
        Music second = existing(); second.setId(2L);
        when(musicMapper.selectAllForUpdate()).thenReturn(List.of(existing(), second));
        musicService.updateSortOrder(List.of(2L, 1L));
        var wrapper = org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(musicMapper, times(2)).update(isNull(), wrapper.capture());
        for (var value : wrapper.getAllValues()) {
            assertTrue(value.getSqlSet().contains("sort_order="));
            assertFalse(value.getSqlSet().contains("full_audio_url"));
            assertFalse(value.getSqlSet().contains("status="));
        }
    }

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
