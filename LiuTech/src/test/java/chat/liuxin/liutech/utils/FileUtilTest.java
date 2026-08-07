package chat.liuxin.liutech.utils;

import chat.liuxin.liutech.config.CosStorageProperties;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        CosStorageProperties cosProps = new CosStorageProperties();
        cosProps.setRegion("ap-chongqing");
        cosProps.setBucket("liutech-1341692466");
        cosProps.setBaseUrl("https://static.liuxin.chat"); // 模拟生产配置的自定义访问域名
        fileUtil = new FileUtil(config, mock(FileStorage.class), cosProps);
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
    void normalize_COS直出URL() {
        assertEquals("images/2026/01/01/a.jpg",
                fileUtil.normalizeToRelativePath("https://static.liuxin.chat/images/2026/01/01/a.jpg"));
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
    void normalize_畸形双重前缀URL_主站域加COS域() {
        // 粘贴来源拼了双重前缀：https://liuxin.chat + https://static.liuxin.chat/...
        assertEquals("images/2026/08/04/a.jpg",
                fileUtil.normalizeToRelativePath(
                        "https://liuxin.chathttps://static.liuxin.chat/images/2026/08/04/a.jpg"));
    }

    @Test
    void normalize_畸形双重前缀URL_路径带uploads() {
        assertEquals("images/a.jpg",
                fileUtil.normalizeToRelativePath(
                        "https://liuxin.chathttps://static.liuxin.chat/uploads/images/a.jpg"));
    }

    @Test
    void normalize_畸形三重前缀URL() {
        assertEquals("images/a.jpg",
                fileUtil.normalizeToRelativePath(
                        "https://liuxin.chathttps://liuxin.chathttps://static.liuxin.chat/images/a.jpg"));
    }

    @Test
    void normalize_畸形前缀_外部域名不剥离() {
        // 双前缀但尾段 host 不是系统域名：保持忽略
        assertNull(fileUtil.normalizeToRelativePath("https://example.comhttps://evil.com/images/a.jpg"));
    }

    @Test
    void normalize_空值返回null() {
        assertNull(fileUtil.normalizeToRelativePath(null));
        assertNull(fileUtil.normalizeToRelativePath(""));
    }

    @Test
    void extractImageUrls_兼容三种URL形态() {
        String content = "<p>本地图</p>"
                + "<img src=\"/uploads/images/a.jpg\">"
                + "<img src='https://www.liuxin.chat/uploads/images/b.jpg'>"
                + "<img src=\"https://static.liuxin.chat/images/c.jpg\">"
                + "<img src=\"https://example.com/uploads/outside.png\">"
                + "![md图](/uploads/images/d.jpg)";

        List<String> urls = fileUtil.extractImageUrls(content);

        assertEquals(4, urls.size());
        assertEquals("/uploads/images/a.jpg", urls.get(0));
        assertEquals("https://www.liuxin.chat/uploads/images/b.jpg", urls.get(1));
        assertEquals("https://static.liuxin.chat/images/c.jpg", urls.get(2));
        assertEquals("/uploads/images/d.jpg", urls.get(3));
    }

    @Test
    void extractImageUrls_同图多次出现按次数返回() {
        // 不去重：同一 URL 出现 N 次返回 N 次，创建/删除文章按出现次数增减引用计数
        String content = "<p>图一</p>"
                + "<img src=\"/uploads/images/a.jpg\">"
                + "<p>图二</p>"
                + "<img src=\"/uploads/images/a.jpg\">"
                + "<img src=\"/uploads/images/b.jpg\">";

        List<String> urls = fileUtil.extractImageUrls(content);

        assertEquals(3, urls.size());
        assertEquals("/uploads/images/a.jpg", urls.get(0));
        assertEquals("/uploads/images/a.jpg", urls.get(1));
        assertEquals("/uploads/images/b.jpg", urls.get(2));
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
