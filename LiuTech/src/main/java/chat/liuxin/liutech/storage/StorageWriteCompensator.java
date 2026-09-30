package chat.liuxin.liutech.storage;

import chat.liuxin.liutech.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.UnexpectedRollbackException;

/** 确定未提交才清理新文件；提交结果不明时保留可能已有引用的文件。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageWriteCompensator {
    private final FileStorage storage;

    public void onFailure(String newPath, RuntimeException failure) {
        if (failure instanceof BusinessException || failure instanceof DataIntegrityViolationException
                || failure instanceof UnexpectedRollbackException) {
            try {
                storage.delete(newPath);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                log.warn("新文件补偿清理未完成: path={}", newPath, cleanupFailure);
            }
        } else {
            log.warn("持久化结果待核验，保留本次文件: path={}, error={}", newPath, failure.getClass().getSimpleName());
        }
    }
}
