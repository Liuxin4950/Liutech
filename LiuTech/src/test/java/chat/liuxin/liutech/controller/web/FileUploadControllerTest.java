package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.FileUploadResp;
import chat.liuxin.liutech.service.FileUploadService;
import chat.liuxin.liutech.utils.UserUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileUploadControllerTest {

    private FileUploadController controller;
    private FileUploadService fileUploadService;
    private UserUtils userUtils;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        fileUploadService = mock(FileUploadService.class);
        userUtils = mock(UserUtils.class);
        request = new MockHttpServletRequest();
        controller = new FileUploadController(fileUploadService, userUtils);
    }

    // ========== uploadImage ==========

    @Test
    void uploadImage_shouldSucceedWithValidFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "image data".getBytes());

        FileUploadResp resp = new FileUploadResp(
                "test.jpg", "/uploads/images/2026/06/test.jpg",
                "https://liuxin.chat/uploads/images/2026/06/test.jpg",
                1024L, "image/jpeg", "jpg", System.currentTimeMillis(),
                null, null, 1L, false);

        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(fileUploadService.uploadImage(eq(file), eq(1L))).thenReturn(resp);

        Result<FileUploadResp> result = controller.uploadImage(file, request);

        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("test.jpg", result.getData().getFileName());
        assertNotNull(result.getData().getFileUrl());
    }

    @Test
    void uploadImage_shouldFailWhenServiceThrows() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "image data".getBytes());

        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(fileUploadService.uploadImage(eq(file), eq(1L)))
                .thenThrow(new RuntimeException("上传失败"));

        assertThrows(RuntimeException.class, () -> controller.uploadImage(file, request));
    }

    // ========== uploadImageForTinyMCE ==========

    @Test
    void uploadImageForTinyMCE_shouldReturnTinyMCEFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "editor.png", "image/png", "png data".getBytes());

        FileUploadResp resp = new FileUploadResp(
                "editor.png", "/uploads/images/2026/06/editor.png",
                "https://liuxin.chat/uploads/images/2026/06/editor.png",
                2048L, "image/png", "png", System.currentTimeMillis(),
                null, null, 2L, false);

        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(fileUploadService.uploadImage(eq(file), eq(1L))).thenReturn(resp);

        Object result = controller.uploadImageForTinyMCE(file, request);

        assertInstanceOf(FileUploadController.TinyMCEResponse.class, result);
        FileUploadController.TinyMCEResponse tinyResult = (FileUploadController.TinyMCEResponse) result;
        assertEquals("https://liuxin.chat/uploads/images/2026/06/editor.png", tinyResult.getLocation());
    }

    @Test
    void uploadImageForTinyMCE_shouldReturnErrorFormatWhenFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.txt", "text/plain", "not an image".getBytes());

        when(userUtils.getCurrentUserId()).thenReturn(1L);
        when(fileUploadService.uploadImage(eq(file), eq(1L)))
                .thenThrow(new RuntimeException("不支持的文件格式"));

        Object result = controller.uploadImageForTinyMCE(file, request);

        assertInstanceOf(FileUploadController.TinyMCEErrorResponse.class, result);
        FileUploadController.TinyMCEErrorResponse errorResult = (FileUploadController.TinyMCEErrorResponse) result;
        assertEquals("不支持的文件格式", errorResult.getError());
    }
}
