package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.Images;
import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.FileUploadResp;
import chat.liuxin.liutech.resp.ImageUploadResult;
import chat.liuxin.liutech.storage.FileStorage;
import chat.liuxin.liutech.utils.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileUploadService 单元测试
 * 覆盖图片上传、用户校验、文件校验等核心逻辑
 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {
    @TempDir Path tempDirectory;

    @Test void chunksRejectTraversalAndAreIsolatedByUser() throws Exception {
        Users user = new Users();
        user.setId(1L);
        when(userMapper.selectById(anyLong())).thenReturn(user);
        var file = new MockMultipartFile("file", "test.zip", "application/zip", new byte[]{1});
        for (String invalid : new String[]{"..", "../escape", "a/b", "a\\b", "/absolute", "a.b"}) {
            assertThrows(BusinessException.class,
                    () -> fileUploadService.uploadResourceChunk(file, 1L, invalid, 0, 1, "test.zip"));
            assertThrows(BusinessException.class,
                    () -> fileUploadService.mergeResourceChunks(1L, invalid, 1, "test.zip", null, null, null, 0, 0));
        }
        when(fileUploadConfig.getBasePath()).thenReturn(tempDirectory.toString());
        when(fileUtil.isAllowedResourceType("test.zip")).thenReturn(true);
        fileUploadService.uploadResourceChunk(file, 1L, "task-1", 0, 1, "test.zip");
        assertTrue(Files.exists(tempDirectory.resolve("tmp/1/task-1/0.part")));
        assertThrows(BusinessException.class,
                () -> fileUploadService.mergeResourceChunks(2L, "task-1", 1, "test.zip", null, null, null, 0, 0));
        assertTrue(Files.exists(tempDirectory.resolve("tmp/1/task-1/0.part")));
    }

    @Test void mergeStreamsInOrderAndKeepsChunksWhenDatabaseFails() throws Exception {
        Users user = new Users();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(fileUploadConfig.getBasePath()).thenReturn(tempDirectory.toString());
        when(fileUploadConfig.getMaxFileSize()).thenReturn(100L);
        when(fileUploadConfig.getResourcePath()).thenReturn("resources");
        when(fileUtil.isAllowedResourceType("test.zip")).thenReturn(true);
        fileUploadService.uploadResourceChunk(new MockMultipartFile("file", new byte[]{1, 2}), 1L, "task", 0, 2, "test.zip");
        fileUploadService.uploadResourceChunk(new MockMultipartFile("file", new byte[]{3, 4}), 1L, "task", 1, 2, "test.zip");
        AtomicReference<byte[]> saved = new AtomicReference<>();
        when(fileStorage.save(any(byte[].class), eq("resources"), eq("test.zip"))).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return "resources/test.zip";
        });
        when(resourcesMapper.insert(any(Resources.class))).thenThrow(new IllegalStateException("DB failed"));
        assertThrows(IllegalStateException.class,
                () -> fileUploadService.mergeResourceChunks(1L, "task", 2, "test.zip", null, null, null, 0, 0));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, saved.get());
        verify(fileStorage).delete("resources/test.zip");
        assertTrue(Files.exists(tempDirectory.resolve("tmp/1/task/0.part")));
        assertTrue(Files.exists(tempDirectory.resolve("tmp/1/task/1.part")));
    }

    @Mock
    private FileUtil fileUtil;

    @Mock
    private FileUploadConfig fileUploadConfig;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ResourcesMapper resourcesMapper;

    @Mock
    private PostAttachmentsMapper postAttachmentsMapper;

    @Mock
    private ImagesService imagesService;

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private FileUploadService fileUploadService;

    // ========== uploadImage 测试 ==========

    @Test
    void uploadImage_shouldUploadSuccessfully() throws Exception {
        Long userId = 1L;

        Users user = new Users();
        user.setId(userId);
        user.setUsername("testuser");

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        when(file.getSize()).thenReturn(1024L);
        when(file.isEmpty()).thenReturn(false);

        Images image = new Images();
        image.setId(100L);
        image.setFileName("test.jpg");
        image.setFilePath("images/test.jpg");
        image.setFileUrl("http://localhost:8080/uploads/images/test.jpg");
        image.setFileSize(1024L);
        image.setExtension("jpg");

        ImageUploadResult uploadResult = new ImageUploadResult(image, false);

        when(userMapper.selectById(userId)).thenReturn(user);
        when(fileUtil.isAllowedImageType("test.jpg")).thenReturn(true);
        when(fileUtil.isValidFileSize(1024L, 10 * 1024 * 1024L)).thenReturn(true);
        when(fileUploadConfig.getImagePath()).thenReturn("images");
        when(fileUploadConfig.getMaxImageSize()).thenReturn(10 * 1024 * 1024L);
        when(imagesService.uploadImage(file, userId, "images")).thenReturn(uploadResult);

        FileUploadResp result = fileUploadService.uploadImage(file, userId);

        assertNotNull(result);
        assertEquals("test.jpg", result.getFileName());
        assertEquals("images/test.jpg", result.getFilePath());
        assertEquals(100L, result.getImageId());
        assertFalse(result.getIsDuplicate());
        assertEquals("image", result.getFileType());

        verify(userMapper).selectById(userId);
        verify(imagesService).uploadImage(file, userId, "images");
    }

    @Test
    void uploadImage_shouldThrowWhenUserIdIsNull() {
        MultipartFile file = mock(MultipartFile.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.uploadImage(file, null));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verifyNoInteractions(imagesService);
    }

    @Test
    void uploadImage_shouldThrowWhenUserNotFound() {
        Long userId = 999L;

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.jpg");

        when(userMapper.selectById(userId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.uploadImage(file, userId));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verifyNoInteractions(imagesService);
    }

    @Test
    void uploadImage_shouldThrowWhenUserIsSoftDeleted() {
        Long userId = 1L;

        Users deletedUser = new Users();
        deletedUser.setId(userId);
        deletedUser.setDeletedAt(new java.util.Date());

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.jpg");

        when(userMapper.selectById(userId)).thenReturn(deletedUser);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.uploadImage(file, userId));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verifyNoInteractions(imagesService);
    }

    @Test
    void uploadImage_shouldThrowWhenFileIsEmpty() {
        Long userId = 1L;

        Users user = new Users();
        user.setId(userId);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        when(userMapper.selectById(userId)).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.uploadImage(file, userId));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verifyNoInteractions(imagesService);
    }

    @Test
    void uploadImage_shouldReturnDuplicateFlagWhenImageAlreadyExists() throws Exception {
        Long userId = 1L;

        Users user = new Users();
        user.setId(userId);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("existing.png");
        when(file.getSize()).thenReturn(2048L);
        when(file.isEmpty()).thenReturn(false);

        Images image = new Images();
        image.setId(50L);
        image.setFileName("existing.png");
        image.setFilePath("images/existing.png");
        image.setFileUrl("http://localhost:8080/uploads/images/existing.png");
        image.setFileSize(2048L);
        image.setExtension("png");

        ImageUploadResult uploadResult = new ImageUploadResult(image, true);

        when(userMapper.selectById(userId)).thenReturn(user);
        when(fileUtil.isAllowedImageType("existing.png")).thenReturn(true);
        when(fileUtil.isValidFileSize(2048L, 10 * 1024 * 1024L)).thenReturn(true);
        when(fileUploadConfig.getImagePath()).thenReturn("images");
        when(fileUploadConfig.getMaxImageSize()).thenReturn(10 * 1024 * 1024L);
        when(imagesService.uploadImage(file, userId, "images")).thenReturn(uploadResult);

        FileUploadResp result = fileUploadService.uploadImage(file, userId);

        assertNotNull(result);
        assertTrue(result.getIsDuplicate());
        assertEquals(50L, result.getImageId());
    }
}
