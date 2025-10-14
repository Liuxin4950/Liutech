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
    private String basePath = getDefaultBasePath();
    
    /**
     * 获取默认的文件上传根目录（为了兼容docker，不知道文件放哪里的问题）
     * 优先使用环境变量，其次使用系统属性，最后使用默认路径
     */
    private String getDefaultBasePath() {
        // 1. 优先使用环境变量（Docker环境）
        String envPath = System.getenv("FILE_UPLOAD_BASE_PATH");
        if (envPath != null && !envPath.trim().isEmpty()) {
            return envPath;
        }
        
        // 2. 使用系统属性
        String propPath = System.getProperty("file.upload.base-path");
        if (propPath != null && !propPath.trim().isEmpty()) {
            return propPath;
        }
        
        // 3. 默认路径（开发环境）
        return System.getProperty("user.dir") + "/uploads";
    }
    
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
    // 增加 mp3 支持，便于上传音频资源
    private String[] allowedResourceTypes = {"zip", "rar", "7z", "tar", "gz", "exe", "msi", "jar", "war", "mp3"};
    
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
    
    /**
     * 服务器基础URL（用于生成完整的文件访问URL）
     * 优先使用环境变量，其次使用配置文件，最后使用默认值
     */
    private String serverBaseUrl = getDefaultServerBaseUrl();
    
    /**
     * 获取默认的服务器基础URL
     * 优先使用环境变量（Docker环境），其次使用系统属性，最后使用默认值
     */
    private String getDefaultServerBaseUrl() {
        // 1. 优先使用环境变量（Docker环境）
        String envUrl = System.getenv("SERVER_BASE_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            return envUrl;
        }
        
        // 2. 使用系统属性
        String propUrl = System.getProperty("server.base-url");
        if (propUrl != null && !propUrl.trim().isEmpty()) {
            return propUrl;
        }
        
        // 3. 默认值（开发环境）
        return "http://localhost:8080";
    }
}