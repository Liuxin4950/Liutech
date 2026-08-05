package chat.liuxin.liutech.utils;

import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * FileUtil 路径归一化测试
 * 所有引用计数/对账/溯源共用 normalizeToRelativePath，口径必须稳定
 */
class FileUtilTest {

    private FileUtil fileUtil;

    @BeforeEach
    void setUp() {
        FileUploadConfig config = new FileUploadConfig();
        config.setUrlPrefix("/uploads");
        config.setServerBaseUrl("https://www.liuxin.chat");
        fileUtil = new FileUtil(config, mock(FileStorage.class));
    }

    @Test
    void normalize_相对路径() {
        assertEquals("images/2026/01/01/a.jpg",
                fileUtil.normalizeToRelativePath("/uploads/images/2026/01/01/a.jpg"));
    }

    @Test
    void normalize_站内完整URL() {
        assertEquals("images/2026/01/01/a.jpg",
                fileUtil.normalizeToRelativePath("https://www.liuxin.chat/uploads/images/2026/01/01/a.jpg"));
    }

    @Test
    void normalize_裸路径() {
        assertEquals("images/2026/01/01/a.jpg",
                fileUtil.normalizeToRelativePath("images/2026/01/01/a.jpg"));
    }

    @Test
    void normalize_外部URL被忽略() {
        assertNull(fileUtil.normalizeToRelativePath("https://example.com/uploads/images/a.jpg"));
    }

    @Test
    void normalize_空值返回null() {
        assertNull(fileUtil.normalizeToRelativePath(null));
        assertNull(fileUtil.normalizeToRelativePath(""));
    }

    @Test
    void detectImageFormat_各格式识别() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        byte[] gif = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0};
        byte[] bmp = {0x42, 0x4D, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] webp = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};

        assertEquals("jpg", fileUtil.detectImageFormat(jpeg));
        assertEquals("png", fileUtil.detectImageFormat(png));
        assertEquals("gif", fileUtil.detectImageFormat(gif));
        assertEquals("bmp", fileUtil.detectImageFormat(bmp));
        assertEquals("webp", fileUtil.detectImageFormat(webp));
        assertNull(fileUtil.detectImageFormat(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
    }
}
