package chat.liuxin.liutech.storage;

import chat.liuxin.liutech.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘文件存储（{@link FileStorage} 默认实现）
 * 逻辑迁自原 FileUtil 的磁盘 IO 部分；上传根目录配置见 {@link FileUploadConfig#getBasePath()}
 *
 * @author 刘鑫
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

    private final FileUploadConfig fileUploadConfig;

    @Override
    public String save(byte[] data, String subPath, String originalFilename) throws IOException {
        String fileName = generateFileName(originalFilename);
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String relativePath = subPath + "/" + datePath + "/" + fileName;

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

    /**
     * 生成唯一文件名（保留原始扩展名）
     */
    private String generateFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return timestamp + "_" + uuid + "." + extension;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
