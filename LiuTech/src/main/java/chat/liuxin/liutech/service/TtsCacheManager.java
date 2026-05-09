package chat.liuxin.liutech.service;

import chat.liuxin.liutech.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * TTS 临时音频缓存管理。
 *
 * 职责：
 * - 管理缓存目录（创建、路径解析）
 * - 定时 + 写入后节流清理过期/超额文件
 * - 按时间和容量两个维度淘汰
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsCacheManager {

    private final FileUploadConfig fileUploadConfig;

    private final AtomicLong lastCleanupAt = new AtomicLong(0L);

    @Value("${tts.cache.max-age-hours:${TTS_CACHE_MAX_AGE_HOURS:24}}")
    private long maxAgeHours;

    @Value("${tts.cache.max-bytes:${TTS_CACHE_MAX_BYTES:536870912}}")
    private long maxBytes;

    @Value("${tts.cache.cleanup-interval-ms:${TTS_CACHE_CLEANUP_INTERVAL_MS:3600000}}")
    private long cleanupIntervalMs;

    /**
     * 获取 TTS 缓存目录绝对路径。
     */
    public Path cacheDir() {
        return Path.of(fileUploadConfig.getBasePath(), "tts-cache").toAbsolutePath().normalize();
    }

    /**
     * 写入音频后节流触发清理。
     * 两次清理之间至少间隔 cleanupIntervalMs 毫秒。
     */
    void cleanupIfDue() {
        long now = System.currentTimeMillis();
        long previous = lastCleanupAt.get();
        long interval = Math.max(1000L, cleanupIntervalMs);
        if ((now - previous) < interval) {
            return;
        }
        if (lastCleanupAt.compareAndSet(previous, now)) {
            cleanup();
        }
    }

    /**
     * 定时清理过期和超出容量限制的音频缓存文件。
     *
     * 策略：
     * 1. 先删除超过 maxAgeHours 的过期文件。
     * 2. 再按修改时间从旧到新删除，直到总大小低于 maxBytes。
     */
    @Scheduled(fixedDelayString = "${tts.cache.cleanup-interval-ms:${TTS_CACHE_CLEANUP_INTERVAL_MS:3600000}}")
    public void cleanup() {
        Path dir = cacheDir();
        if (!Files.isDirectory(dir)) {
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(Math.max(1L, maxAgeHours)));
        List<AudioCacheFile> activeFiles = new ArrayList<>();
        AtomicLong totalBytes = new AtomicLong(0L);
        AtomicLong deletedCount = new AtomicLong(0L);

        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(this::isManagedCacheFile)
                    .forEach(path -> collectOrDeleteExpired(path, cutoff, activeFiles, totalBytes, deletedCount));
        } catch (IOException e) {
            log.warn("扫描 TTS 缓存目录失败: {}", e.getMessage());
            return;
        }

        long capacity = Math.max(0L, maxBytes);
        if (capacity > 0 && totalBytes.get() > capacity) {
            activeFiles.sort(Comparator.comparing(AudioCacheFile::modifiedAt));
            for (AudioCacheFile file : activeFiles) {
                if (totalBytes.get() <= capacity) {
                    break;
                }
                if (deleteFile(file.path())) {
                    totalBytes.addAndGet(-file.size());
                    deletedCount.incrementAndGet();
                }
            }
        }

        long deleted = deletedCount.get();
        if (deleted > 0) {
            log.info("TTS 缓存清理完成: deleted={}, remainingBytes={}", deleted, totalBytes.get());
        }
    }

    private void collectOrDeleteExpired(
            Path path,
            Instant cutoff,
            List<AudioCacheFile> activeFiles,
            AtomicLong totalBytes,
            AtomicLong deletedCount) {
        try {
            FileTime modifiedAt = Files.getLastModifiedTime(path);
            long size = Files.size(path);
            if (modifiedAt.toInstant().isBefore(cutoff)) {
                if (deleteFile(path)) {
                    deletedCount.incrementAndGet();
                }
                return;
            }
            activeFiles.add(new AudioCacheFile(path, size, modifiedAt));
            totalBytes.addAndGet(size);
        } catch (IOException e) {
            log.debug("跳过不可读 TTS 缓存文件 {}: {}", path.getFileName(), e.getMessage());
        }
    }

    private boolean isManagedCacheFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return fileName.matches("[a-f0-9\\-]{36}\\.(mp3|wav|opus|pcm)");
    }

    private boolean deleteFile(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("删除 TTS 缓存文件失败 {}: {}", path.getFileName(), e.getMessage());
            return false;
        }
    }

    private record AudioCacheFile(Path path, long size, FileTime modifiedAt) {
    }
}
