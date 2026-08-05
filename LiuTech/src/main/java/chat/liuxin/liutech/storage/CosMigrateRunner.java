package chat.liuxin.liutech.storage;

import chat.liuxin.liutech.config.CosStorageProperties;
import chat.liuxin.liutech.config.FileUploadConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * 一次性迁移工具：把本地 uploads 目录已有文件全量上传到 COS
 * <p>
 * 对象键 = 相对 basePath 的路径（如 images/2026/08/05/xxx.jpg），与数据库存储的逻辑路径一致，
 * 迁移后无需改数据库。已存在的对象自动跳过（可重入，失败重跑不会重复上传）。
 * <p>
 * 使用：.env 设 COS_MIGRATE=true 后重启 backend，日志出现「COS 迁移完成」即结束；
 * 确认 COS 文件齐全后再设 COS_ENABLED=true 正式切换，最后移除 COS_MIGRATE。
 * 迁移不动本地文件（保留兜底，可手动清理）。
 *
 * @author 刘鑫
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cos", name = "migrate", havingValue = "true")
public class CosMigrateRunner implements ApplicationRunner {

    private final FileUploadConfig fileUploadConfig;

    private final CosStorageProperties cosProperties;

    private COSClient cosClient;

    @PostConstruct
    void init() {
        COSCredentials cred = new BasicCOSCredentials(cosProperties.getSecretId(), cosProperties.getSecretKey());
        cosClient = new COSClient(cred, new ClientConfig(new Region(cosProperties.getRegion())));
    }

    @PreDestroy
    void shutdown() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        Path base = Paths.get(fileUploadConfig.getBasePath());
        if (!Files.isDirectory(base)) {
            log.warn("COS 迁移：上传目录不存在，跳过: {}", base);
            return;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(base)) {
            files = walk.filter(Files::isRegularFile).toList();
        } catch (Exception e) {
            log.error("COS 迁移：扫描目录失败: {}", base, e);
            return;
        }

        long uploaded = 0, skipped = 0, failed = 0;
        for (Path file : files) {
            // 相对路径即对象键（Windows 反斜杠转正斜杠，与逻辑路径格式一致）
            String key = base.relativize(file).toString().replace('\\', '/');
            if (cosClient.doesObjectExist(cosProperties.getBucket(), key)) {
                skipped++;
                continue;
            }
            try {
                cosClient.putObject(cosProperties.getBucket(), key, file.toFile());
                uploaded++;
            } catch (CosClientException e) {
                failed++;
                log.error("COS 迁移失败: {}", key, e);
            }
        }
        log.info("COS 迁移完成: 共 {} 个文件，上传 {}，跳过（已存在）{}，失败 {}",
                files.size(), uploaded, skipped, failed);
    }
}
