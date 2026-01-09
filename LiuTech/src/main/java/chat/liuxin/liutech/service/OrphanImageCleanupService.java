package chat.liuxin.liutech.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.query.LambdaQueryWrapper;

import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.model.Images;
import lombok.extern.slf4j.Slf4j;

/**
 * 孤立图片清理服务
 * 完全依赖 usage_count 判断图片是否可以删除
 * @author liuxin
 */
@Slf4j
@Service
public class OrphanImageCleanupService {

    /**
     * 图片上传基础路径
     */
    @Value("${file.upload.base-path:${user.dir}/uploads}")
    private String uploadBasePath;

    @Autowired
    private ImagesMapper imagesMapper;

    /**
     * 每天凌晨3点执行（北京时间）
     * 生产环境使用此配置
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void cleanup() {
        log.info("开始清理孤立图片...");

        try {
            // 1. 查询 usage_count = 0 的图片记录
            List<Images> zeroUsageImages = queryZeroUsageImages();
            log.info("发现 {} 张 usage_count=0 的图片记录", zeroUsageImages.size());

            if (zeroUsageImages.isEmpty()) {
                log.info("没有需要清理的孤立图片");
                return;
            }

            // 2. 删除物理文件并软删除记录
            int deletedCount = 0;
            int failedCount = 0;

            for (Images img : zeroUsageImages) {
                if (deleteImageAndRecord(img)) {
                    deletedCount++;
                } else {
                    failedCount++;
                }
            }

            log.info("孤立图片清理完成，成功删除: {} 张，失败: {} 张", deletedCount, failedCount);

        } catch (Exception e) {
            log.error("清理孤立图片失败", e);
        }
    }

    /**
     * 查询 usage_count = 0 的图片记录
     */
    private List<Images> queryZeroUsageImages() {
        LambdaQueryWrapper<Images> query = new LambdaQueryWrapper<>();
        query.eq(Images::getUsageCount, 0)
             .eq(Images::getStatus, 1)
             .isNull(Images::getDeletedAt);
        return imagesMapper.selectList(query);
    }

    /**
     * 删除图片物理文件并软删除记录
     */
    private boolean deleteImageAndRecord(Images img) {
        // 1. 删除物理文件
        boolean deleted = deletePhysicalFile(img.getFilePath());

        // 2. 软删除记录
        UpdateWrapper<Images> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", img.getId())
                     .set("deleted_at", new Date());
        imagesMapper.update(null, updateWrapper);

        if (deleted) {
            log.info("已清理孤立图片: {} (ID: {})", img.getFilePath(), img.getId());
        } else {
            log.warn("文件不存在或删除失败: {} (ID: {})，已软删除记录", img.getFilePath(), img.getId());
        }

        return deleted;
    }

    /**
     * 删除物理文件
     */
    private boolean deletePhysicalFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        try {
            Path fullPath = Paths.get(uploadBasePath, filePath);
            return Files.deleteIfExists(fullPath);
        } catch (IOException e) {
            log.warn("删除文件失败: {}", filePath, e);
            return false;
        }
    }

    /**
     * 兼容旧方法名（已废弃）
     */
    @Deprecated
    public void cleanupOrphanImages() {
        cleanup();
    }
}
