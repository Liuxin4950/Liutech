package chat.liuxin.liutech.storage;

import chat.liuxin.liutech.utils.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Collection;
import java.util.Objects;

/** 永久删除的文件清理在数据库提交后执行；回滚时保留文件。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageFileCleanup {
    private final FileUtil fileUtil;

    public void afterCommit(Collection<String> urls) {
        var files = urls.stream().filter(Objects::nonNull).filter(url -> !url.isBlank()).distinct().toList();
        if (files.isEmpty()) return;
        Runnable cleanup = () -> files.forEach(url -> {
            try {
                if (!fileUtil.deleteFileByUrl(url)) log.warn("永久删除后的文件清理未完成，请核验: {}", url);
            } catch (Exception failure) {
                log.error("永久删除后的文件清理失败，请核验: {}", url, failure);
            }
        });
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
    }
}
