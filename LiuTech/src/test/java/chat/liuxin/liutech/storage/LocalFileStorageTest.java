package chat.liuxin.liutech.storage;

import chat.liuxin.liutech.config.FileUploadConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalFileStorageTest {
    @TempDir Path directory;

    @Test void streamedUploadIsCompleteAndFailedCopyDoesNotLeaveFile() throws Exception {
        FileUploadConfig config = mock(FileUploadConfig.class);
        when(config.getBasePath()).thenReturn(directory.toString());
        LocalFileStorage storage = new LocalFileStorage(config);
        String relative = storage.save(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "resources", "test.zip");
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(directory.resolve(relative)));
        assertThrows(java.io.IOException.class,
                () -> storage.save(new ByteArrayInputStream(new byte[]{1}), 3, "resources", "bad.zip"));
        try (var files = Files.walk(directory)) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }
}
