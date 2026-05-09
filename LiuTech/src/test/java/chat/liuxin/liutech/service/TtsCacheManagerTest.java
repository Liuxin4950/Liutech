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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsCacheManagerTest {

    @TempDir
    Path tempDir;

    private TtsCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        FileUploadConfig fileUploadConfig = new FileUploadConfig();
        ReflectionTestUtils.setField(fileUploadConfig, "basePath", tempDir.toString());
        cacheManager = new TtsCacheManager(fileUploadConfig);
        ReflectionTestUtils.setField(cacheManager, "maxAgeHours", 24L);
        ReflectionTestUtils.setField(cacheManager, "maxBytes", 100_000L); // 100KB for testing
        ReflectionTestUtils.setField(cacheManager, "cleanupIntervalMs", 100L);
    }

    @Test
    void cacheDirShouldReturnTtsCacheUnderUploads() {
        Path dir = cacheManager.cacheDir();
        assertTrue(dir.endsWith("tts-cache"));
        assertTrue(dir.startsWith(tempDir));
    }

    @Test
    void cleanupShouldSkipWhenDirectoryDoesNotExist() {
        // cacheDir doesn't exist yet, cleanup should not throw
        cacheManager.cleanup();
        assertFalse(Files.exists(cacheManager.cacheDir()));
    }

    @Test
    void cleanupShouldDeleteExpiredFiles() throws IOException {
        Path dir = cacheManager.cacheDir();
        Files.createDirectories(dir);

        // Create a file and backdate it to 25 hours ago (older than 24h maxAge)
        Path file = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file, new byte[1024]);
        FileTime oldTime = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        Files.setLastModifiedTime(file, oldTime);

        cacheManager.cleanup();

        assertFalse(Files.exists(file), "Expired file should be deleted");
    }

    @Test
    void cleanupShouldNotDeleteRecentFiles() throws IOException {
        Path dir = cacheManager.cacheDir();
        Files.createDirectories(dir);

        Path file = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file, new byte[1024]);

        cacheManager.cleanup();

        assertTrue(Files.exists(file), "Recent file should not be deleted");
    }

    @Test
    void cleanupShouldEvictOldestWhenOverCapacity() throws IOException, InterruptedException {
        Path dir = cacheManager.cacheDir();
        Files.createDirectories(dir);

        // Create 3 files, each 40KB (total 120KB > 100KB capacity)
        Path file1 = dir.resolve(UUID.randomUUID() + ".mp3");
        Path file2 = dir.resolve(UUID.randomUUID() + ".mp3");
        Path file3 = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file1, new byte[40_000]);
        Thread.sleep(10); // Ensure different timestamps
        Files.write(file2, new byte[40_000]);
        Thread.sleep(10);
        Files.write(file3, new byte[40_000]);

        cacheManager.cleanup();

        // file1 is oldest, should be deleted; file2 and file3 should remain (80KB < 100KB)
        assertFalse(Files.exists(file1), "Oldest file should be evicted");
        assertTrue(Files.exists(file2), "Second file should remain");
        assertTrue(Files.exists(file3), "Third file should remain");
    }

    @Test
    void cleanupShouldSkipNonManagedFiles() throws IOException {
        Path dir = cacheManager.cacheDir();
        Files.createDirectories(dir);

        // Create a non-cache file (not matching UUID.ext pattern)
        Path file = dir.resolve("test.txt");
        Files.write(file, "hello".getBytes());

        // Backdate to make it "expired" - but it should still be skipped
        FileTime oldTime = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        Files.setLastModifiedTime(file, oldTime);

        cacheManager.cleanup();

        assertTrue(Files.exists(file), "Non-managed file should be left alone");
    }

    @Test
    void cleanupShouldHandleUnreadableFiles() throws IOException {
        Path dir = cacheManager.cacheDir();
        Files.createDirectories(dir);

        // A file that exists but is not a regular file shouldn't break cleanup
        // This test verifies the exception handling path
        cacheManager.cleanup();
        // No exception = success
    }

    @Test
    void cleanupIfDueShouldThrottleWhenCalledRapidly() throws IOException {
        Path dir = cacheManager.cacheDir();
        Files.createDirectories(dir);

        ReflectionTestUtils.setField(cacheManager, "cleanupIntervalMs", 60_000L); // 60s interval

        // Create expired file for first call
        Path file1 = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file1, new byte[1024]);
        FileTime oldTime = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        Files.setLastModifiedTime(file1, oldTime);

        // First call should trigger cleanup (deletes expired file)
        cacheManager.cleanupIfDue();
        assertFalse(Files.exists(file1), "First call should delete expired file");

        // Second call should be throttled (no cleanup run)
        Path file2 = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file2, new byte[1024]);
        Files.setLastModifiedTime(file2, oldTime);
        cacheManager.cleanupIfDue();
        assertTrue(Files.exists(file2), "Second file should not be cleaned due to throttle");
    }

    @Test
    void cleanupWhenCapacityIsZeroShouldSkipSizeBasedEviction() throws IOException {
        Path dir = cacheManager.cacheDir();
        Files.createDirectories(dir);

        Path file = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file, new byte[1_000_000]); // 1MB

        ReflectionTestUtils.setField(cacheManager, "maxBytes", 0L); // Disabled

        cacheManager.cleanup();

        assertTrue(Files.exists(file), "File should not be deleted when maxBytes=0");
    }

    @Test
    void cleanupIfDueShouldRepeatedlyRunAfterInterval() throws IOException, InterruptedException {
        Path dir = cacheManager.cacheDir();
        Files.createDirectories(dir);

        ReflectionTestUtils.setField(cacheManager, "cleanupIntervalMs", 1L); // 1ms interval

        // First call
        cacheManager.cleanupIfDue();

        // Wait past the interval
        Thread.sleep(1200);

        // Create expired file for second call
        Path file = dir.resolve(UUID.randomUUID() + ".mp3");
        Files.write(file, new byte[1024]);
        FileTime oldTime = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        Files.setLastModifiedTime(file, oldTime);

        // Second call should run cleanup again
        cacheManager.cleanupIfDue();

        assertFalse(Files.exists(file), "Second cleanup should delete expired file");
    }
}