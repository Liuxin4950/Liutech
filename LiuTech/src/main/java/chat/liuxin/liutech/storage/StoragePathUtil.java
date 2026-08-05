package chat.liuxin.liutech.storage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 存储路径生成工具：本地磁盘与 COS 实现共用
 * 保证两处生成的逻辑路径完全一致（数据库零迁移的关键）
 *
 * @author 刘鑫
 */
public final class StoragePathUtil {

    private StoragePathUtil() {
    }

    /**
     * 生成逻辑路径：{@code <subPath>/yyyy/MM/dd/<timestamp>_<uuid>.<ext>}
     *
     * @param subPath          子路径（如 images、documents、music）
     * @param originalFilename 原始文件名（仅用于取扩展名）
     */
    public static String generateRelativePath(String subPath, String originalFilename) {
        String fileName = generateFileName(originalFilename);
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return subPath + "/" + datePath + "/" + fileName;
    }

    /**
     * 生成唯一文件名（保留原始扩展名）
     */
    public static String generateFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return timestamp + "_" + uuid + "." + extension;
    }

    private static String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
