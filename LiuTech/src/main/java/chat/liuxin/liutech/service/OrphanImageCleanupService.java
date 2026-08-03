package chat.liuxin.liutech.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.model.Images;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * 孤立图片清理服务
 * 完全依赖 usage_count 判断图片是否可以删除
 * @author liuxin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanImageCleanupService {

    /**
     * 图片上传基础路径
     */
    @Value("${file.upload.base-path:${user.dir}/uploads}")
    private String uploadBasePath;

    @Value("${orphan.image.cleanup.ttl-hours:168}")
    private long cleanupTtlHours;

    private final ImagesMapper imagesMapper;

    @Scheduled(cron = "${orphan.image.cleanup.cron:0 0 3 * * ?}", zone = "${orphan.image.cleanup.zone:Asia/Shanghai}")
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

            // 2. 删除物理文件并物理删除记录
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
        Date cutoff = new Date(System.currentTimeMillis() - cleanupTtlHours * 3600_000L);
        LambdaQueryWrapper<Images> query = new LambdaQueryWrapper<>();
        query.eq(Images::getUsageCount, 0)
             .eq(Images::getStatus, 1)
             .isNull(Images::getDeletedAt)
             .lt(Images::getCreatedAt, cutoff);
        return imagesMapper.selectList(query);
    }

    /**
     * 删除图片物理文件并物理删除记录
     */
    private boolean deleteImageAndRecord(Images img) {
        // 1. 删除物理文件（文件可能已被手动删除，返回 true 表示可以继续）
        boolean fileDeleted = deletePhysicalFile(img.getFilePath());

        // 2. 物理删除记录（绕过 @TableLogic 软删除）
        Integer rows = imagesMapper.permanentDeleteById(img.getId());

        if (rows != null && rows > 0) {
            if (fileDeleted) {
                log.info("已彻底清理孤立图片: {} (ID: {})", img.getFilePath(), img.getId());
            } else {
                // 文件不存在也视为成功（可能是之前被手动删除或清理过了）
                log.info("图片记录已删除（文件已不存在）: {} (ID: {})", img.getFilePath(), img.getId());
            }
            return true;
        } else {
            log.warn("清理失败（无法删除记录）: {} (ID: {})", img.getFilePath(), img.getId());
            return false;
        }
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
