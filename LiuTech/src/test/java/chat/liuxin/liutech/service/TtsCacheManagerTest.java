package chat.liuxin.liutech.service;

import chat.liuxin.liutech.config.FileUploadConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TTS 缓存清理逻辑测试（原 TtsCacheManagerTest，适配合并后的 TtsSpeechService）
 */
class TtsCacheManagerTest {

    @TempDir
    Path tempDir;

    private TtsSpeechService ttsSpeechService;

    @BeforeEach
    void setUp() {
        FileUploadConfig fileUploadConfig = new FileUploadConfig();
        ReflectionTestUtils.setField(fileUploadConfig, "basePath", tempDir.toString());

        ttsSpeechService = new TtsSpeechService(null, fileUploadConfig, null);
        ReflectionTestUtils.setField(ttsSpeechService, "maxAgeHours", 24L);
        ReflectionTestUtils.setField(ttsSpeechService, "maxBytes", 100_000L);
        ReflectionTestUtils.setField(ttsSpeechService, "cleanupIntervalMs", 100L);
    }

    @Test
    void cacheDirShouldReturnTtsCacheUnderUploads() {
        Path dir = ttsSpeechService.cacheDir();
        assertTrue(dir.endsWith("tts-cache"));
        assertTrue(dir.startsWith(tempDir));
    }

    @Test
    void cleanupShouldSkipWhenDirectoryDoesNotExist() {
        ttsSpeechService.cleanup();
        assertFalse(Files.exists(ttsSpeechService.cacheDir()));
    }

    @Test
    void cleanupShouldDeleteExpiredFiles() throws IOException {
        Path dir = ttsSpeechService.cacheDir();
        Files.createDirectories(dir);

        Path file = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file, new byte[1024]);
        FileTime oldTime = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        Files.setLastModifiedTime(file, oldTime);

        ttsSpeechService.cleanup();

        assertFalse(Files.exists(file), "Expired file should be deleted");
    }

    @Test
    void cleanupShouldNotDeleteRecentFiles() throws IOException {
        Path dir = ttsSpeechService.cacheDir();
        Files.createDirectories(dir);

        Path file = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file, new byte[1024]);

        ttsSpeechService.cleanup();

        assertTrue(Files.exists(file), "Recent file should not be deleted");
    }

    @Test
    void cleanupShouldEvictOldestWhenOverCapacity() throws IOException, InterruptedException {
        Path dir = ttsSpeechService.cacheDir();
        Files.createDirectories(dir);

        Path file1 = dir.resolve(UUID.randomUUID() + ".mp3");
        Path file2 = dir.resolve(UUID.randomUUID() + ".mp3");
        Path file3 = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file1, new byte[40_000]);
        Thread.sleep(10);
        Files.write(file2, new byte[40_000]);
        Thread.sleep(10);
        Files.write(file3, new byte[40_000]);

        ttsSpeechService.cleanup();

        assertFalse(Files.exists(file1), "Oldest file should be evicted");
        assertTrue(Files.exists(file2), "Second file should remain");
        assertTrue(Files.exists(file3), "Third file should remain");
    }

    @Test
    void cleanupShouldSkipNonManagedFiles() throws IOException {
        Path dir = ttsSpeechService.cacheDir();
        Files.createDirectories(dir);

        Path file = dir.resolve("test.txt");
        Files.write(file, "hello".getBytes());
        FileTime oldTime = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        Files.setLastModifiedTime(file, oldTime);

        ttsSpeechService.cleanup();

        assertTrue(Files.exists(file), "Non-managed file should be left alone");
    }

    @Test
    void cleanupShouldHandleUnreadableFiles() throws IOException {
        Path dir = ttsSpeechService.cacheDir();
        Files.createDirectories(dir);

        ttsSpeechService.cleanup();
    }

    @Test
    void cleanupIfDueShouldThrottleWhenCalledRapidly() throws IOException {
        Path dir = ttsSpeechService.cacheDir();
        Files.createDirectories(dir);

        ReflectionTestUtils.setField(ttsSpeechService, "cleanupIntervalMs", 60_000L);

        Path file1 = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file1, new byte[1024]);
        FileTime oldTime = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        Files.setLastModifiedTime(file1, oldTime);

        ttsSpeechService.cleanupIfDue();
        assertFalse(Files.exists(file1), "First call should delete expired file");

        Path file2 = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file2, new byte[1024]);
        Files.setLastModifiedTime(file2, oldTime);
        ttsSpeechService.cleanupIfDue();
        assertTrue(Files.exists(file2), "Second file should not be cleaned due to throttle");
    }

    @Test
    void cleanupWhenCapacityIsZeroShouldSkipSizeBasedEviction() throws IOException {
        Path dir = ttsSpeechService.cacheDir();
        Files.createDirectories(dir);

        Path file = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file, new byte[1_000_000]);

        ReflectionTestUtils.setField(ttsSpeechService, "maxBytes", 0L);

        ttsSpeechService.cleanup();

        assertTrue(Files.exists(file), "File should not be deleted when maxBytes=0");
    }

    @Test
    void cleanupIfDueShouldRepeatedlyRunAfterInterval() throws IOException, InterruptedException {
        Path dir = ttsSpeechService.cacheDir();
        Files.createDirectories(dir);

        ReflectionTestUtils.setField(ttsSpeechService, "cleanupIntervalMs", 1L);

        ttsSpeechService.cleanupIfDue();

        Thread.sleep(1200);

        Path file = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file, new byte[1024]);
        FileTime oldTime = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        Files.setLastModifiedTime(file, oldTime);

        ttsSpeechService.cleanupIfDue();

        assertFalse(Files.exists(file), "Second cleanup should delete expired file");
    }
}
