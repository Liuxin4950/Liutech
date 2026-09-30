package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.DownloadUrlResp;
import chat.liuxin.liutech.service.ResourceDownloadService;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import java.io.ByteArrayInputStream;
import chat.liuxin.liutech.storage.DownloadFile;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResourceDownloadControllerTest {

    private ResourceDownloadController controller;
    private ResourceDownloadService resourceDownloadService;
    private UserUtils userUtils;

    @BeforeEach
    void setUp() {
        resourceDownloadService = mock(ResourceDownloadService.class);
        userUtils = mock(UserUtils.class);
        controller = new ResourceDownloadController(resourceDownloadService, userUtils);
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

        // Controller 不再自行包装异常，交由 GlobalExceptionHandler 统一转响应
        assertThrows(RuntimeException.class, () -> controller.purchaseResource(10L));
    }

    @Test
    void purchaseResource_shouldFailWhenUserNotLoggedIn() {
        when(userUtils.getCurrentUserId()).thenReturn(null);
        doThrow(new RuntimeException("用户不存在")).when(resourceDownloadService)
                .purchaseResource(isNull(), eq(10L));

        assertThrows(RuntimeException.class, () -> controller.purchaseResource(10L));
    }

    // ========== downloadResource ==========

    @Test
    void downloadResource_shouldReturnFileWhenValid() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(resourceDownloadService.downloadResource(1L, 10L))
                .thenReturn(new DownloadFile("中文文件.zip", 4L, new ByteArrayInputStream(new byte[4])));

        ResponseEntity<Resource> result = controller.downloadResource(10L);

        assertEquals(200, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertEquals(4, result.getHeaders().getContentLength());
        assertTrue(result.getHeaders().getFirst("Content-Disposition").contains("filename*=UTF-8"));
    }

    @Test
    void downloadResource_shouldPropagateFailureToGlobalHandler() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(resourceDownloadService.downloadResource(1L, 999L))
                .thenThrow(new RuntimeException("资源不存在"));

        assertThrows(RuntimeException.class, () -> controller.downloadResource(999L));
    }

    // ========== getDownloadUrl ==========

    @Test
    void getDownloadUrl_shouldReturnSignedUrlWhenValid() {
        DownloadUrlResp resp = new DownloadUrlResp("https://bucket.cos.example.com/resource.zip?sign=abc", 1700000000000L);
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(resourceDownloadService.getDownloadUrl(1L, 10L)).thenReturn(resp);

        Result<DownloadUrlResp> result = controller.getDownloadUrl(10L);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(resp, result.getData());
    }

    @Test
    void getDownloadUrl_shouldFailWhenServiceThrows() {
        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(resourceDownloadService.getDownloadUrl(1L, 999L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "请先购买该资源"));

        // 服务层领域错误保持语义，交给全局处理。
        BusinessException ex = assertThrows(BusinessException.class, () -> controller.getDownloadUrl(999L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("请先购买该资源", ex.getMessage());
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
