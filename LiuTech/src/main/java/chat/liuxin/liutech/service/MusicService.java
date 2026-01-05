package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.MusicMapper;
import chat.liuxin.liutech.model.Music;
import chat.liuxin.liutech.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 音乐服务类
 * @author liuxin
 */
@Slf4j
@Service
public class MusicService extends ServiceImpl<MusicMapper, Music> {

    @Autowired
    private MusicMapper musicMapper;

    @Autowired
    private FileUtil fileUtil;

    @Autowired
    private FileUploadConfig fileUploadConfig;

    /**
     * 获取启用的音乐列表
     * @return 音乐列表
     */
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
    public Music getMusicById(Long id) {
        Music music = musicMapper.selectById(id);
        if (music == null || music.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在或已下架");
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
    @Transactional
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
    @Transactional
    public boolean updateMusic(Long id, String title, String artist, String coverUrl, Integer sortOrder, Integer status) {
        Music music = musicMapper.selectById(id);
        if (music == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在");
        }

        if (title != null && !title.trim().isEmpty()) {
            music.setTitle(title);
        }
        if (artist != null) {
            music.setArtist(artist);
        }
        if (coverUrl != null) {
            music.setCoverUrl(coverUrl);
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
    @Transactional
    public boolean deleteMusic(Long id) {
        Music music = musicMapper.selectById(id);
        if (music == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不存在");
        }

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
    @Transactional
    public boolean batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }

        for (Long id : ids) {
            Music music = musicMapper.selectById(id);
            if (music != null) {
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
     * 删除音乐关联文件
     * @param music 音乐对象
     */
    private void deleteMusicFiles(Music music) {
        // 删除封面图
        if (music.getCoverUrl() != null && !music.getCoverUrl().isEmpty()) {
            fileUtil.deleteFileByUrl(music.getCoverUrl());
        }
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
    @Transactional
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
}
