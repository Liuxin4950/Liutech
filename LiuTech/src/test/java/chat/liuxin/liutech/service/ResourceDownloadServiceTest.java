package chat.liuxin.liutech.service;

import chat.liuxin.liutech.mapper.ResourceDownloadsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.model.ResourceDownloads;
import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.storage.FileStorage;
import chat.liuxin.liutech.utils.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResourceDownloadServiceTest {

    private ResourceDownloadService service;
    private ResourcesMapper resourcesMapper;
    private ResourceDownloadsMapper resourceDownloadsMapper;
    private PointsService pointsService;
    private FileStorage fileStorage;
    private FileUtil fileUtil;

    private static final Long USER_ID = 10L;
    private static final Long RESOURCE_ID = 1L;
    private static final Long UPLOADER_ID = 2L;

    @BeforeEach
    void setUp() {
        resourcesMapper = mock(ResourcesMapper.class);
        resourceDownloadsMapper = mock(ResourceDownloadsMapper.class);
        pointsService = mock(PointsService.class);
        fileStorage = mock(FileStorage.class);
        fileUtil = mock(FileUtil.class);
        service = new ResourceDownloadService(resourcesMapper, resourceDownloadsMapper, pointsService, fileStorage, fileUtil);
    }

    private Resources createPaidResource() {
        Resources resource = new Resources();
        resource.setId(RESOURCE_ID);
        resource.setName("付费资源.zip");
        resource.setDownloadType(1);
        resource.setPointsNeeded(BigDecimal.valueOf(20));
        resource.setUploaderId(UPLOADER_ID);
        return resource;
    }

    private Resources createFreeResource() {
        Resources resource = new Resources();
        resource.setId(RESOURCE_ID);
        resource.setName("免费资源.zip");
        resource.setDownloadType(0);
        resource.setPointsNeeded(BigDecimal.ZERO);
        resource.setUploaderId(UPLOADER_ID);
        return resource;
    }

    // ========== downloadResource 路径安全测试 ==========

    @Test
    void shouldDownloadResourceViaStorage() throws Exception {
        Resources resource = new Resources();
        resource.setName("demo.zip");
        resource.setFileUrl("https://liuxin.chat/uploads/resources/2026/04/demo.zip");
        resource.setDownloadType(0);
        when(resourcesMapper.selectById(1L)).thenReturn(resource);
        when(fileUtil.extractRelativePath("https://liuxin.chat/uploads/resources/2026/04/demo.zip"))
                .thenReturn("resources/2026/04/demo.zip");
        when(fileStorage.open("resources/2026/04/demo.zip"))
                .thenReturn(new ByteArrayInputStream("demo".getBytes()));

        ResponseEntity<Resource> response = service.downloadResource(USER_ID, 1L);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().exists());
        verify(fileStorage).open("resources/2026/04/demo.zip");
    }

    @Test
    void shouldThrowWhenFileNotExistsInStorage() {
        Resources resource = new Resources();
        resource.setName("missing.zip");
        resource.setFileUrl("/uploads/resources/2026/04/missing.zip");
        resource.setDownloadType(0);
        when(resourcesMapper.selectById(1L)).thenReturn(resource);
        when(fileUtil.extractRelativePath("/uploads/resources/2026/04/missing.zip"))
                .thenReturn("resources/2026/04/missing.zip");
        when(fileStorage.open("resources/2026/04/missing.zip")).thenReturn(null);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.downloadResource(USER_ID, 1L));

        assertEquals("文件不存在", error.getMessage());
    }

    @Test
    void shouldRejectResourcePathEscapingUploadDir() {
        Resources resource = new Resources();
        resource.setName("bad.zip");
        resource.setFileUrl("https://liuxin.chat/uploads/resources/../../application.yml");
        resource.setDownloadType(0);
        when(resourcesMapper.selectById(2L)).thenReturn(resource);
        when(fileUtil.extractRelativePath("https://liuxin.chat/uploads/resources/../../application.yml"))
                .thenReturn("resources/../../application.yml");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.downloadResource(USER_ID, 2L));

        assertEquals("非法资源路径", error.getMessage());
        // 非法路径必须在校验层拦截，不触碰存储
        verify(fileStorage, never()).open(anyString());
    }

    @Test
    void shouldRequirePurchaseWhenDownloadTypeIsUnexpectedButPointsArePositive() {
        Resources resource = new Resources();
        resource.setName("paid.zip");
        resource.setFileUrl("https://liuxin.chat/uploads/resources/2026/04/paid.zip");
        resource.setDownloadType(2);
        resource.setPointsNeeded(BigDecimal.TEN);
        when(resourcesMapper.selectById(3L)).thenReturn(resource);
        when(resourceDownloadsMapper.countUserPurchase(USER_ID, 3L)).thenReturn(0);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.downloadResource(USER_ID, 3L));

        assertEquals("请先购买该资源", error.getMessage());
    }

    // ========== purchaseResource 测试 ==========

    @Test
    void purchaseResource_shouldSucceedForPaidResource() {
        Resources resource = createPaidResource();
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);
        when(resourceDownloadsMapper.insert(any(ResourceDownloads.class))).thenReturn(1);
        doNothing().when(pointsService).deductPoints(eq(USER_ID), eq(BigDecimal.valueOf(20)),
                eq(PointsService.SOURCE_RESOURCE_DOWNLOAD), eq(RESOURCE_ID), anyString());

        service.purchaseResource(USER_ID, RESOURCE_ID);

        verify(resourceDownloadsMapper).insert(any(ResourceDownloads.class));
        verify(pointsService).deductPoints(eq(USER_ID), eq(BigDecimal.valueOf(20)),
                eq(PointsService.SOURCE_RESOURCE_DOWNLOAD), eq(RESOURCE_ID), contains("付费资源"));
    }

    @Test
    void purchaseResource_shouldThrowWhenResourceNotFound() {
        when(resourcesMapper.selectById(999L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.purchaseResource(USER_ID, 999L));
        assertTrue(ex.getMessage().contains("资源不存在"));
    }

    @Test
    void purchaseResource_shouldThrowWhenResourceIsFree() {
        Resources resource = createFreeResource();
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.purchaseResource(USER_ID, RESOURCE_ID));
        assertTrue(ex.getMessage().contains("免费资源"));
    }

    @Test
    void purchaseResource_shouldThrowWhenUserIsUploader() {
        Resources resource = createPaidResource();
        resource.setUploaderId(USER_ID);
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.purchaseResource(USER_ID, RESOURCE_ID));
        assertTrue(ex.getMessage().contains("上传者"));
    }

    @Test
    void purchaseResource_shouldThrowOnDuplicatePurchase() {
        Resources resource = createPaidResource();
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);
        when(resourceDownloadsMapper.insert(any(ResourceDownloads.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.purchaseResource(USER_ID, RESOURCE_ID));
        assertTrue(ex.getMessage().contains("已购买"));
    }

    @Test
    void purchaseResource_shouldRollbackWhenPointsDeductionFails() {
        Resources resource = createPaidResource();
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);
        when(resourceDownloadsMapper.insert(any(ResourceDownloads.class))).thenReturn(1);
        doThrow(new RuntimeException("积分不足")).when(pointsService)
                .deductPoints(eq(USER_ID), any(), any(), any(), any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.purchaseResource(USER_ID, RESOURCE_ID));
        assertTrue(ex.getMessage().contains("积分扣减失败"));
    }

    @Test
    void purchaseResource_shouldThrowWhenInsertReturnsZero() {
        Resources resource = createPaidResource();
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);
        when(resourceDownloadsMapper.insert(any(ResourceDownloads.class))).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.purchaseResource(USER_ID, RESOURCE_ID));
        assertTrue(ex.getMessage().contains("购买记录创建失败"));
    }

    // ========== hasUserPurchased 测试 ==========

    @Test
    void hasUserPurchased_shouldReturnTrueForFreeResource() {
        Resources resource = createFreeResource();
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);

        assertTrue(service.hasUserPurchased(USER_ID, RESOURCE_ID));
    }

    @Test
    void hasUserPurchased_shouldReturnTrueForUploader() {
        Resources resource = createPaidResource();
        resource.setUploaderId(USER_ID);
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);

        assertTrue(service.hasUserPurchased(USER_ID, RESOURCE_ID));
    }

    @Test
    void hasUserPurchased_shouldReturnTrueWhenPurchased() {
        Resources resource = createPaidResource();
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);
        when(resourceDownloadsMapper.countUserPurchase(USER_ID, RESOURCE_ID)).thenReturn(1);

        assertTrue(service.hasUserPurchased(USER_ID, RESOURCE_ID));
    }

    @Test
    void hasUserPurchased_shouldReturnFalseWhenNotPurchased() {
        Resources resource = createPaidResource();
        when(resourcesMapper.selectById(RESOURCE_ID)).thenReturn(resource);
        when(resourceDownloadsMapper.countUserPurchase(USER_ID, RESOURCE_ID)).thenReturn(0);

        assertFalse(service.hasUserPurchased(USER_ID, RESOURCE_ID));
    }
}
