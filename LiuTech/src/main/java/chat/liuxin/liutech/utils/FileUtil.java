package chat.liuxin.liutech.utils;

import chat.liuxin.liutech.config.FileUploadConfig;
import chat.liuxin.liutech.mapper.ImagesMapper;
import chat.liuxin.liutech.model.Images;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件工具类
 * 提供文件上传、保存、验证等功能
 * 
 * @author 刘鑫
 * @date 2025-08-07
 */
@Slf4j
@Component
public class FileUtil {

    @Autowired
    private FileUploadConfig fileUploadConfig;

    @Autowired
    private ImagesMapper imagesMapper;

    /** 图片URL提取正则：匹配 <img src="URL">，支持属性换行和多种格式 */
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img\\s+[^>]*?src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    /** 图片URL提取正则（无引号版本） */
    private static final Pattern IMG_SRC_PATTERN_NO_QUOTE = Pattern.compile(
            "<img\\s+[^>]*?src\\s*=\\s*([^\\s\"'>]+)[^>]*>", Pattern.CASE_INSENSITIVE);

    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)");
    
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
        
        // 创建完整的文件路径（确保为绝对路径，避免Tomcat相对路径解析到临时目录）
        Path base = Paths.get(fileUploadConfig.getBasePath());
        Path fullPath = (base.isAbsolute() ? base : base.toAbsolutePath()).resolve(relativePath);
        
        // 确保目录存在
        Files.createDirectories(fullPath.getParent());
        
        // 保存文件
        java.io.File targetFile = fullPath.toFile();
        if (targetFile != null) {
            file.transferTo(targetFile);
        }
        
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
        return fileUploadConfig.getServerBaseUrl() + fileUploadConfig.getUrlPrefix() + "/" + relativePath;
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

    /**
     * 从完整URL提取相对路径
     * @param fileUrl 完整文件URL（如 http://localhost:8080/uploads/music/2025/01/05/xxx.mp3）
     * @return 相对路径（如 music/2025/01/05/xxx.mp3），提取失败返回null
     */
    public String extractRelativePath(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        String prefix = fileUploadConfig.getServerBaseUrl() + fileUploadConfig.getUrlPrefix() + "/";
        if (fileUrl.startsWith(prefix)) {
            return fileUrl.substring(prefix.length());
        }

        // 兼容旧数据或其他来源的URL
        if (fileUrl.startsWith("/uploads/")) {
            return fileUrl.substring("/uploads/".length());
        }

        return null;
    }

    /**
     * 从完整URL删除文件（便捷方法）
     * @param fileUrl 完整文件URL
     * @return 是否删除成功（URL无法解析返回false）
     */
    public boolean deleteFileByUrl(String fileUrl) {
        String relativePath = extractRelativePath(fileUrl);
        if (relativePath == null) {
            log.warn("无法解析文件路径: {}", fileUrl);
            return false; // URL无法解析返回false
        }
        return deleteFile(relativePath); // 返回实际删除结果
    }

    /**
     * 计算文件的SHA-256哈希值
     *
     * @param file 上传的文件
     * @return 哈希值的十六进制字符串（64位）
     * @throws IOException IO异常
     */
    public String calculateFileHash(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return calculateFileHash(is);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256算法不可用", e);
            throw new RuntimeException("哈希计算失败", e);
        }
    }

    /**
     * 计算输入流的SHA-256哈希值
     *
     * @param inputStream 输入流
     * @return 哈希值的十六进制字符串
     * @throws IOException IO异常
     * @throws NoSuchAlgorithmException SHA-256算法不可用
     */
    public String calculateFileHash(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }

        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 从HTML内容中提取所有图片URL（仅提取系统内的图片）
     *
     * @param content HTML内容
     * @return 图片URL列表（去重）
     */
    public List<String> extractImageUrls(String content) {
        List<String> urls = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return urls;
        }

        // 匹配带引号的 src
        Matcher matcher = IMG_SRC_PATTERN.matcher(content);
        while (matcher.find()) {
            String src = matcher.group(1);
            // 清理 URL：去除首尾引号和空格
            if (src != null) {
                src = src.replaceAll("^[\"']|[\"']$", "").trim();
            }
            if (src != null && (src.startsWith("/uploads/") || src.contains("/uploads/"))) {
                urls.add(src);
            }
        }

        Matcher noQuoteMatcher = IMG_SRC_PATTERN_NO_QUOTE.matcher(content);
        while (noQuoteMatcher.find()) {
            String src = noQuoteMatcher.group(1);
            if (src != null) {
                src = src.trim();
            }
            if (src != null && (src.startsWith("/uploads/") || src.contains("/uploads/"))) {
                urls.add(src);
            }
        }

        Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(content);
        while (markdownMatcher.find()) {
            String src = markdownMatcher.group(1);
            if (src != null) {
                src = src.trim();
            }
            if (src != null && (src.startsWith("/uploads/") || src.contains("/uploads/"))) {
                urls.add(src);
            }
        }

        return urls;
    }

    public int incrementImageUsageCountByUrl(String url, int delta) {
        String relativePath = normalizeToRelativePath(url);
        if (relativePath == null) {
            return 0;
        }

        LambdaQueryWrapper<Images> query = new LambdaQueryWrapper<>();
        query.eq(Images::getFilePath, relativePath)
                .isNull(Images::getDeletedAt)
                .eq(Images::getStatus, 1);
        Images image = imagesMapper.selectOne(query);
        if (image == null) {
            return 0;
        }
        return imagesMapper.incrementUsageCount(image.getId(), delta);
    }

    public int decrementImageUsageCountByUrl(String url) {
        return incrementImageUsageCountByUrl(url, -1);
    }

    private String normalizeToRelativePath(String fileUrlOrRelativePath) {
        if (fileUrlOrRelativePath == null || fileUrlOrRelativePath.isEmpty()) {
            return null;
        }

        String relativePath = extractRelativePath(fileUrlOrRelativePath);
        if (relativePath != null && !relativePath.isEmpty()) {
            return relativePath;
        }

        if (fileUrlOrRelativePath.contains("://")) {
            return null;
        }

        String value = fileUrlOrRelativePath.trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.startsWith("uploads/")) {
            value = value.substring("uploads/".length());
        }
        if (value.isEmpty()) {
            return null;
        }
        return value;
    }

    /**
     * 根据URL获取图片记录
     *
     * @param url 图片URL
     * @return 图片记录，不存在或已删除返回null
     */
    public Images getImageByUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        String relativePath = extractRelativePath(url);
        if (relativePath == null) {
            return null;
        }

        LambdaQueryWrapper<Images> query = new LambdaQueryWrapper<>();
        query.eq(Images::getFilePath, relativePath)
             .isNull(Images::getDeletedAt)
             .eq(Images::getStatus, 1);
        return imagesMapper.selectOne(query);
    }
}
