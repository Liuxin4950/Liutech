package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.service.ResourceDownloadService;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResourceDownloadControllerTest {

    private ResourceDownloadController controller;
    private ResourceDownloadService resourceDownloadService;
    private UserUtils userUtils;

    @BeforeEach
    void setUp() {
        controller = new ResourceDownloadController();
        resourceDownloadService = mock(ResourceDownloadService.class);
        userUtils = mock(UserUtils.class);

        ReflectionTestUtils.setField(controller, "resourceDownloadService", resourceDownloadService);
        ReflectionTestUtils.setField(controller, "userUtils", userUtils);
    }

    // ========== purchaseResource ==========

    @Test
    void purchaseResource_shouldSucceedWhenValid() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        doNothing().when(resourceDownloadService).purchaseResource(1L, 10L);

        Result<String> result = controller.purchaseResource(10L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        verify(resourceDownloadService).purchaseResource(1L, 10L);
    }

    @Test
    void purchaseResource_shouldFailWhenServiceThrows() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        doThrow(new RuntimeException("积分不足")).when(resourceDownloadService)
                .purchaseResource(1L, 10L);

        Result<String> result = controller.purchaseResource(10L);

        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("积分不足"));
    }

    @Test
    void purchaseResource_shouldFailWhenUserNotLoggedIn() {
        when(userUtils.getCurrentUserId()).thenReturn(null);
        doThrow(new RuntimeException("用户不存在")).when(resourceDownloadService)
                .purchaseResource(isNull(), eq(10L));

        Result<String> result = controller.purchaseResource(10L);

        assertEquals(500, result.getCode());
    }

    // ========== downloadResource ==========

    @Test
    void downloadResource_shouldReturnFileWhenValid() {
        Resource mockResource = mock(Resource.class);
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(resourceDownloadService.downloadResource(1L, 10L))
                .thenReturn(ResponseEntity.ok(mockResource));

        ResponseEntity<Resource> result = controller.downloadResource(10L);

        assertEquals(200, result.getStatusCode().value());
        assertNotNull(result.getBody());
    }

    @Test
    void downloadResource_shouldReturnBadRequestWhenServiceThrows() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(resourceDownloadService.downloadResource(1L, 999L))
                .thenThrow(new RuntimeException("资源不存在"));

        ResponseEntity<Resource> result = controller.downloadResource(999L);

        assertEquals(400, result.getStatusCode().value());
        assertNull(result.getBody());
    }

    // ========== checkPurchaseStatus ==========

    @Test
    void checkPurchaseStatus_shouldReturnTrueWhenPurchased() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(resourceDownloadService.hasUserPurchased(1L, 10L)).thenReturn(true);

        Result<Boolean> result = controller.checkPurchaseStatus(10L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData());
    }

    @Test
    void checkPurchaseStatus_shouldReturnFalseWhenNotPurchased() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(resourceDownloadService.hasUserPurchased(1L, 10L)).thenReturn(false);

        Result<Boolean> result = controller.checkPurchaseStatus(10L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertFalse(result.getData());
    }
}
