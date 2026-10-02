package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.config.CosStorageProperties;
import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.mapper.UserMapper;
import chat.liuxin.liutech.model.PostAttachments;
import chat.liuxin.liutech.model.Resources;
import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.FileUploadResp;
import chat.liuxin.liutech.storage.FileStorage;
import chat.liuxin.liutech.storage.LocalFileStorage;
import chat.liuxin.liutech.utils.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实临时磁盘验证路径边界、内容查重和 Spring 事务回调。 */
@ExtendWith(MockitoExtension.class)
class FileUploadResourceSafetyTest {
    @TempDir
    Path uploadRoot;
    @Mock
    UserMapper userMapper;
    @Mock
    ResourcesMapper resourcesMapper;
    @Mock
    PostAttachmentsMapper attachmentsMapper;
    @Mock
    ImagesService imagesService;

    FileUploadConfig config;
    FileStorage storage;
    FileUploadService service;

    @BeforeEach
    void setUp() {
        config = new FileUploadConfig();
        config.setBasePath(uploadRoot.toString());
        storage = spy(new LocalFileStorage(config));
        FileUtil fileUtil = new FileUtil(config, storage, new CosStorageProperties());
        service = new FileUploadService(fileUtil, config, userMapper, resourcesMapper,
                attachmentsMapper, imagesService, storage);
        when(userMapper.selectById(anyLong())).thenAnswer(invocation -> {
            Users user = new Users();
            user.setId(invocation.getArgument(0));
            return user;
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../images", "..", ".", "/tmp", "a/b", "a\\b", "a b", "a.b", "合法"})
    void invalidUploadIdCannotWriteOrMergeOutsideTask(String uploadId) throws IOException {
        Path image = uploadRoot.resolve("images/0.part");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[] {9});

        BusinessException uploadError = assertThrows(BusinessException.class,
                () -> service.uploadResourceChunk(part(new byte[] {1}), 1L, uploadId, 0, 1, "data.zip"));
        BusinessException mergeError = assertThrows(BusinessException.class,
                () -> merge(service, 1L, uploadId, 1));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), uploadError.getCode());
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), mergeError.getCode());
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(image));
        verifyNoInteractions(resourcesMapper, attachmentsMapper);
    }

    @Test
    void symbolicLinkTaskCannotWriteOrDeleteImages() throws IOException {
        Path imageDirectory = uploadRoot.resolve("images");
        Path image = imageDirectory.resolve("0.part");
        Files.createDirectories(imageDirectory);
        Files.write(image, new byte[] {9});
        Path task = uploadRoot.resolve("tmp/1/task");
        Files.createDirectories(task.getParent());
        Files.createSymbolicLink(task, imageDirectory);

        assertThrows(BusinessException.class,
                () -> service.uploadResourceChunk(part(new byte[] {1}), 1L, "task", 0, 1, "data.zip"));
        assertThrows(BusinessException.class, () -> merge(service, 1L, "task", 1));

        assertArrayEquals(new byte[] {9}, Files.readAllBytes(image));
        assertTrue(Files.isSymbolicLink(task));
    }

    @Test
    void symbolicLinkChunkCannotBeReadOrOverwritten() throws IOException {
        Path secret = uploadRoot.resolve("secret");
        Files.write(secret, new byte[] {9});
        Path task = uploadRoot.resolve("tmp/1/task");
        Files.createDirectories(task);
        Files.createSymbolicLink(task.resolve("0.part"), secret);

        assertThrows(BusinessException.class,
                () -> service.uploadResourceChunk(part(new byte[] {1}), 1L, "task", 0, 1, "data.zip"));
        assertThrows(BusinessException.class, () -> merge(service, 1L, "task", 1));
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(secret));
    }

    @Test
    void usersWithSameUploadIdKeepSeparateChunks() throws IOException {
        service.uploadResourceChunk(part(new byte[] {1}), 1L, "task", 0, 1, "data.zip");
        service.uploadResourceChunk(part(new byte[] {2}), 2L, "task", 0, 1, "data.zip");

        assertArrayEquals(new byte[] {1}, Files.readAllBytes(uploadRoot.resolve("tmp/1/task/0.part")));
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(uploadRoot.resolve("tmp/2/task/0.part")));
        assertThrows(BusinessException.class, () -> merge(service, 3L, "task", 1));
    }

    @Test
    void retryReplacesOneChunkWithoutLeavingPartialStagingFiles() throws IOException {
        service.uploadResourceChunk(part(new byte[] {1}), 1L, "task", 0, 1, "data.zip");
        service.uploadResourceChunk(part(new byte[] {2, 3}), 1L, "task", 0, 1, "data.zip");

        Path task = uploadRoot.resolve("tmp/1/task");
        assertArrayEquals(new byte[] {2, 3}, Files.readAllBytes(task.resolve("0.part")));
        try (var children = Files.list(task)) {
            assertEquals(List.of("0.part"), children.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    void failedChunkTransferPreservesPreviousCompletePart() throws IOException {
        service.uploadResourceChunk(part(new byte[] {1}), 1L, "task", 0, 1, "data.zip");
        MockMultipartFile failing = new MockMultipartFile("file", "data.zip", "application/zip", new byte[] {2, 3}) {
            @Override public void transferTo(File destination) throws IOException {
                Files.write(destination.toPath(), new byte[] {2});
                throw new IOException("connection failed");
            }
        };

        assertThrows(BusinessException.class,
                () -> service.uploadResourceChunk(failing, 1L, "task", 0, 1, "data.zip"));

        Path task = uploadRoot.resolve("tmp/1/task");
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(task.resolve("0.part")));
        try (var children = Files.list(task)) {
            assertEquals(List.of("0.part"), children.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    void uploadAndMergeRejectTooManyChunks() {
        assertThrows(BusinessException.class,
                () -> service.uploadResourceChunk(part(new byte[] {1}), 1L, "task", 0, 1001, "data.zip"));
        assertThrows(BusinessException.class, () -> merge(service, 1L, "task", 1001));
        assertFalse(Files.exists(uploadRoot.resolve("tmp")));
    }

    @Test
    void sameNameWithDifferentBytesCreatesNewResource() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("draft"), any(java.util.Date.class))).thenReturn(List.of(existing));
        assignResourceId();

        FileUploadResp result = upload(new byte[] {3, 4}, null);

        assertEquals(100L, result.getResourceId());
        assertNotEquals(Boolean.TRUE, result.getIsDuplicate());
        assertArrayEquals(new byte[] {3, 4}, Files.readAllBytes(uploadRoot.resolve(result.getFilePath())));
        assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(uploadRoot.resolve("resources/previous.zip")));
    }

    @Test
    void shorterAndLongerCandidatesDoNotMatchEqualPrefix() throws IOException {
        Resources shorter = candidate(8L, "short.zip", new byte[] {1});
        Resources longer = candidate(7L, "long.zip", new byte[] {1, 2, 3});
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("draft"), any(java.util.Date.class))).thenReturn(List.of(shorter, longer));
        assignResourceId();

        FileUploadResp result = upload(new byte[] {1, 2}, null);

        assertEquals(100L, result.getResourceId());
        verify(resourcesMapper).insert(any(Resources.class));
    }

    @Test
    void findsMatchingOlderCandidateAndCreatesDraftAssociationOnce() throws IOException {
        Resources newest = candidate(8L, "new.zip", new byte[] {3, 4});
        Resources older = candidate(7L, "old.zip", new byte[] {1, 2});
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("draft"), any(java.util.Date.class))).thenReturn(List.of(newest, older));
        PostAttachments association = new PostAttachments();
        association.setId(20L);
        when(attachmentsMapper.selectByDraftKeyAndResourceId("draft", 7L)).thenReturn(null, association);
        when(attachmentsMapper.insert(any(PostAttachments.class))).thenAnswer(invocation -> {
            ((PostAttachments) invocation.getArgument(0)).setId(20L);
            return 1;
        });

        FileUploadResp first = service.uploadResource(part(new byte[] {1, 2}), 1L, null, "draft", "attachment", 0, 0);
        FileUploadResp retry = service.uploadResource(part(new byte[] {1, 2}), 1L, null, "draft", "attachment", 0, 0);

        assertTrue(first.getIsDuplicate());
        assertEquals(7L, first.getResourceId());
        assertEquals(20L, first.getAttachmentId());
        assertEquals(20L, retry.getAttachmentId());
        verify(attachmentsMapper, times(1)).insert(any(PostAttachments.class));
        verify(resourcesMapper, never()).insert(any(Resources.class));
        verify(storage, never()).save(any(), anyString(), anyString());
    }

    @Test
    void sameContentWithDifferentPriceIsSeparateBusinessResource() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("draft"), any(java.util.Date.class))).thenReturn(List.of(existing));
        assignResourceId();

        FileUploadResp result = service.uploadResource(part(new byte[] {1, 2}), 1L, null, "draft", "attachment", 1, 5);

        assertEquals(100L, result.getResourceId());
        ArgumentCaptor<Resources> saved = ArgumentCaptor.forClass(Resources.class);
        verify(resourcesMapper).insert(saved.capture());
        assertEquals(1, saved.getValue().getDownloadType());
        assertEquals(new BigDecimal("5"), saved.getValue().getPointsNeeded());
        verify(storage, never()).open(anyString());
    }

    @Test
    void sameContentWithDifferentDescriptionDoesNotLoseNewMetadata() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("draft"), any(java.util.Date.class))).thenReturn(List.of(existing));
        assignResourceId();

        FileUploadResp result = upload(new byte[] {1, 2}, "新版说明");

        assertEquals(100L, result.getResourceId());
        ArgumentCaptor<Resources> saved = ArgumentCaptor.forClass(Resources.class);
        verify(resourcesMapper).insert(saved.capture());
        assertEquals("新版说明", saved.getValue().getDescription());
    }

    @Test
    void missingStoredFileDoesNotReturnBrokenDuplicate() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        Files.delete(uploadRoot.resolve("resources/previous.zip"));
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("draft"), any(java.util.Date.class))).thenReturn(List.of(existing));
        assignResourceId();

        FileUploadResp result = upload(new byte[] {1, 2}, null);

        assertEquals(100L, result.getResourceId());
        assertTrue(Files.isRegularFile(uploadRoot.resolve(result.getFilePath())));
    }

    @Test
    void unsafeHistoricalCandidateIsNotReadOrReused() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        existing.setFileUrl("/uploads/resources/../secret");
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("draft"), any(java.util.Date.class))).thenReturn(List.of(existing));
        assignResourceId();

        FileUploadResp result = upload(new byte[] {1, 2}, null);

        assertEquals(100L, result.getResourceId());
        verify(storage, never()).open(anyString());
    }

    @Test
    void differentDraftDoesNotReuseAnotherDraftsBusinessResource() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("original"), any(java.util.Date.class)))
                .thenReturn(List.of(existing));
        when(resourcesMapper.selectRecentUploadCandidates(eq(1L), eq("data.zip"), eq("new-draft"), any(java.util.Date.class)))
                .thenReturn(List.of());
        assignResourceId();

        FileUploadResp retry = service.uploadResource(part(new byte[] {1, 2}), 1L, null, "original", "attachment", 0, 0);
        FileUploadResp independent = service.uploadResource(part(new byte[] {1, 2}), 1L, null, "new-draft", "attachment", 0, 0);

        assertEquals(7L, retry.getResourceId());
        assertTrue(retry.getIsDuplicate());
        assertEquals(100L, independent.getResourceId());
        assertNotEquals(Boolean.TRUE, independent.getIsDuplicate());
        assertTrue(Files.exists(uploadRoot.resolve(independent.getFilePath())));
        assertTrue(Files.exists(uploadRoot.resolve("resources/previous.zip")));
    }

    @Test
    void uploadWithoutDraftContextCreatesIndependentResource() {
        assignResourceId();

        FileUploadResp result = service.uploadResource(part(new byte[] {1, 2}), 1L, null);

        assertEquals(100L, result.getResourceId());
        verify(resourcesMapper, never()).selectRecentUploadCandidates(anyLong(), anyString(), anyString(), any(java.util.Date.class));
        verifyNoInteractions(attachmentsMapper);
    }

    @Test
    void sharedResourceCannotBeDestroyedFromAttachmentDeleteEndpoint() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        when(resourcesMapper.selectById(7L)).thenReturn(existing);
        when(attachmentsMapper.countActiveReferences(7L)).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteAttachment(7L, 1L));

        assertEquals("该资源被多个草稿或文章引用，不能直接删除", error.getMessage());
        verify(resourcesMapper, never()).deleteById(anyLong());
        verify(attachmentsMapper, never()).deleteByResourceId(anyLong());
        verify(storage, never()).delete(anyString());
        assertTrue(Files.exists(uploadRoot.resolve("resources/previous.zip")));
    }

    @Test
    void failedAttachmentDeleteKeepsStoredFileForRolledBackResource() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        when(resourcesMapper.selectById(7L)).thenReturn(existing);
        when(attachmentsMapper.countActiveReferences(7L)).thenReturn(1L);
        when(resourcesMapper.deleteById(7L)).thenThrow(new IllegalStateException("db failed"));
        TestTransactionManager manager = new TestTransactionManager();
        FileUploadService proxied = withTransactions(manager);

        assertThrows(IllegalStateException.class, () -> proxied.deleteAttachment(7L, 1L));

        assertEquals(1, manager.rollbacks);
        assertTrue(Files.exists(uploadRoot.resolve("resources/previous.zip")));
        verify(storage, never()).delete(anyString());
    }

    @Test
    void attachmentDeleteRemovesStoredFileAfterDatabaseCommit() throws IOException {
        Resources existing = candidate(7L, "previous.zip", new byte[] {1, 2});
        when(resourcesMapper.selectById(7L)).thenReturn(existing);
        when(attachmentsMapper.countActiveReferences(7L)).thenReturn(1L);
        TestTransactionManager manager = new TestTransactionManager();
        manager.beforeCommit = () -> assertTrue(Files.exists(uploadRoot.resolve("resources/previous.zip")));
        FileUploadService proxied = withTransactions(manager);

        proxied.deleteAttachment(7L, 1L);

        assertEquals(1, manager.commits);
        assertFalse(Files.exists(uploadRoot.resolve("resources/previous.zip")));
        verify(attachmentsMapper).deleteByResourceId(7L);
        verify(resourcesMapper).deleteById(7L);
    }

    @Test
    void invalidMetadataIsRejectedBeforeStorageSave() throws IOException {
        assertThrows(BusinessException.class,
                () -> service.uploadResource(part(new byte[] {1}), 1L, null, null, null, 99, 0));
        verify(storage, never()).save(any(), anyString(), anyString());
        verifyNoInteractions(resourcesMapper, attachmentsMapper);
    }

    @Test
    void failedDatabaseWriteWithoutProxyAlsoRemovesNewFile() {
        when(resourcesMapper.insert(any(Resources.class))).thenThrow(new IllegalStateException("db failed"));

        assertThrows(IllegalStateException.class, () -> service.uploadResource(part(new byte[] {1}), 1L, null));

        assertEquals(0, storedResourceCount());
        verify(storage).delete(anyString());
    }

    @Test
    void mergeRunsDatabaseWritesInsideTransactionAndCleansChunksAfterCommit() throws IOException {
        service.uploadResourceChunk(part(new byte[] {1, 2}), 1L, "task", 0, 2, "data.zip");
        service.uploadResourceChunk(part(new byte[] {3, 4}), 1L, "task", 1, 2, "data.zip");
        TestTransactionManager manager = new TestTransactionManager();
        manager.beforeCommit = () -> assertTrue(Files.exists(uploadRoot.resolve("tmp/1/task/0.part")));
        FileUploadService proxied = withTransactions(manager);
        when(resourcesMapper.insert(any(Resources.class))).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            ((Resources) invocation.getArgument(0)).setId(100L);
            return 1;
        });

        FileUploadResp result = merge(proxied, 1L, "task", 2);

        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
        assertFalse(Files.exists(uploadRoot.resolve("tmp/1/task")));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(uploadRoot.resolve(result.getFilePath())));
    }

    @Test
    void failedAttachmentWriteRollsBackMergeAndPreservesChunksForRetry() {
        service.uploadResourceChunk(part(new byte[] {1}), 1L, "task", 0, 1, "data.zip");
        TestTransactionManager manager = new TestTransactionManager();
        FileUploadService proxied = withTransactions(manager);
        assignResourceId();
        when(attachmentsMapper.insert(any(PostAttachments.class))).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            throw new IllegalStateException("db failed");
        });

        assertThrows(IllegalStateException.class,
                () -> proxied.mergeResourceChunks(1L, "task", 1, "data.zip", null, "draft", "attachment", 0, 0));

        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
        assertTrue(Files.exists(uploadRoot.resolve("tmp/1/task/0.part")));
        assertEquals(0, storedResourceCount());
        verify(storage).delete(anyString());
    }

    @Test
    void failedStorageSaveKeepsChunksAndSupportsMergeRetry() throws IOException {
        service.uploadResourceChunk(part(new byte[] {1}), 1L, "task", 0, 1, "data.zip");
        doThrow(new IOException("disk failed")).doCallRealMethod().when(storage).save(any(), eq("resources"), eq("data.zip"));
        TestTransactionManager manager = new TestTransactionManager();
        FileUploadService proxied = withTransactions(manager);

        assertThrows(BusinessException.class, () -> merge(proxied, 1L, "task", 1));
        assertTrue(Files.exists(uploadRoot.resolve("tmp/1/task/0.part")));
        assignResourceId();
        FileUploadResp result = merge(proxied, 1L, "task", 1);

        assertEquals(1, manager.rollbacks);
        assertEquals(1, manager.commits);
        assertTrue(Files.exists(uploadRoot.resolve(result.getFilePath())));
        assertFalse(Files.exists(uploadRoot.resolve("tmp/1/task")));
    }

    @Test
    void ordinaryUploadConvenienceOverloadAlsoStartsTransaction() {
        TestTransactionManager manager = new TestTransactionManager();
        FileUploadService proxied = withTransactions(manager);
        when(resourcesMapper.insert(any(Resources.class))).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            throw new IllegalStateException("db failed");
        });

        assertThrows(IllegalStateException.class, () -> proxied.uploadResource(part(new byte[] {1}), 1L, null));
        assertEquals(1, manager.rollbacks);
        assertEquals(0, storedResourceCount());
    }

    private Resources candidate(long id, String storedName, byte[] data) throws IOException {
        Path path = uploadRoot.resolve("resources/" + storedName);
        Files.createDirectories(path.getParent());
        Files.write(path, data);
        Resources resource = new Resources();
        resource.setId(id);
        resource.setName("data.zip");
        resource.setUploaderId(1L);
        resource.setFileUrl("/uploads/resources/" + storedName);
        resource.setDownloadType(0);
        resource.setPointsNeeded(BigDecimal.ZERO);
        return resource;
    }

    private void assignResourceId() {
        when(resourcesMapper.insert(any(Resources.class))).thenAnswer(invocation -> {
            ((Resources) invocation.getArgument(0)).setId(100L);
            return 1;
        });
    }

    private FileUploadResp upload(byte[] data, String description) {
        return service.uploadResource(part(data), 1L, description, "draft", "attachment", 0, 0);
    }

    private FileUploadResp merge(FileUploadService target, long userId, String uploadId, int chunks) {
        return target.mergeResourceChunks(userId, uploadId, chunks, "data.zip", null, null, null, 0, 0);
    }

    private MockMultipartFile part(byte[] data) {
        return new MockMultipartFile("file", "data.zip", "application/zip", data);
    }

    private long storedResourceCount() {
        Path directory = uploadRoot.resolve("resources");
        if (!Files.exists(directory)) return 0;
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private FileUploadService withTransactions(TestTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (FileUploadService) factory.getProxy();
    }

    /** 不连接数据库，实际运行 Spring 事务拦截及同步回调。 */
    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        Runnable beforeCommit = () -> {};
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { beforeCommit.run(); commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
