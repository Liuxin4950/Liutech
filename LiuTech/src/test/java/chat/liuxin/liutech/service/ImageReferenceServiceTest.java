package chat.liuxin.liutech.service;

import chat.liuxin.liutech.utils.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图片引用计数服务测试
 * 验证：URL 计数、delta 同步、路径归一化归并
 * （ImagesService 的 DB 调用全部 mock，纯逻辑验证）
 */
class ImageReferenceServiceTest {

    @Mock
    private ImagesService imagesService;

    @Mock
    private FileUtil fileUtil;

    @InjectMocks
    private ImageReferenceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void addReferences_每个URL加一且重复累加() {
        service.addReferences(List.of(
                "/uploads/images/2026/01/01/a.jpg",
                "/uploads/images/2026/01/01/a.jpg",
                "/uploads/images/2026/01/01/b.jpg"));

        verify(imagesService).incrementImageUsageCountByUrl("/uploads/images/2026/01/01/a.jpg", 2);
        verify(imagesService).incrementImageUsageCountByUrl("/uploads/images/2026/01/01/b.jpg", 1);
        verify(imagesService, org.mockito.Mockito.times(2)).incrementImageUsageCountByUrl(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void removeReferences_计数为负() {
        service.removeReferences(List.of(
                "/uploads/images/2026/01/01/a.jpg",
                "/uploads/images/2026/01/01/a.jpg"));

        verify(imagesService).incrementImageUsageCountByUrl("/uploads/images/2026/01/01/a.jpg", -2);
    }

    @Test
    void syncReferences_新增移除不变() {
        // 旧引用：a、b；新引用：a、a、c → delta: a=+1, b=-1, c=+1
        service.syncReferences(
                List.of("/uploads/a.jpg", "/uploads/b.jpg"),
                List.of("/uploads/a.jpg", "/uploads/a.jpg", "/uploads/c.jpg"));

        verify(imagesService).incrementImageUsageCountByUrl("/uploads/a.jpg", 1);
        verify(imagesService).incrementImageUsageCountByUrl("/uploads/b.jpg", -1);
        verify(imagesService).incrementImageUsageCountByUrl("/uploads/c.jpg", 1);
        verify(imagesService, org.mockito.Mockito.times(3)).incrementImageUsageCountByUrl(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void syncReferences_引用不变时不调用() {
        service.syncReferences(
                List.of("/uploads/a.jpg", "/uploads/b.jpg"),
                List.of("/uploads/b.jpg", "/uploads/a.jpg"));

        verify(imagesService, never()).incrementImageUsageCountByUrl(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void addReferences_空列表不调用() {
        service.addReferences(null);
        service.addReferences(List.of());
        service.addReferences(List.of("", "  "));

        verify(imagesService, never()).incrementImageUsageCountByUrl(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void countByPath_多种URL写法归并到同一路径() {
        // 归一化由 FileUtil 负责（见 FileUtilTest），此处验证服务层按归一化结果归并计数
        when(fileUtil.normalizeToRelativePath("/uploads/images/a.jpg")).thenReturn("images/a.jpg");
        when(fileUtil.normalizeToRelativePath("https://www.liuxin.chat/uploads/images/a.jpg")).thenReturn("images/a.jpg");
        when(fileUtil.normalizeToRelativePath("images/a.jpg")).thenReturn("images/a.jpg");
        when(fileUtil.normalizeToRelativePath("http://example.com/other/b.jpg")).thenReturn(null);

        Map<String, Integer> counts = service.countByPath(List.of(
                "/uploads/images/a.jpg",
                "https://www.liuxin.chat/uploads/images/a.jpg",
                "images/a.jpg",
                "http://example.com/other/b.jpg")); // 外部 URL 归一化为 null，应被忽略

        assertEquals(3, counts.get("images/a.jpg"), "三种写法应归并为 images/a.jpg 计 3 次");
        assertEquals(1, counts.size(), "外部 URL 不应计入");
    }

    @Test
    void countByPath_重复路径累加() {
        when(fileUtil.normalizeToRelativePath("/uploads/images/a.jpg")).thenReturn("images/a.jpg");
        when(fileUtil.normalizeToRelativePath("/uploads/images/b.jpg")).thenReturn("images/b.jpg");

        Map<String, Integer> counts = service.countByPath(List.of(
                "/uploads/images/a.jpg",
                "/uploads/images/a.jpg",
                "/uploads/images/b.jpg"));

        assertEquals(2, counts.get("images/a.jpg"));
        assertEquals(1, counts.get("images/b.jpg"));
    }
}
