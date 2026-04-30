package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.ResourceDownloadsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.model.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceDownloadServiceTest {

    private ResourceDownloadService service;
    private ResourcesMapper resourcesMapper;
    private ResourceDownloadsMapper resourceDownloadsMapper;

    @TempDir
    Path uploadDir;

    @BeforeEach
    void setUp() {
        resourcesMapper = mock(ResourcesMapper.class);
        resourceDownloadsMapper = mock(ResourceDownloadsMapper.class);
        service = new ResourceDownloadService();
        ReflectionTestUtils.setField(service, "resourcesMapper", resourcesMapper);
        ReflectionTestUtils.setField(service, "resourceDownloadsMapper", resourceDownloadsMapper);
        ReflectionTestUtils.setField(service, "pointsService", mock(PointsService.class));
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    @Test
    void shouldDownloadResourceInsideUploadDir() throws Exception {
        Path file = uploadDir.resolve("resources/2026/04/demo.zip");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "demo");

        Resources resource = new Resources();
        resource.setName("demo.zip");
        resource.setFileUrl("https://liuxin.chat/uploads/resources/2026/04/demo.zip");
        resource.setDownloadType(0);
        when(resourcesMapper.selectById(1L)).thenReturn(resource);

        ResponseEntity<Resource> response = service.downloadResource(10L, 1L);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().exists());
    }

    @Test
    void shouldRejectResourcePathEscapingUploadDir() {
        Resources resource = new Resources();
        resource.setName("bad.zip");
        resource.setFileUrl("https://liuxin.chat/uploads/resources/../../application.yml");
        resource.setDownloadType(0);
        when(resourcesMapper.selectById(2L)).thenReturn(resource);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.downloadResource(10L, 2L));

        assertEquals("非法资源路径", error.getMessage());
    }

    @Test
    void shouldRequirePurchaseWhenDownloadTypeIsUnexpectedButPointsArePositive() {
        Resources resource = new Resources();
        resource.setName("paid.zip");
        resource.setFileUrl("https://liuxin.chat/uploads/resources/2026/04/paid.zip");
        resource.setDownloadType(2);
        resource.setPointsNeeded(java.math.BigDecimal.TEN);
        when(resourcesMapper.selectById(3L)).thenReturn(resource);
        when(resourceDownloadsMapper.countUserPurchase(10L, 3L)).thenReturn(0);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.downloadResource(10L, 3L));

        assertEquals("请先购买该资源", error.getMessage());
    }
}
