package chat.liuxin.liutech.service;

import chat.liuxin.liutech.storage.StorageFileCleanup;
import chat.liuxin.liutech.utils.FileUtil;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static org.mockito.Mockito.*;

class StorageFileCleanupTest {
    @Test void rollbackKeepsFilesAndCommitDeletesEachUrlOnce() {
        FileUtil files = mock(FileUtil.class);
        var cleanup = new StorageFileCleanup(files);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cleanup.afterCommit(List.of("/uploads/test-owned.zip", "/uploads/test-owned.zip"));
            verifyNoInteractions(files);
            // A rollback never invokes afterCommit.
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(files);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            when(files.deleteFileByUrl("/uploads/test-owned.zip")).thenReturn(true);
            cleanup.afterCommit(List.of("/uploads/test-owned.zip", "/uploads/test-owned.zip"));
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(files, times(1)).deleteFileByUrl("/uploads/test-owned.zip");
        } finally { TransactionSynchronizationManager.clear(); }
    }
}
