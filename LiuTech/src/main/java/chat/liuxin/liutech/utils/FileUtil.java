package chat.liuxin.liutech.utils;

import chat.liuxin.liutech.config.FileUploadConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

/**
 * 文件工具类
 * 提供文件上传、保存、验证等功能
 * 
 * @author 刘鑫
 * @date 2025-08-07
 */
@Component
public class FileUtil {
    
    @Autowired
    private FileUploadConfig fileUploadConfig;
    
    /**
     * 保存上传的文件
     * 
     * @param file 上传的文件
     * @param subPath 子路径（如：images、documents、resources）
     * @return 文件相对路径
     * @throws IOException IO异常
     */
    public String saveFile(MultipartFile file, String subPath) throws IOException {
        // 生成文件名
        String fileName = generateFileName(file.getOriginalFilename());
        
        // 构建完整路径
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String relativePath = subPath + "/" + datePath + "/" + fileName;
        
        // 创建完整的文件路径
        Path fullPath = Paths.get(fileUploadConfig.getBasePath(), relativePath);
        
        // 确保目录存在
        Files.createDirectories(fullPath.getParent());
        
        // 保存文件
        file.transferTo(fullPath.toFile());
        
        return relativePath;
    }
    
    /**
     * 生成唯一文件名
     * 
     * @param originalFilename 原始文件名
     * @return 新文件名
     */
    private String generateFileName(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return timestamp + "_" + uuid + "." + extension;
    }
    
    /**
     * 获取文件扩展名
     * 
     * @param filename 文件名
     * @return 扩展名（小写）
     */
    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 验证文件类型是否允许
     * 
     * @param filename 文件名
     * @param allowedTypes 允许的文件类型数组
     * @return 是否允许
     */
    public boolean isAllowedFileType(String filename, String[] allowedTypes) {
        String extension = getFileExtension(filename);
        return Arrays.asList(allowedTypes).contains(extension);
    }
    
    /**
     * 验证图片文件类型
     * 
     * @param filename 文件名
     * @return 是否为允许的图片类型
     */
    public boolean isAllowedImageType(String filename) {
        return isAllowedFileType(filename, fileUploadConfig.getAllowedImageTypes());
    }
    
    /**
     * 验证文档文件类型
     * 
     * @param filename 文件名
     * @return 是否为允许的文档类型
     */
    public boolean isAllowedDocumentType(String filename) {
        return isAllowedFileType(filename, fileUploadConfig.getAllowedDocumentTypes());
    }
    
    /**
     * 验证资源文件类型
     * 
     * @param filename 文件名
     * @return 是否为允许的资源类型
     */
    public boolean isAllowedResourceType(String filename) {
        return isAllowedFileType(filename, fileUploadConfig.getAllowedResourceTypes());
    }
    
    /**
     * 验证文件大小
     * 
     * @param fileSize 文件大小
     * @param maxSize 最大允许大小
     * @return 是否在允许范围内
     */
    public boolean isValidFileSize(long fileSize, long maxSize) {
        return fileSize <= maxSize;
    }
    
    /**
     * 生成文件访问URL
     * 
     * @param relativePath 文件相对路径
     * @return 完整的访问URL
     */
    public String generateFileUrl(String relativePath) {
        // 返回完整的URL，TinyMCE需要完整URL才能正确获取图片尺寸
        return "http://localhost:8080" + fileUploadConfig.getUrlPrefix() + "/" + relativePath;
    }
    
    /**
     * 删除文件
     * 
     * @param relativePath 文件相对路径
     * @return 是否删除成功
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path filePath = Paths.get(fileUploadConfig.getBasePath(), relativePath);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 检查文件是否存在
     * 
     * @param relativePath 文件相对路径
     * @return 是否存在
     */
    public boolean fileExists(String relativePath) {
        Path filePath = Paths.get(fileUploadConfig.getBasePath(), relativePath);
        return Files.exists(filePath);
    }
}