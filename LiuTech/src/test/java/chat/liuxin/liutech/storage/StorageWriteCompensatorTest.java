package chat.liuxin.liutech.storage;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import static org.mockito.Mockito.*;

class StorageWriteCompensatorTest {
    @Test void knownRollbackDeletesOnlyNewFile() {
        FileStorage storage = mock(FileStorage.class);
        new StorageWriteCompensator(storage).onFailure("new-file", new DataIntegrityViolationException("constraint"));
        verify(storage).delete("new-file");
    }

    @Test void unknownCommitOutcomeKeepsPossiblyReferencedFile() {
        FileStorage storage = mock(FileStorage.class);
        new StorageWriteCompensator(storage).onFailure("new-file", new TransactionSystemException("lost commit acknowledgement"));
        verifyNoInteractions(storage);
    }
}
