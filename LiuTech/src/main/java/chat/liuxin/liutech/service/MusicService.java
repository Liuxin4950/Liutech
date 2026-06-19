package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.MusicMapper;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 音乐服务类
 * @author liuxin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService extends ServiceImpl<MusicMapper, Music> {

    private final MusicMapper musicMapper;

    private final FileUtil fileUtil;

    private final FileUploadConfig fileUploadConfig;

    private final ImagesService imagesService;

    /**
     * 获取启用的音乐列表
     * @return 音乐列表
     */
    @Transactional(readOnly = true)
    public List<Music> getMusicList() {
        return musicMapper.selectList(new LambdaQueryWrapper<Music>()
                .eq(Music::getStatus, 1)
                .orderByAsc(Music::getSortOrder)
                .orderByAsc(Music::getId));
    }

    /**
     * 获取管理端音乐列表（支持筛选）
     * @param status 状态
     * @param keyword 关键词
     * @return 音乐列表
     */
    @Transactional(readOnly = true)
    public List<Music> getAdminMusicList(Integer status, String keyword) {
        LambdaQueryWrapper<Music> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Music::getStatus, status);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(Music::getTitle, keyword)
                    .or().like(Music::getArtist, keyword));
        }
        // 默认按排序值升序
        wrapper.orderByAsc(Music::getSortOrder).orderByAsc(Music::getId);
        return musicMapper.selectList(wrapper);
    }

    /**
     * 根据ID获取音乐
     * @param id 音乐ID
     * @return 音乐
     */
    @Transactional(readOnly = true)
    public Music getMusicById(Long id) {
        Music music = musicMapper.selectById(id);
        if (music == null || music.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在或已下架");
        }
        return music;
    }

    /**
     * 管理端根据ID获取音乐（不限状态）
     * @param id 音乐ID
     * @return 音乐
     */
    @Transactional(readOnly = true)
    public Music getAdminMusicById(Long id) {
        Music music = musicMapper.selectById(id);
        if (music == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在");
        }
        return music;
    }

    /**
     * 上传音乐（Admin）
     * @param title 歌曲名
     * @param artist 艺术家
     * @param coverUrl 封面图URL
     * @param fullAudio 完整音频
     * @param vocalAudio 人声音频
     * @return 音乐ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long uploadMusic(String title, String artist, String coverUrl,
                           MultipartFile fullAudio, MultipartFile vocalAudio) {
        // 验证参数
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "歌曲名不能为空");
        }
        if (fullAudio == null || fullAudio.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "完整音频不能为空");
        }
        if (vocalAudio == null || vocalAudio.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "人声音频不能为空");
        }

        Music music = new Music();
        music.setTitle(title);
        music.setArtist(artist);
        music.setCoverUrl(coverUrl);  // 直接使用前端上传后的URL
        music.setStatus(1);
        music.setSortOrder(0);
        music.setCreatedAt(new Date());
        music.setUpdatedAt(new Date());

        try {
            // 保存完整音频
            String fullAudioPath = fileUtil.saveFile(fullAudio, fileUploadConfig.getMusicPath());
            music.setFullAudioUrl(fileUtil.generateFileUrl(fullAudioPath));

            // 保存人声音频
            String vocalAudioPath = fileUtil.saveFile(vocalAudio, fileUploadConfig.getMusicPath());
            music.setVocalUrl(fileUtil.generateFileUrl(vocalAudioPath));

            // 保存记录
            musicMapper.insert(music);
            incrementCoverReference(music.getCoverUrl());

            log.info("音乐上传成功 - ID: {}, 标题: {}", music.getId(), title);
            return music.getId();

        } catch (Exception e) {
            log.error("音乐上传失败 - 标题: {}", title, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "音乐上传失败: " + e.getMessage());
        }
    }

    /**
     * 更新音乐信息（Admin）
     * @param id 音乐ID
     * @param title 歌曲名
     * @param artist 艺术家
     * @param coverUrl 封面图URL
     * @param sortOrder 排序
     * @param status 状态
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateMusic(Long id, String title, String artist, String coverUrl, Integer sortOrder, Integer status) {
        Music music = musicMapper.selectById(id);
        if (music == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在");
        }
        String oldCoverUrl = music.getCoverUrl();

        if (title != null && !title.trim().isEmpty()) {
            music.setTitle(title);
        }
        if (artist != null) {
            music.setArtist(artist);
        }
        // 处理封面变化
        if (coverUrl != null && !coverUrl.equals(oldCoverUrl)) {
            music.setCoverUrl(coverUrl);
            decrementCoverReference(oldCoverUrl);
            incrementCoverReference(coverUrl);
        }
        if (sortOrder != null) {
            music.setSortOrder(sortOrder);
        }
        if (status != null) {
            music.setStatus(status);
        }
        music.setUpdatedAt(new Date());

        return musicMapper.updateById(music) > 0;
    }

    /**
     * 删除音乐（Admin）- 硬删除并清理文件
     * @param id 音乐ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMusic(Long id) {
        Music music = musicMapper.selectById(id);
        if (music == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在");
        }

        // 减少封面图片引用计数
        decrementCoverReference(music.getCoverUrl());

        // 清理关联文件
        deleteMusicFiles(music);

        // 物理删除
        int rows = musicMapper.deleteById(id);
        if (rows > 0) {
            log.info("音乐已物理删除 - ID: {}, 标题: {}", id, music.getTitle());
            return true;
        } else {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除失败");
        }
    }

    /**
     * 批量删除音乐（Admin）- 硬删除并清理文件
     * @param ids 音乐ID列表
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }

        for (Long id : ids) {
            Music music = musicMapper.selectById(id);
            if (music != null) {
                // 减少封面图片引用计数
                decrementCoverReference(music.getCoverUrl());
                // 清理关联文件
                deleteMusicFiles(music);
                // 删除记录
                musicMapper.deleteById(id);
                log.info("音乐已物理删除 - ID: {}, 标题: {}", id, music.getTitle());
            }
        }
        return true;
    }

    /**
     * 删除音乐关联文件（仅音频文件，封面图由 usage_count 机制管理）
     * @param music 音乐对象
     */
    private void deleteMusicFiles(Music music) {
        // 注意：封面图不直接删除，由 usage_count 机制管理，定时任务在 usage_count=0 时清理
        // 只删除音频文件（音频文件没有复用机制）
        // 删除完整音频
        if (music.getFullAudioUrl() != null) {
            fileUtil.deleteFileByUrl(music.getFullAudioUrl());
        }
        // 删除人声音频
        if (music.getVocalUrl() != null) {
            fileUtil.deleteFileByUrl(music.getVocalUrl());
        }
    }

    /**
     * 更新排序
     * @param ids 排序后的ID列表
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSortOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }

        for (int i = 0; i < ids.size(); i++) {
            Music music = musicMapper.selectById(ids.get(i));
            if (music != null) {
                music.setSortOrder(i);
                music.setUpdatedAt(new Date());
                musicMapper.updateById(music);
            }
        }
        return true;
    }

    // ==================== 封面图片引用计数管理 ====================

    /**
     * 增加封面图片引用计数
     * @param coverUrl 封面URL
     */
    private void incrementCoverReference(String coverUrl) {
        if (coverUrl == null || coverUrl.isEmpty()) {
            return;
        }
        try {
            Images img = imagesService.getImageByUrl(coverUrl);
            if (img != null && img.getDeletedAt() == null) {
                imagesService.incrementUsageCount(img.getId(), 1);
                log.debug("音乐封面增加引用: {} -> {}", coverUrl, img.getUsageCount() + 1);
            }
        } catch (Exception e) {
            log.warn("增加封面引用计数失败: {}", coverUrl, e);
        }
    }

    /**
     * 减少封面图片引用计数
     * @param coverUrl 封面URL
     */
    private void decrementCoverReference(String coverUrl) {
        if (coverUrl == null || coverUrl.isEmpty()) {
            return;
        }
        try {
            Images img = imagesService.getImageByUrl(coverUrl);
            if (img != null) {
                // 无论图片是否已软删除，都减少 usage_count
                imagesService.incrementUsageCount(img.getId(), -1);
                log.debug("音乐封面减少引用: {} -> {}", coverUrl, Math.max(0, img.getUsageCount() - 1));
            }
        } catch (Exception e) {
            log.warn("减少封面引用计数失败: {}", coverUrl, e);
        }
    }
}
