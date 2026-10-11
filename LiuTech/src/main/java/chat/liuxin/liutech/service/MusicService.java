package chat.liuxin.liutech.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.MusicMapper;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.storage.FileStorage;
import chat.liuxin.liutech.storage.StorageFileCleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 完整音轨负责播放，人声音轨只用于口型分析；音频替换与数据库引用同事务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService extends ServiceImpl<MusicMapper, Music> {
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "ogg", "flac", "m4a", "aac", "webm", "opus");
    private final MusicMapper musicMapper;
    private final FileStorage fileStorage;
    private final FileUploadConfig fileUploadConfig;
    private final ImageReferenceService imageReferenceService;
    private final StorageFileCleanup storageFileCleanup;

    @Transactional(readOnly = true)
    public List<Music> getMusicList() {
        return musicMapper.selectList(new LambdaQueryWrapper<Music>()
                .eq(Music::getStatus, 1).orderByAsc(Music::getSortOrder).orderByAsc(Music::getId));
    }

    @Transactional(readOnly = true)
    public List<Music> getAdminMusicList(Integer status, String keyword) {
        var wrapper = new LambdaQueryWrapper<Music>();
        if (status != null) wrapper.eq(Music::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Music::getTitle, keyword.trim()).or().like(Music::getArtist, keyword.trim()));
        }
        return musicMapper.selectList(wrapper.orderByAsc(Music::getSortOrder).orderByAsc(Music::getId));
    }

    @Transactional(readOnly = true)
    public Music getMusicById(Long id) {
        Music music = getAdminMusicById(id);
        if (!Integer.valueOf(1).equals(music.getStatus())) throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在或已下架");
        return music;
    }

    @Transactional(readOnly = true)
    public Music getAdminMusicById(Long id) {
        Music music = musicMapper.selectById(id);
        if (music == null) throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在");
        return music;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long uploadMusic(String title, String artist, String coverUrl, MultipartFile fullAudio, MultipartFile vocalAudio) {
        if (title == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "歌曲名不能为空");
        validateMetadata(title, artist, 0, 1);
        validateAudio(fullAudio, "完整音频", true);
        validateAudio(vocalAudio, "人声音频", true);
        List<String> newPaths = new ArrayList<>();
        boolean managed = registerRollbackCleanup(newPaths);
        try {
            Music music = new Music();
            music.setTitle(title.trim());
            music.setArtist(artist == null ? null : artist.trim());
            music.setCoverUrl(coverUrl);
            music.setFullAudioUrl(saveAudio(fullAudio, newPaths));
            music.setVocalUrl(saveAudio(vocalAudio, newPaths));
            music.setSortOrder(0);
            music.setStatus(1);
            music.setCreatedAt(new Date());
            music.setUpdatedAt(new Date());
            if (musicMapper.insert(music) != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "音乐保存失败");
            imageReferenceService.addReferences(coverUrls(coverUrl));
            return music.getId();
        } catch (RuntimeException failure) {
            if (!managed) cleanupNewPaths(newPaths);
            throw failure;
        }
    }

    /** 保留既有仅更新元数据的服务契约。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateMusic(Long id, String title, String artist, String coverUrl, Integer sortOrder, Integer status) {
        return updateMusic(id, title, artist, coverUrl, sortOrder, status, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateMusic(Long id, String title, String artist, String coverUrl, Integer sortOrder, Integer status,
                               MultipartFile fullAudio, MultipartFile vocalAudio) {
        validateMetadata(title, artist, sortOrder, status);
        validateAudio(fullAudio, "完整音频", false);
        validateAudio(vocalAudio, "人声音频", false);
        // 与删除/其它编辑串行，避免替换后清理掉另一次编辑仍在使用的音轨。
        Music existing = requireLockedMusic(id);
        List<String> newPaths = new ArrayList<>();
        boolean managed = registerRollbackCleanup(newPaths);
        try {
            var update = new LambdaUpdateWrapper<Music>().eq(Music::getId, id).set(Music::getUpdatedAt, new Date());
            if (title != null) update.set(Music::getTitle, title.trim());
            if (artist != null) update.set(Music::getArtist, artist.trim());
            if (sortOrder != null) update.set(Music::getSortOrder, sortOrder);
            if (status != null) update.set(Music::getStatus, status);
            List<String> oldAudio = new ArrayList<>();
            if (fullAudio != null) {
                update.set(Music::getFullAudioUrl, saveAudio(fullAudio, newPaths)).set(Music::getDuration, null);
                oldAudio.add(existing.getFullAudioUrl());
            }
            if (vocalAudio != null) {
                update.set(Music::getVocalUrl, saveAudio(vocalAudio, newPaths));
                oldAudio.add(existing.getVocalUrl());
            }
            if (coverUrl != null && !coverUrl.equals(existing.getCoverUrl())) {
                update.set(Music::getCoverUrl, coverUrl);
                imageReferenceService.syncReferences(coverUrls(existing.getCoverUrl()), coverUrls(coverUrl));
            }
            if (musicMapper.update(null, update) != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "音乐更新失败");
            // 单轨替换不能删除恰好与另一条保留轨复用的旧文件。
            if (fullAudio == null) oldAudio.removeIf(url -> java.util.Objects.equals(url, existing.getFullAudioUrl()));
            if (vocalAudio == null) oldAudio.removeIf(url -> java.util.Objects.equals(url, existing.getVocalUrl()));
            storageFileCleanup.afterCommit(oldAudio);
            return true;
        } catch (RuntimeException failure) {
            if (!managed) cleanupNewPaths(newPaths);
            throw failure;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMusic(Long id) {
        Music music = requireLockedMusic(id);
        if (musicMapper.deletePermanently(id) != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "音乐删除失败");
        imageReferenceService.removeReferences(coverUrls(music.getCoverUrl()));
        storageFileCleanup.afterCommit(java.util.Arrays.asList(music.getFullAudioUrl(), music.getVocalUrl()));
        log.info("音乐已永久删除 - ID: {}", id);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return true;
        validateIds(ids);
        for (Long id : ids.stream().distinct().sorted().toList()) {
            Music music = musicMapper.selectForUpdate(id);
            if (music == null || music.getDeletedAt() != null) continue;
            if (musicMapper.deletePermanently(id) != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "音乐删除失败");
            imageReferenceService.removeReferences(coverUrls(music.getCoverUrl()));
            storageFileCleanup.afterCommit(java.util.Arrays.asList(music.getFullAudioUrl(), music.getVocalUrl()));
            log.info("音乐已永久删除 - ID: {}", id);
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateSortOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return true;
        validateIds(ids);
        if (ids.stream().distinct().count() != ids.size()) throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序列表不能包含重复音乐");
        // 对完整歌单加锁并校验，避免筛选子集或过期列表造成排序重复/遗漏。
        List<Music> current = musicMapper.selectAllForUpdate();
        var currentIds = current.stream().map(Music::getId).collect(java.util.stream.Collectors.toSet());
        if (!currentIds.equals(new java.util.HashSet<>(ids))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请刷新完整音乐列表后再调整顺序");
        }
        for (int i = 0; i < ids.size(); i++) {
            musicMapper.update(null, new LambdaUpdateWrapper<Music>().eq(Music::getId, ids.get(i))
                    .set(Music::getSortOrder, i).set(Music::getUpdatedAt, new Date()));
        }
        return true;
    }

    private Music requireLockedMusic(Long id) {
        Music music = musicMapper.selectForUpdate(id);
        if (music == null || music.getDeletedAt() != null) throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在");
        return music;
    }

    private void validateMetadata(String title, String artist, Integer sortOrder, Integer status) {
        if (title != null && (title.isBlank() || title.trim().length() > 100)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "歌曲名须为 1 至 100 个字符");
        if (artist != null && artist.trim().length() > 100) throw new BusinessException(ErrorCode.PARAMS_ERROR, "艺术家名称不能超过 100 个字符");
        if (sortOrder != null && sortOrder < 0) throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序不能为负数");
        if (status != null && status != 0 && status != 1) throw new BusinessException(ErrorCode.PARAMS_ERROR, "音乐状态不合法");
    }

    private void validateIds(List<Long> ids) {
        if (ids.size() > 500 || ids.stream().anyMatch(id -> id == null || id <= 0)) throw new BusinessException(ErrorCode.PARAMS_ERROR, "音乐 ID 列表不合法（最多 500 项）");
    }

    private void validateAudio(MultipartFile file, String label, boolean required) {
        if (file == null) {
            if (required) throw new BusinessException(ErrorCode.PARAMS_ERROR, label + "不能为空");
            return;
        }
        if (file.isEmpty() || file.getSize() > fileUploadConfig.getMaxFileSize()) throw new BusinessException(ErrorCode.PARAMS_ERROR, label + "为空或超过上传大小限制");
        String name = file.getOriginalFilename();
        String extension = name == null || !name.contains(".") ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mime = file.getContentType();
        if (!AUDIO_EXTENSIONS.contains(extension) || (mime != null && !mime.startsWith("audio/")
                && !mime.equals("application/octet-stream") && !mime.equals("video/webm") && !mime.equals("video/mp4"))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, label + "格式不支持，请选择音频文件");
        }
    }

    private String saveAudio(MultipartFile audio, List<String> newPaths) {
        try (InputStream input = audio.getInputStream()) {
            String path = fileStorage.save(input, audio.getSize(), fileUploadConfig.getMusicPath(), audio.getOriginalFilename());
            newPaths.add(path);
            return fileStorage.generateUrl(path);
        } catch (IOException failure) {
            log.error("音乐音轨保存失败", failure);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "音频保存失败，请稍后重试");
        }
    }

    private boolean registerRollbackCleanup(List<String> newPaths) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                // 提交结果未知时保留文件，以免删除可能已被引用的音轨。
                if (status == STATUS_ROLLED_BACK) cleanupNewPaths(newPaths);
            }
        });
        return true;
    }

    private void cleanupNewPaths(List<String> paths) {
        paths.forEach(path -> {
            try { fileStorage.delete(path); }
            catch (RuntimeException failure) { log.warn("音乐新文件回滚清理未完成: path={}", path, failure); }
        });
    }

    private List<String> coverUrls(String url) {
        return url == null || url.isBlank() ? List.of() : List.of(url);
    }
}
