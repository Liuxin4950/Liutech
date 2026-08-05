package chat.liuxin.liutech.storage;

import chat.liuxin.liutech.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地磁盘文件存储（{@link FileStorage} 默认实现）
 * cos.enabled=false（默认）时生效；启用 COS 后由 {@link CosFileStorage} 接管
 * 上传根目录配置见 {@link FileUploadConfig#getBasePath()}
 *
 * @author 刘鑫
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cos", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final FileUploadConfig fileUploadConfig;

    @Override
    public String save(byte[] data, String subPath, String originalFilename) throws IOException {
        // 路径生成与 COS 实现共用 StoragePathUtil，保证两处逻辑路径结构一致（数据库零迁移）
        String relativePath = StoragePathUtil.generateRelativePath(subPath, originalFilename);

        // 创建完整的文件路径（确保为绝对路径，避免Tomcat相对路径解析到临时目录）
        Path base = Paths.get(fileUploadConfig.getBasePath());
        Path fullPath = (base.isAbsolute() ? base : base.toAbsolutePath()).resolve(relativePath);
        Files.createDirectories(fullPath.getParent());
        Files.write(fullPath, data);
        return relativePath;
    }

    @Override
    public void delete(String relativePath) {
        try {
            Path filePath = Paths.get(fileUploadConfig.getBasePath(), relativePath);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除文件失败: {}", relativePath, e);
        }
    }

    @Override
    public boolean exists(String relativePath) {
        Path filePath = Paths.get(fileUploadConfig.getBasePath(), relativePath);
        return Files.exists(filePath);
    }

    @Override
    public String generateUrl(String relativePath) {
        // 返回相对路径（如 /uploads/images/2026/01/xxx.png），由前端反向代理统一转发：
        // - 开发环境：vite proxy /uploads -> http://localhost:8080
        // - 生产环境：nginx location /uploads/ -> backend
        // 数据库存储与环境无关，避免开发/生产域名不一致导致图片加载失败
        return fileUploadConfig.getUrlPrefix() + "/" + relativePath;
    }

    @Override
    public InputStream open(String relativePath) {
        Path filePath = Paths.get(fileUploadConfig.getBasePath(), relativePath);
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            log.warn("打开文件失败: {}", relativePath, e);
            return null;
        }
    }
}
