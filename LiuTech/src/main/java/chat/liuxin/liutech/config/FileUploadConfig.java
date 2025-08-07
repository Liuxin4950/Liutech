package chat.liuxin.liutech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * 文件上传配置类
 * 配置文件上传相关参数
 * 
 * @author 刘鑫
 * @date 2025-08-07
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {
    
    /**
     * 文件上传根目录
     */
    private String basePath = System.getProperty("user.dir") + "/uploads";
    
    /**
     * 图片上传目录
     */
    private String imagePath = "images";
    
    /**
     * 文档上传目录
     */
    private String documentPath = "documents";
    
    /**
     * 资源文件上传目录
     */
    private String resourcePath = "resources";
    
    /**
     * 允许的图片文件类型
     */
    private String[] allowedImageTypes = {"jpg", "jpeg", "png", "gif", "bmp", "webp"};
    
    /**
     * 允许的文档文件类型
     */
    private String[] allowedDocumentTypes = {"txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"};
    
    /**
     * 允许的资源文件类型
     */
    private String[] allowedResourceTypes = {"zip", "rar", "7z", "tar", "gz", "exe", "msi", "jar", "war"};
    
    /**
     * 最大文件大小（字节）
     */
    private long maxFileSize = 100 * 1024 * 1024; // 100MB
    
    /**
     * 图片最大文件大小（字节）
     */
    private long maxImageSize = 10 * 1024 * 1024; // 10MB
    
    /**
     * 访问URL前缀
     */
    private String urlPrefix = "/uploads";
}